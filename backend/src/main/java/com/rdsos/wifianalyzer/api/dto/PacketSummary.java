package com.rdsos.wifianalyzer.api.dto;

import lombok.Value;

@Value
public class PacketSummary {
    long id;
    long timestampMicros;
    int length;
    String protocol;
    String source;
    String destination;
    String info;
}
