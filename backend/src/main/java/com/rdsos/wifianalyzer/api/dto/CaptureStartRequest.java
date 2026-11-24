package com.rdsos.wifianalyzer.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class CaptureStartRequest {
    String source;
    @JsonProperty("interface")
    String interfaceName;
    String file;
}
