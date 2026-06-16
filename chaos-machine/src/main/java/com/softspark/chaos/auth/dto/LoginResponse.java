package com.softspark.chaos.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.annotation.Nullable;

/**
 * Response from the auth service login and refresh endpoints.
 *
 * <p>Both snake_case and camelCase field names are accepted to tolerate auth service
 * implementations that use either convention.
 *
 * @param accessToken the issued bearer token
 * @param tokenType the token type (typically "Bearer" or "bearer")
 * @param expiresIn seconds until the token expires
 * @param refreshToken an optional refresh token
 */
@RecordBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginResponse(
    @JsonProperty("access_token") @JsonAlias("accessToken") String accessToken,
    @JsonProperty("token_type") @JsonAlias("tokenType") String tokenType,
    @JsonProperty("expires_in") @JsonAlias({"expiresIn", "expires"}) Long expiresIn,
    @Nullable @JsonProperty("refresh_token") @JsonAlias("refreshToken") String refreshToken) {}
