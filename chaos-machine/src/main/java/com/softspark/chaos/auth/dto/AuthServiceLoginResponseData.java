package com.softspark.chaos.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.annotation.Nullable;
import java.util.Map;

@RecordBuilder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AuthServiceLoginResponseData(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("user") Map<String, Object> user,
    @JsonProperty("realm") AuthServiceLoginResponseRealmData realm,
    @JsonProperty("required_permissions") @Nullable Map<String, Object> requiredPermissions) {}
