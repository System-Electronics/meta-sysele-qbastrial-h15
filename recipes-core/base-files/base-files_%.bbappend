hostname:pn-base-files = "qbastrialh15"

do_install:append() {
    echo "qbastrialh15" > ${D}${sysconfdir}/hostname

    # The data partition: the configuration and the customer applications live there, outside both root file systems.
    install -d ${D}/data
    echo '/dev/mmcblk1p5       /data                ext4       noatime,nofail        0  0' >> ${D}${sysconfdir}/fstab
}
