# meta-sysele-qbastrial-h15

Layer for the QBAstrial H15 smart camera, on top of `meta-sysele-astrial-h15` (collection `meta-sysele-bsp`).
It holds only the difference from the base layer. Collection `sysele-qba-bsp`, priority 11.

## Differences from the base layer

| Area | Change |
|---|---|
| Console | U-Boot (SPL included), kernel and getty on UART0 (`ttyS0`) |
| Display | none: DSI, DPI and panel nodes disabled, Waveshare trees and DSI tools removed |
| Identity | hostname `qbastrialh15`, banner `QBAstrial H15` |
| Update | signed SWU, A/B with fallback to the other slot |
| Configuration | root password, IP address, SSH host keys and customer applications on the data partition, applied at every boot |
| Boot menu | reduced to eMMC boot, SWUpdate, board init and recovery |

The SPI-NOR is programmed once through the mask ROM on UART1; from the first boot on, U-Boot and Linux use UART0.

## Update

`/etc/run_swupdate.sh` fetches the SWU over TFTP and installs it without a console.

- First installation: `fw_setenv swupdate_update_modes init-partitions-dual,init-scu-bl,copy-a,copy-b`, then
  `run_swupdate.sh -b -s <server> -r <file>.swu`.
- Update: `run_swupdate.sh -b -d -s <server> -r <file>.swu` writes the other slot and boots it next.

The SWU is Hailo's, with the sha256 of every entry added. swupdate verifies the RSA-PSS signature of `sw-description`
and every hash with `/etc/swupdate-public.pem`, in the system and in `swupdate-image`. An unsigned, altered or
wrongly signed SWU is refused.

The SWU is signed at build time with `QBA_SWU_PRIVATE_KEY`, the path of an RSA private key. The public key is read
from `QBA_SECURE_ASSETS_DIR/swupdate-public.pem`. Without a key the SWU is unsigned and the images refuse it.

## Configuration of a unit

The configuration lives on the data partition (`/dev/mmcblk1p5`, mounted on `/data`), which no update rewrites, and is
applied by `qba-config` at every boot, whatever slot booted.

| What | Where | How to change it |
|---|---|---|
| root password | `/data/config/root-hash` | `qba-config set-password` |
| IP address | `/data/config/network` | `qba-config set-ip ADDRESS/PREFIX [GATEWAY] [--now]` |
| SSH host keys | `/data/config/ssh`, one set per unit, made on the first boot | not meant to change |
| applications | `/data/app`, bind mounted on `/home/root/apps` | install under `/home/root/apps` |

`/home/root/apps` is filled once from the image the first time `/data/app` is used. Later images do not update it.

The unit has one of four states, shown by `qba-config status`:

- `configured`: a password is stored and applied.
- `new`: no password yet. Root is locked, and the serial console asks for the password at the first boot. The network
  cannot set it.
- `locked`: the unit was configured but the stored password is gone, or the data partition cannot be read. Root stays
  locked: there is no default password to fall back to.
- `bringup`: no data partition yet (image just written). The image defaults are left alone.

A marker tells `new` from `locked`. It is one 4 KiB block at the end of `/dev/mmcblk1boot1`, holding a magic string and
the hash of the eMMC CID. It is written with the first password and cleared by `qba-reset-config`, the only way back to
`new`, which also removes the stored password and keeps the address, the host keys and the applications. The updates do
not write to the eMMC boot partitions.

The password protects the network side. Whoever reaches the serial console and the boot menu can still act on the
unit: the physical access is for the owner of the camera to control.

### Boot menu

The U-Boot environment that an update writes offers to boot from SD or NFS, to write an unsigned image from TFTP and
to switch the device tree. `qba-config` sets the menu back, at every boot where it differs, to: Autodetect (eMMC only),
Boot from eMMC, SWUpdate, SWUpdate AB board init, and Recovery. Recovery fetches `hailo-update-image-<board>.swu` over
TFTP and runs its `reset-config` mode.

## Fallback to the other slot

The SCU bootloader boots the next descriptor after `boot_max_retries` boots that end in a watchdog reset.

- The configuration lists the target slot, the other slot and the target slot again, two boots each. `set_sw_image.sh`
  writes it, so after an update the new slot is first and the old one is the fallback.
- `watchdog.open_timeout` (60 s) is on the kernel command line of the eMMC boot. The `watchdog` daemon starts at S98 and
  feeds `/dev/watchdog`.
- `hailo_linux_init.sh` runs at S99 and sets `boot_success_ap_software` once the daemon is up.
- `qba-slot-sync` (S98) mounts the `/boot` of the booted slot. When the SPI-NOR configuration differs from the one of the
  booted slot, as after a fallback, it rewrites it and writes 99 to `boot_success_ap_software`, which resets the descriptor
  index the SCU keeps across warm resets.

The SCU bootloader configuration is not authenticated by the secure boot chain.

## Keys

Neither key is in this repository. `QBA_SECURE_ASSETS_DIR` holds `customer_keypair.pem` (secure boot) and
`swupdate-public.pem`.

## Build

```
bitbake u-boot u-boot-tfa-image
bitbake swupdate-image
bitbake core-image-hailo-dev
bitbake hailo-update-image
```

## CI

`.github/workflows/build.yml` is the reusable build engine (same pattern as `meta-sysele-astrial-h15` and
`meta-sysele-astrial-imx8`): it pins `meta-sysele-astrial-h15` at `BASE_LAYER_REF`, verifies the keys, and builds with
kas. Secrets: `HAILO_CUSTOMER_KEYPAIR` (secure boot) and `SWU_SIGNING_KEY` (SWU signing). The run stops before the
build if either is missing or invalid. Three thin callers decide what to build and publish:

- `main.yml`: on push to `main` and once a month on schedule. Overwrites the single `continuous` prerelease, never
  accumulates tags. The scheduled run is the canary: it also runs with `sbom: standard`, so it catches a CVE
  regression unattended; a push keeps `sbom: none`.
- `dev-build.yml`: manual dispatch, with GitHub Actions Cache enabled (dev build). Publishes only the run's own
  artifacts.
- `release.yml`: on a pushed tag `vX.Y.Z`. Reads the changelog from the tag's own annotated message and publishes
  the release.

Tag scheme `vMAJOR.MINOR.PATCH`, release candidates `-rcN`:

- major = Yocto line, same as `meta-sysele-astrial-h15` (kirkstone 1, scarthgap 2);
- minor = relationship with the pinned `BASE_LAYER_REF`;
- patch = everything else, even when it changes behavior.
