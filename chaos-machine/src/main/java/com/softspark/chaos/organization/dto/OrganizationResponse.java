package com.softspark.chaos.organization.dto;

import com.softspark.chaos.organization.enumeration.OrganizationStatus;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;
import java.util.List;

/**
 * Response record representing an onboarded organization.
 *
 * <p>The {@code typeName}, {@code countryName}, {@code countryIsoCode}, {@code countryStatus}, and
 * {@code countryModifiedDate} fields are point-in-time snapshots of the referenced reference data
 * captured at onboarding time; they are not re-derived on read and may diverge from the current
 * country/type values after later edits.
 *
 * @param organizationId      the organization ID (UUID v4)
 * @param name                the organization name
 * @param organizationTypeId  the referenced organization type ID (null for legacy rows)
 * @param countryId           the referenced country ID (null for legacy rows)
 * @param typeName            snapshot of the organization type name at onboarding time
 * @param countryName         snapshot of the country name at onboarding time
 * @param countryIsoCode      snapshot of the country ISO code at onboarding time
 * @param countryStatus       snapshot of the country status at onboarding time
 * @param countryModifiedDate snapshot of the country modified date at onboarding time
 * @param primaryContactEmail the primary contact email (nullable)
 * @param phoneNumbers        the list of phone numbers (never null; empty when none)
 * @param status              the organization status
 * @param createdAt           the creation timestamp
 * @param updatedAt           the last update timestamp
 * @param eventId             the enqueued onboarding event ID (set only on the onboard path; null
 *                            for get/list responses)
 */
@RecordBuilder
public record OrganizationResponse(
    String organizationId,
    String name,
    String organizationTypeId,
    String countryId,
    String typeName,
    String countryName,
    String countryIsoCode,
    String countryStatus,
    Instant countryModifiedDate,
    String primaryContactEmail,
    List<String> phoneNumbers,
    OrganizationStatus status,
    Instant createdAt,
    Instant updatedAt,
    String eventId) {}
