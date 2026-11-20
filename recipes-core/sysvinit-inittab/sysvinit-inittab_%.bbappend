# The serial console goes through qba-console: a unit that was never configured asks for its root password there.
RDEPENDS:${PN} += "qba-config"

do_install:append() {
    sed -i 's|^\(.*:12345:respawn:\)${base_bindir}/start_getty \(.* ttyS0 .*\)$|\1${sbindir}/qba-console \2|' ${D}${sysconfdir}/inittab
    grep -q 'qba-console' ${D}${sysconfdir}/inittab || bbfatal "inittab: the serial console line was not found"
}
