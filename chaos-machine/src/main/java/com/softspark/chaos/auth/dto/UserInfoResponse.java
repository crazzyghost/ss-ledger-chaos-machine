package com.softspark.chaos.auth.dto;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

/**
 * Response for the {@code GET /api/v0/auth/me} endpoint.
 *
 * @param subject the authenticated principal name
 * @param authorities the list of authority strings for the principal
 */
@RecordBuilder
public record UserInfoResponse(String subject, List<String> authorities) {}
