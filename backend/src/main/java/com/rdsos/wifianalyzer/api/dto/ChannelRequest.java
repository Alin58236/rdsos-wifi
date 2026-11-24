package com.rdsos.wifianalyzer.api.dto;

import lombok.Value;

/** Body of {@code PUT /api/channel}: {@code {"channel":10}}. */
@Value
public class ChannelRequest {
    int channel;
}
