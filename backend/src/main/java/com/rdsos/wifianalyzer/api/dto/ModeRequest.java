package com.rdsos.wifianalyzer.api.dto;

import lombok.Value;

/** Body of {@code POST /api/interfaces/{name}/mode}: {@code {"mode":"MONITOR"}}. */
@Value
public class ModeRequest {
    String mode;
}
