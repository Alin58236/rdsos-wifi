package com.rdsos.wifianalyzer.model;

import lombok.Value;

@Value
public class AccessPoint {

    String BSSID;
    String SSID;
    int channel;
    Integer frequencyMHz;
    Integer signalDBm;
    String security;
    String vendor;
    long lastSeenMillis;

}
