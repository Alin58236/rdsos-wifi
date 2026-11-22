package com.rdsos.wifianalyzer.model;

import lombok.Value;

@Value
public class CommandResult {
    int exitCode;
    String output;
}
