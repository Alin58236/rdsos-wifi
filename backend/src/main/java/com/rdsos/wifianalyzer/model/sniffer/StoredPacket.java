package com.rdsos.wifianalyzer.model.sniffer;

import lombok.Value;

@Value
public class StoredPacket {

    long id;
    DecodedPacket packet;

}
