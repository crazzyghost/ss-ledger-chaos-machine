package com.softspark.chaos.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.soabase.recordbuilder.core.RecordBuilder;

/**
 * Request body for the login endpoint.
 *
 * @param email the user's email address
 * @param password the user's password
 */
@RecordBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginRequest(
    @JsonProperty("email") String email, @JsonProperty("password") String password) {}
