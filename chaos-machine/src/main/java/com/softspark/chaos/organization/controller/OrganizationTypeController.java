package com.softspark.chaos.organization.controller;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.organization.dto.CreateOrganizationTypeRequest;
import com.softspark.chaos.organization.dto.OrganizationTypeResponse;
import com.softspark.chaos.organization.dto.UpdateOrganizationTypeRequest;
import com.softspark.chaos.organization.service.OrganizationTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for organization type reference-data operations.
 *
 * <p>Provides endpoints for creating, listing, retrieving, and updating organization types.
 */
@RestController
@RequestMapping("/api/v0/organization-types")
@Tag(name = "Organization Types", description = "Organization type reference data")
public class OrganizationTypeController {

  private final OrganizationTypeService organizationTypeService;

  public OrganizationTypeController(OrganizationTypeService organizationTypeService) {
    this.organizationTypeService = organizationTypeService;
  }

  /**
   * Creates a new organization type.
   *
   * @param request the creation request
   * @return the created organization type
   */
  @PostMapping
  @Operation(
      summary = "Create an organization type",
      description = "Creates a new organization type with a server-generated UUID identifier")
  public ResponseEntity<OrganizationTypeResponse> createOrganizationType(
      @Valid @RequestBody CreateOrganizationTypeRequest request) {
    var created = organizationTypeService.createOrganizationType(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /**
   * Retrieves an organization type by ID.
   *
   * @param organizationTypeId the organization type ID
   * @return the organization type
   */
  @GetMapping("/{organizationTypeId}")
  @Operation(
      summary = "Get an organization type",
      description = "Retrieves an organization type by its ID")
  public ResponseEntity<OrganizationTypeResponse> getOrganizationType(
      @PathVariable String organizationTypeId) {
    var organizationType = organizationTypeService.getOrganizationType(organizationTypeId);
    return ResponseEntity.ok(organizationType);
  }

  /**
   * Lists organization types with pagination.
   *
   * @param page    the page number (0-indexed)
   * @param perPage the number of items per page
   * @return a paginated list of organization types
   */
  @GetMapping
  @Operation(
      summary = "List organization types",
      description = "Lists organization types with pagination")
  public ResponseEntity<PageResponse<OrganizationTypeResponse>> listOrganizationTypes(
      @Parameter(description = "Page number (0-indexed)") @RequestParam(required = false)
          Integer page,
      @Parameter(description = "Number of items per page (max 100)") @RequestParam(required = false)
          Integer perPage) {
    var result = organizationTypeService.listOrganizationTypes(page, perPage);
    return ResponseEntity.ok(result);
  }

  /**
   * Updates an existing organization type.
   *
   * @param organizationTypeId the organization type ID
   * @param request            the update request
   * @return the updated organization type
   */
  @PutMapping("/{organizationTypeId}")
  @Operation(
      summary = "Update an organization type",
      description = "Updates an organization type's name")
  public ResponseEntity<OrganizationTypeResponse> updateOrganizationType(
      @PathVariable String organizationTypeId,
      @Valid @RequestBody UpdateOrganizationTypeRequest request) {
    var updated = organizationTypeService.updateOrganizationType(organizationTypeId, request);
    return ResponseEntity.ok(updated);
  }
}
