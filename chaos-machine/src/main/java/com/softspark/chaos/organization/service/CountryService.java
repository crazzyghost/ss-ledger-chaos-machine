package com.softspark.chaos.organization.service;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.organization.dto.CountryResponse;
import com.softspark.chaos.organization.dto.CreateCountryRequest;
import com.softspark.chaos.organization.dto.UpdateCountryRequest;
import com.softspark.chaos.organization.enumeration.CountryStatus;
import com.softspark.chaos.organization.model.Country;
import com.softspark.chaos.organization.repository.CountryRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing country reference data.
 *
 * <p>Provides create, list (paginated), get-by-id, and update operations. ISO codes are normalised
 * to upper-case and enforced unique; identifiers are server-assigned UUID v4 values.
 */
@Service
public class CountryService {

  private static final Logger log = LoggerFactory.getLogger(CountryService.class);
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final CountryRepository countryRepository;

  public CountryService(CountryRepository countryRepository) {
    this.countryRepository = countryRepository;
  }

  /**
   * Creates a new country.
   *
   * @param request the creation request
   * @return the created country
   * @throws BadRequestException if the status is invalid
   * @throws ConflictException   if the ISO code already exists
   */
  @Transactional
  public CountryResponse createCountry(CreateCountryRequest request) {
    log.info("Creating country: {}", request.name());

    String isoCode = request.isoCode().toUpperCase();
    if (countryRepository.existsByIsoCode(isoCode)) {
      throw new ConflictException("Country already exists with ISO code: " + isoCode);
    }

    CountryStatus status = parseStatus(request.status());

    var country = new Country();
    country.setCountryId(UUID.randomUUID().toString());
    country.setName(request.name());
    country.setIsoCode(isoCode);
    country.setStatus(status);
    country.setModifiedDate(
        request.modifiedDate() != null ? request.modifiedDate() : Instant.now());

    var saved = countryRepository.save(country);
    log.info("Created country: {} ({})", saved.getCountryId(), saved.getIsoCode());

    return mapToResponse(saved);
  }

  /**
   * Retrieves a country by ID.
   *
   * @param countryId the country ID
   * @return the country
   * @throws NotFoundException if the country is not found
   */
  @Transactional(readOnly = true)
  public CountryResponse getCountry(String countryId) {
    log.debug("Fetching country: {}", countryId);
    var country =
        countryRepository
            .findById(countryId)
            .orElseThrow(() -> new NotFoundException("Country not found: " + countryId));
    return mapToResponse(country);
  }

  /**
   * Lists countries with pagination.
   *
   * @param page    the page number (0-indexed)
   * @param perPage the number of items per page
   * @return a paginated list of countries
   */
  @Transactional(readOnly = true)
  public PageResponse<CountryResponse> listCountries(Integer page, Integer perPage) {
    log.debug("Listing countries");

    int pageNum = page != null && page >= 0 ? page : 0;
    int pageSize =
        perPage != null && perPage > 0 ? Math.min(perPage, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
    Pageable pageable = PageRequest.of(pageNum, pageSize);

    Page<Country> countryPage = countryRepository.findAll(pageable);
    var items = countryPage.getContent().stream().map(this::mapToResponse).toList();

    return new PageResponse<>(items, pageNum, pageSize, countryPage.getTotalElements());
  }

  /**
   * Updates an existing country's name, ISO code, status, and modified date.
   *
   * @param countryId the country ID
   * @param request   the update request
   * @return the updated country
   * @throws NotFoundException   if the country is not found
   * @throws BadRequestException if the status is invalid
   * @throws ConflictException   if the new ISO code belongs to another country
   */
  @Transactional
  public CountryResponse updateCountry(String countryId, UpdateCountryRequest request) {
    log.info("Updating country: {}", countryId);

    var country =
        countryRepository
            .findById(countryId)
            .orElseThrow(() -> new NotFoundException("Country not found: " + countryId));

    String isoCode = request.isoCode().toUpperCase();
    if (!isoCode.equals(country.getIsoCode())
        && countryRepository
            .findByIsoCode(isoCode)
            .filter(existing -> !existing.getCountryId().equals(countryId))
            .isPresent()) {
      throw new ConflictException("Country already exists with ISO code: " + isoCode);
    }

    CountryStatus status = parseStatus(request.status());

    country.setName(request.name());
    country.setIsoCode(isoCode);
    country.setStatus(status);
    country.setModifiedDate(
        request.modifiedDate() != null ? request.modifiedDate() : Instant.now());

    var saved = countryRepository.save(country);
    log.info("Updated country: {} ({})", saved.getCountryId(), saved.getIsoCode());

    return mapToResponse(saved);
  }

  private CountryStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return CountryStatus.ACTIVE;
    }
    try {
      return CountryStatus.valueOf(status.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Invalid status: " + status, e);
    }
  }

  private CountryResponse mapToResponse(Country country) {
    return new CountryResponse(
        country.getCountryId(),
        country.getName(),
        country.getIsoCode(),
        country.getStatus(),
        country.getModifiedDate(),
        country.getCreatedAt(),
        country.getUpdatedAt());
  }
}
