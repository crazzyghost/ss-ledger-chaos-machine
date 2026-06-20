package com.softspark.chaos.organization.dto;

import com.softspark.chaos.base.validation.IsInEnum;
import com.softspark.chaos.organization.enumeration.OrganizationStatus;
import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Request record for onboarding an organization.
 *
 * @param organizationTypeId  the referenced organization type ID (must exist)
 * @param countryId           the referenced country ID (must exist)
 * @param name                the organization name
 * @param primaryContactEmail optional primary contact email (validated when present)
 * @param phoneNumbers        optional list of phone numbers
 * @param status              optional status (defaults to ACTIVE)
 */
@RecordBuilder
public record CreateOrganizationRequest(
    @NotBlank(message = "Name is required") String name,
    @NotBlank(message = "Organization type ID is required") String organizationTypeId,
    @NotBlank(message = "Country ID is required") String countryId,
    @Email(message = "Primary contact email must be a valid email address")
        String primaryContactEmail,
    List<String> phoneNumbers,
    @IsInEnum(enumClass = OrganizationStatus.class, message = "Invalid status") String status) {}
