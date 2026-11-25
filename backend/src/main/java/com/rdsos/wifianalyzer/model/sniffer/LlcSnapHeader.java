package com.rdsos.wifianalyzer.model.sniffer;

import lombok.Builder;
import lombok.Value;

/** LLC/SNAP header: 8 bytes after an unencrypted 802.11 data header, ending in the EtherType. */
@Value
@Builder
public class LlcSnapHeader {

    int destinationServiceAccessPoint;
    int sourceServiceAccessPoint;
    int control;
    String organizationCode;
    // The protocol that follows: 0x0800 = IPv4
    int etherType;

}
