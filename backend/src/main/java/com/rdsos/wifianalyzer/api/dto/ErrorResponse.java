package com.rdsos.wifianalyzer.api.dto;

import lombok.Value;

/** Body of every error response: {@code {"error":"..."}}. */
@Value
public class ErrorResponse {
    String error;
}
