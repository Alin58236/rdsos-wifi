package com.rdsos.wifianalyzer.service.iface;

/** Interface mode, derived from the number in /sys/class/net/&lt;if&gt;/type. */
public enum InterfaceMode {
    MANAGED,
    MONITOR,
    OTHER;

    /** Frames look like Ethernet (802.11 header removed by the kernel). Kernel name: ARPHRD_ETHER. */
    private static final int ETHERNET_FRAMES = 1;

    /** Raw 802.11 frames with a Radiotap header in front. Kernel name: ARPHRD_IEEE80211_RADIOTAP. */
    private static final int RADIOTAP_FRAMES = 803;

    static InterfaceMode fromSysfsType(int type) {
        return switch (type) {
            case ETHERNET_FRAMES -> MANAGED;
            case RADIOTAP_FRAMES -> MONITOR;
            default -> OTHER;
        };
    }
}
