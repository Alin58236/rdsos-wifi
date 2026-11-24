package com.rdsos.wifianalyzer.model;

import lombok.Value;

import java.util.List;

@Value
public class Beacon {

    int beaconInterval;
    boolean privacy;
    String ssid;
    Integer channel;
    List<IE> ies;

}
