package com.rdsos.wifianalyzer.api.dto;

import lombok.Value;

import java.util.List;

@Value
public class PacketLayer {
    String name;
    int offset;
    int length;
    List<PacketField> fields;
}
