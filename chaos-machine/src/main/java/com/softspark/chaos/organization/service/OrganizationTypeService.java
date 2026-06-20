package com.softspark.chaos.organization.service;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.base.SortSupport;
import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.organization.dto.CreateOrganizationTypeRequest;
import com.softspark.chaos.organization.dto.OrganizationTypeResponse;
import com.softspark.chaos.organization.dto.UpdateOrganizationTypeRequest;
import com.softspark.chaos.organization.model.OrganizationType;
import com.softspark.chaos.organization.repository.OrganizationTypeRepository;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing organization type reference data.
 *
 * <p>Provides create, list (paginated), get-by-id, and update operations. Names are enforced unique
 * case-insensitively; identifiers are server-assigned UUID v4 values.
 */
@Service
public class OrganizationTypeService {

  private static final Logger log = LoggerFactory.getLogger(OrganizationTypeService.class);
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;
  private static final Set<String> SORTABLE = Set.of("name", "createdAt", "updatedAt");
  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "name");

  private final OrganizationTypeRepository organizationTypeRepository;

  public OrganizationTypeService(OrganizationTypeRepository organizationTypeRepository) {
    this.organizationTypeRepository = organizationTypeRepository;
  }

  /**
   * Creates a new organization type.
   *
   * @param request the creation request
   * @return the created organization type
   * @throws ConflictException if the name already exists (case-insensitive)
   */
  @Transactional
  public OrganizationTypeResponse createOrganizationType(CreateOrganizationTypeRequest request) {
    log.info("Creating organization type: {}", request.name());

    if (organizationTypeRepository.findByNameIgnoreCase(request.name()).isPresent()) {
      throw new ConflictException("Organization type already exists with name: " + request.name());
    }

    var organizationType = new OrganizationType();
    organizationType.setOrganizationTypeId(UUID.randomUUID().toString());
    organizationType.setName(request.name());

    var saved = organizationTypeRepository.save(organizationType);
    log.info("Created organization type: {} ({})", saved.getOrganizationTypeId(), saved.getName());

    return mapToResponse(saved);
  }

  /**
   * Retrieves an organization type by ID.
   *
   * @param organizationTypeId the organization type ID
   * @return the organization type
   * @throws NotFoundException if the organization type is not found
   */
  @Transactional(readOnly = true)
  public OrganizationTypeResponse getOrganizationType(String organizationTypeId) {
    log.debug("Fetching organization type: {}", organizationTypeId);
    var organizationType =
        organizationTypeRepository
            .findById(organizationTypeId)
            .orElseThrow(
                () -> new NotFoundException("Organization type not found: " + organizationTypeId));
    return mapToResponse(organizationType);
  }

  /**
   * Lists organization types with pagination.
   *
   * @param page    the page number (0-indexed)
   * @param perPage the number of items per page
   * @return a paginated list of organization types
   */
  @Transactional(readOnly = true)
  public PageResponse<OrganizationTypeResponse> listOrganizationTypes(
      Integer page, Integer perPage, String search, String sortBy, String sortDir) {
    log.debug("Listing organization types");

    int pageNum = page != null && page >= 0 ? page : 0;
    int pageSize =
        perPage != null && perPage > 0 ? Math.min(perPage, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
    Sort sort = SortSupport.resolve(sortBy, sortDir, SORTABLE, DEFAULT_SORT);
    Pageable pageable = PageRequest.of(pageNum, pageSize, sort);

    Page<OrganizationType> typePage =
        search != null && !search.isBlank()
            ? organizationTypeRepository.findByNameContainingIgnoreCase(search.trim(), pageable)
            : organizationTypeRepository.findAll(pageable);
    var items = typePage.getContent().stream().map(this::mapToResponse).toList();

    return new PageResponse<>(items, pageNum, pageSize, typePage.getTotalElements());
  }

  /**
   * Updates an existing organization type's name.
   *
   * @param organizationTypeId the organization type ID
   * @param request            the update request
   * @return the updated organization type
   * @throws NotFoundException if the organization type is not found
   * @throws ConflictException if the new name belongs to another organization type
   */
  @Transactional
  public OrganizationTypeResponse updateOrganizationType(
      String organizationTypeId, UpdateOrganizationTypeRequest request) {
    log.info("Updating organization type: {}", organizationTypeId);

    var organizationType =
        organizationTypeRepository
            .findById(organizationTypeId)
            .orElseThrow(
                () -> new NotFoundException("Organization type not found: " + organizationTypeId));

    if (!request.name().equalsIgnoreCase(organizationType.getName())
        && organizationTypeRepository
            .findByNameIgnoreCase(request.name())
            .filter(existing -> !existing.getOrganizationTypeId().equals(organizationTypeId))
            .isPresent()) {
      throw new ConflictException("Organization type already exists with name: " + request.name());
    }

    organizationType.setName(request.name());

    var saved = organizationTypeRepository.save(organizationType);
    log.info("Updated organization type: {} ({})", saved.getOrganizationTypeId(), saved.getName());

    return mapToResponse(saved);
  }

  private OrganizationTypeResponse mapToResponse(OrganizationType organizationType) {
    return new OrganizationTypeResponse(
        organizationType.getOrganizationTypeId(),
        organizationType.getName(),
        organizationType.getCreatedAt(),
        organizationType.getUpdatedAt());
  }
}
