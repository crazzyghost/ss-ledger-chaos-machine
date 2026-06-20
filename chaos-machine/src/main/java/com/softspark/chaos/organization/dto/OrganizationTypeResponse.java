package com.softspark.chaos.organization.dto;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;

/**
 * Response record representing an organization type.
 *
 * @param organizationTypeId the organization type ID (UUID v4)
 * @param name               the organization type name
 * @param createdAt          the creation timestamp
 * @param updatedAt          the last update timestamp
 */
@RecordBuilder
public record OrganizationTypeResponse(
    String organizationTypeId, String name, Instant createdAt, Instant updatedAt) {}
