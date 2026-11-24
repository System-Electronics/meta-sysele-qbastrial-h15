SUMMARY = "Persistent configuration of the QBAstrial H15 camera"
DESCRIPTION = "Keeps the root password, the IP address, the SSH host keys and the customer applications on the data partition, and applies them at every boot, whatever slot booted."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://qba-config \
    file://qba-config.init \
    file://qba-console \
    file://qba-reset-config \
"
S = "${WORKDIR}"

inherit update-rc.d allarch

# Before S01networking, so that the stored address is in place when the network comes up.
INITSCRIPT_NAME = "qba-config"
INITSCRIPT_PARAMS = "start 00 2 3 4 5 ."

do_install() {
    install -d ${D}${sbindir} ${D}${sysconfdir}/init.d
    install -m 0755 ${WORKDIR}/qba-config ${D}${sbindir}/qba-config
    install -m 0755 ${WORKDIR}/qba-console ${D}${sbindir}/qba-console
    install -m 0755 ${WORKDIR}/qba-reset-config ${D}${sbindir}/qba-reset-config
    install -m 0755 ${WORKDIR}/qba-config.init ${D}${sysconfdir}/init.d/qba-config
}
