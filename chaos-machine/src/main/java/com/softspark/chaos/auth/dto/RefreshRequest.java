package com.softspark.chaos.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.soabase.recordbuilder.core.RecordBuilder;

/**
 * Request body for the token-refresh endpoint.
 *
 * @param refreshToken the refresh token to exchange
 */
@RecordBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public record RefreshRequest(@JsonProperty("refresh_token") String refreshToken) {}
