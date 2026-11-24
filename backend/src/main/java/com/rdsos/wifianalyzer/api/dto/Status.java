package com.rdsos.wifianalyzer.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/** The status part of every WebSocket message. */
@Value
public class Status {
    boolean running;
    String source;
    @JsonProperty("interface")
    String interfaceName;
    String mode;
    Integer linkType;
    Integer currentChannel;
    Integer pinnedChannel;
    long frames;
    long dropped;
    long parseErrors;
}
