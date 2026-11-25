package com.rdsos.wifianalyzer.model.sniffer;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EthernetHeader {

    String destinationMac;
    String sourceMac;
    //The protocol that follows: 0x0800 = IPv4, 0x86DD = IPv6
    int etherType;
    // 14, or 18 with an 802.1Q VLAN tag
    int headerLengthBytes;

}
