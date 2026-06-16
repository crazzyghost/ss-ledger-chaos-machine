package com.softspark.chaos.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Result from the auth service token introspection endpoint.
 *
 * @param active whether the token is currently valid
 * @param subject the principal (e.g. email) identified by the token
 * @param authorities the list of granted authority strings (e.g. "ROLE_USER")
 */
@RecordBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenVerificationResult(
    @JsonProperty("active") boolean active,
    @Nullable @JsonProperty("subject") String subject,
    @Nullable @JsonProperty("authorities") List<String> authorities) {}
