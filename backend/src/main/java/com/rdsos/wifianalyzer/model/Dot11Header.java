package com.rdsos.wifianalyzer.model;

import lombok.Value;

import java.util.List;

@Value
public class Dot11Header {

    // 0 = management, 1 = control, 2 = data
    int type;
    int subtype;
    boolean toDs;
    boolean fromDs;
    boolean protectedFrame;
    List<String> addresses;
    String bssid;
    String source;
    String destination;
    int headerLength;

}
