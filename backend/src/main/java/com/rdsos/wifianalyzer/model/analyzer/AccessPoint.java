package com.rdsos.wifianalyzer.model.analyzer;

import lombok.Value;

@Value
public class AccessPoint {

    String bssid;
    String ssid;
    int channel;
    Integer frequencyMHz;
    Integer signalDBm;
    String security;
    String vendor;
    long lastSeenMillis;

}
