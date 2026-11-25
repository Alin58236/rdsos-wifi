package com.rdsos.wifianalyzer.api.dto;

import lombok.Value;

import java.util.List;

@Value
public class PacketDetail {
    long id;
    long timestampMicros;
    int length;
    List<PacketLayer> layers;
    String hex;
}
