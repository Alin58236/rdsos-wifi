package com.rdsos.wifianalyzer.model;

import lombok.Value;

@Value
public class RawFrame {
    long timestampMicros;
    int linkType;
    long originalLength;
    byte[] data;
}
