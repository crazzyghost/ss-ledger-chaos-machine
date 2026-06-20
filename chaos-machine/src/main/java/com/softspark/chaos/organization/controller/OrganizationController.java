package com.softspark.chaos.organization.controller;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.organization.dto.CreateOrganizationRequest;
import com.softspark.chaos.organization.dto.OrganizationResponse;
import com.softspark.chaos.organization.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for organization onboarding operations.
 *
 * <p>Provides endpoints for onboarding, listing, and retrieving organizations.
 */
@RestController
@RequestMapping("/api/v0/organizations")
@Tag(name = "Organizations", description = "Organization onboarding")
public class OrganizationController {

  private final OrganizationService organizationService;

  public OrganizationController(OrganizationService organizationService) {
    this.organizationService = organizationService;
  }

  /**
   * Onboards a new organization.
   *
   * @param request the onboarding request
   * @return the onboarded organization
   */
  @PostMapping
  @Operation(
      summary = "Onboard an organization",
      description =
          "Onboards a new organization with a server-generated UUID identifier, validating the "
              + "referenced country and organization type and snapshotting their values")
  public ResponseEntity<OrganizationResponse> onboardOrganization(
      @Valid @RequestBody CreateOrganizationRequest request) {
    var created = organizationService.onboard(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /**
   * Retrieves an organization by ID.
   *
   * @param organizationId the organization ID
   * @return the organization
   */
  @GetMapping("/{organizationId}")
  @Operation(summary = "Get an organization", description = "Retrieves an organization by its ID")
  public ResponseEntity<OrganizationResponse> getOrganization(@PathVariable String organizationId) {
    var organization = organizationService.getOrganization(organizationId);
    return ResponseEntity.ok(organization);
  }

  /**
   * Lists organizations with pagination.
   *
   * @param page    the page number (0-indexed)
   * @param perPage the number of items per page
   * @return a paginated list of organizations
   */
  @GetMapping
  @Operation(summary = "List organizations", description = "Lists organizations with pagination")
  public ResponseEntity<PageResponse<OrganizationResponse>> listOrganizations(
      @Parameter(description = "Page number (0-indexed)") @RequestParam(required = false)
          Integer page,
      @Parameter(description = "Number of items per page (max 100)") @RequestParam(required = false)
          Integer perPage) {
    var result = organizationService.listOrganizations(page, perPage);
    return ResponseEntity.ok(result);
  }
}
