package com.softspark.chaos.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AuthServiceLoginResponse(
    @JsonProperty("http_status") int httpStatus,
    @JsonProperty("status") String status,
    @JsonProperty("status_code") String statusCode,
    @JsonProperty("message") String message,
    @JsonProperty("data") AuthServiceLoginResponseData data) {}
