package com.rdsos.wifianalyzer.api.dto;

import com.rdsos.wifianalyzer.model.AccessPoint;
import lombok.Value;

import java.util.List;

/** One WebSocket message, sent every 200 ms. */
@Value
public class UpdateMessage {
    Status status;
    List<AccessPoint> aps;
    /** Packets decoded since the previous message; empty until the capture side exists. */
    List<Object> packets;
}
