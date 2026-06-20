package com.softspark.chaos.organization.service;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.organization.dto.CreateOrganizationRequest;
import com.softspark.chaos.organization.dto.OrganizationResponse;
import com.softspark.chaos.organization.enumeration.OrganizationStatus;
import com.softspark.chaos.organization.model.Organization;
import com.softspark.chaos.organization.outbox.OutboxEnqueuer;
import com.softspark.chaos.organization.repository.CountryRepository;
import com.softspark.chaos.organization.repository.OrganizationRepository;
import com.softspark.chaos.organization.repository.OrganizationTypeRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for onboarding and reading organizations.
 *
 * <p>Onboarding validates that the referenced country and organization type exist, assigns a
 * server-generated UUID v4 identifier, and copies the referenced reference-data values into the
 * organization's snapshot columns so the persisted row reflects the reference-data state at
 * onboarding time, independent of later edits.
 */
@Service
public class OrganizationService {

  private static final Logger log = LoggerFactory.getLogger(OrganizationService.class);
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final OrganizationRepository organizationRepository;
  private final CountryRepository countryRepository;
  private final OrganizationTypeRepository organizationTypeRepository;
  private final OutboxEnqueuer outboxEnqueuer;
  private final String defaultTenantId;

  public OrganizationService(
      OrganizationRepository organizationRepository,
      CountryRepository countryRepository,
      OrganizationTypeRepository organizationTypeRepository,
      OutboxEnqueuer outboxEnqueuer,
      @Value("${chaos.default-tenant-id:org_system}") String defaultTenantId) {
    this.organizationRepository = organizationRepository;
    this.countryRepository = countryRepository;
    this.organizationTypeRepository = organizationTypeRepository;
    this.outboxEnqueuer = outboxEnqueuer;
    this.defaultTenantId = defaultTenantId;
  }

  /**
   * Onboards a new organization.
   *
   * <p>Validates the referenced country and organization type exist, copies their values into the
   * organization's snapshot columns, assigns a UUID v4 identifier, and persists the organization.
   *
   * @param request the onboarding request
   * @return the onboarded organization
   * @throws NotFoundException   if the referenced country or organization type does not exist
   * @throws BadRequestException if the status is invalid
   */
  @Transactional
  public OrganizationResponse onboard(CreateOrganizationRequest request) {
    log.info("Onboarding organization: {}", request.name());

    var country =
        countryRepository
            .findById(request.countryId())
            .orElseThrow(() -> new NotFoundException("Country not found: " + request.countryId()));

    var organizationType =
        organizationTypeRepository
            .findById(request.organizationTypeId())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Organization type not found: " + request.organizationTypeId()));

    OrganizationStatus status = parseStatus(request.status());

    var organization = new Organization();
    organization.setOrganizationId(UUID.randomUUID().toString());
    organization.setName(request.name());
    organization.setOrganizationTypeId(organizationType.getOrganizationTypeId());
    organization.setCountryId(country.getCountryId());
    organization.setPrimaryContactEmail(request.primaryContactEmail());
    organization.setPhoneNumbers(
        request.phoneNumbers() != null ? request.phoneNumbers() : List.of());
    organization.setTypeName(organizationType.getName());
    organization.setCountryName(country.getName());
    organization.setCountryIsoCode(country.getIsoCode());
    organization.setCountryStatus(country.getStatus().name());
    organization.setCountryModifiedDate(country.getModifiedDate());
    organization.setStatus(status);

    var saved = organizationRepository.save(organization);
    log.info("Onboarded organization: {} ({})", saved.getOrganizationId(), saved.getName());

    // [Task 004] enqueue organization.onboarded outbox event in this same transaction
    String requestId = MDC.get("requestId");
    String correlationId = requestId != null ? requestId : UUID.randomUUID().toString();
    String eventId =
        outboxEnqueuer.enqueueOrganizationOnboarded(saved, correlationId, defaultTenantId);

    return mapToResponse(saved, eventId);
  }

  /**
   * Retrieves an organization by ID.
   *
   * @param organizationId the organization ID
   * @return the organization
   * @throws NotFoundException if the organization is not found
   */
  @Transactional(readOnly = true)
  public OrganizationResponse getOrganization(String organizationId) {
    log.debug("Fetching organization: {}", organizationId);
    var organization =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(() -> new NotFoundException("Organization not found: " + organizationId));
    return mapToResponse(organization);
  }

  /**
   * Lists organizations with pagination.
   *
   * @param page    the page number (0-indexed)
   * @param perPage the number of items per page
   * @return a paginated list of organizations
   */
  @Transactional(readOnly = true)
  public PageResponse<OrganizationResponse> listOrganizations(Integer page, Integer perPage) {
    log.debug("Listing organizations");

    int pageNum = page != null && page >= 0 ? page : 0;
    int pageSize =
        perPage != null && perPage > 0 ? Math.min(perPage, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
    Pageable pageable = PageRequest.of(pageNum, pageSize);

    Page<Organization> organizationPage = organizationRepository.findAll(pageable);
    var items = organizationPage.getContent().stream().map(this::mapToResponse).toList();

    return new PageResponse<>(items, pageNum, pageSize, organizationPage.getTotalElements());
  }

  private OrganizationStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return OrganizationStatus.ACTIVE;
    }
    try {
      return OrganizationStatus.valueOf(status.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Invalid status: " + status, e);
    }
  }

  private OrganizationResponse mapToResponse(Organization organization) {
    return mapToResponse(organization, null);
  }

  private OrganizationResponse mapToResponse(Organization organization, String eventId) {
    return new OrganizationResponse(
        organization.getOrganizationId(),
        organization.getName(),
        organization.getOrganizationTypeId(),
        organization.getCountryId(),
        organization.getTypeName(),
        organization.getCountryName(),
        organization.getCountryIsoCode(),
        organization.getCountryStatus(),
        organization.getCountryModifiedDate(),
        organization.getPrimaryContactEmail(),
        organization.getPhoneNumbers() != null ? organization.getPhoneNumbers() : List.of(),
        organization.getStatus(),
        organization.getCreatedAt(),
        organization.getUpdatedAt(),
        eventId);
  }
}
