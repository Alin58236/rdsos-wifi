package com.rdsos.wifianalyzer.service.iface;

import lombok.Value;

@Value
public class WifiInterface {
    String name;
    InterfaceMode mode;
}
