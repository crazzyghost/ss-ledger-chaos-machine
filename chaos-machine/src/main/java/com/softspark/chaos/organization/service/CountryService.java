package com.softspark.chaos.organization.service;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.base.SortSupport;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.organization.dto.CountryResponse;
import com.softspark.chaos.organization.dto.CreateCountryRequest;
import com.softspark.chaos.organization.dto.CurrencyRefResponse;
import com.softspark.chaos.organization.dto.UpdateCountryRequest;
import com.softspark.chaos.organization.enumeration.CountryStatus;
import com.softspark.chaos.organization.enumeration.CurrencyStatus;
import com.softspark.chaos.organization.model.Country;
import com.softspark.chaos.organization.model.Currency;
import com.softspark.chaos.organization.repository.CountryRepository;
import com.softspark.chaos.organization.repository.CurrencyRepository;
import java.time.Instant;
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
 * Service for managing country reference data.
 *
 * <p>Provides create, list (paginated), get-by-id, and update operations. ISO codes are normalised
 * to upper-case and enforced unique; identifiers are server-assigned UUID v4 values. When a
 * {@code primary_currency_id} is supplied it is validated to reference an existing, {@code ACTIVE}
 * currency, and responses resolve the primary currency for display.
 */
@Service
public class CountryService {

  private static final Logger log = LoggerFactory.getLogger(CountryService.class);
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;
  private static final Set<String> SORTABLE =
      Set.of("name", "isoCode", "status", "modifiedDate", "createdAt", "updatedAt");
  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "name");

  private final CountryRepository countryRepository;
  private final CurrencyRepository currencyRepository;

  public CountryService(
      CountryRepository countryRepository, CurrencyRepository currencyRepository) {
    this.countryRepository = countryRepository;
    this.currencyRepository = currencyRepository;
  }

  /**
   * Creates a new country.
   *
   * @param request the creation request
   * @return the created country
   * @throws BadRequestException if the status or primary currency is invalid
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
    validatePrimaryCurrency(request.primaryCurrencyId());

    var country = new Country();
    country.setCountryId(UUID.randomUUID().toString());
    country.setName(request.name());
    country.setIsoCode(isoCode);
    country.setStatus(status);
    country.setPrimaryCurrencyId(request.primaryCurrencyId());
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
  public PageResponse<CountryResponse> listCountries(
      Integer page, Integer perPage, String search, String sortBy, String sortDir) {
    log.debug("Listing countries");

    int pageNum = page != null && page >= 0 ? page : 0;
    int pageSize =
        perPage != null && perPage > 0 ? Math.min(perPage, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
    Sort sort = SortSupport.resolve(sortBy, sortDir, SORTABLE, DEFAULT_SORT);
    Pageable pageable = PageRequest.of(pageNum, pageSize, sort);

    Page<Country> countryPage =
        search != null && !search.isBlank()
            ? countryRepository.findByNameContainingIgnoreCaseOrIsoCodeContainingIgnoreCase(
                search.trim(), search.trim(), pageable)
            : countryRepository.findAll(pageable);
    var items = countryPage.getContent().stream().map(this::mapToResponse).toList();

    return new PageResponse<>(items, pageNum, pageSize, countryPage.getTotalElements());
  }

  /**
   * Updates an existing country's name, ISO code, status, primary currency, and modified date.
   *
   * @param countryId the country ID
   * @param request   the update request
   * @return the updated country
   * @throws NotFoundException   if the country is not found
   * @throws BadRequestException if the status or primary currency is invalid
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
    validatePrimaryCurrency(request.primaryCurrencyId());

    country.setName(request.name());
    country.setIsoCode(isoCode);
    country.setStatus(status);
    country.setPrimaryCurrencyId(request.primaryCurrencyId());
    country.setModifiedDate(
        request.modifiedDate() != null ? request.modifiedDate() : Instant.now());

    var saved = countryRepository.save(country);
    log.info("Updated country: {} ({})", saved.getCountryId(), saved.getIsoCode());

    return mapToResponse(saved);
  }

  /**
   * Inserts a country keyed by ISO code if one does not already exist; otherwise returns the
   * existing row unchanged.
   *
   * <p>Used by the restcountries.com seeder. Idempotent and seed-if-absent: existing rows (including
   * operator-edited ones) are never modified. New rows are created {@link CountryStatus#ACTIVE} with
   * the supplied primary currency.
   *
   * @param isoCode           the ISO 3166-1 code (upper-cased internally)
   * @param name              the country name (used only on insert)
   * @param primaryCurrencyId the resolved primary currency ID (used only on insert; nullable)
   * @return the existing or newly-created country
   */
  @Transactional
  public Country upsertIfAbsent(String isoCode, String name, String primaryCurrencyId) {
    String code = isoCode.toUpperCase();
    return countryRepository
        .findByIsoCode(code)
        .orElseGet(
            () -> {
              var country = new Country();
              country.setCountryId(UUID.randomUUID().toString());
              country.setName(name != null && !name.isBlank() ? name : code);
              country.setIsoCode(code);
              country.setStatus(CountryStatus.ACTIVE);
              country.setPrimaryCurrencyId(primaryCurrencyId);
              country.setModifiedDate(Instant.now());
              return countryRepository.save(country);
            });
  }

  private void validatePrimaryCurrency(String primaryCurrencyId) {
    if (primaryCurrencyId == null || primaryCurrencyId.isBlank()) {
      return;
    }
    var currency =
        currencyRepository
            .findById(primaryCurrencyId)
            .orElseThrow(
                () -> new BadRequestException("Unknown primary currency: " + primaryCurrencyId));
    if (currency.getStatus() != CurrencyStatus.ACTIVE) {
      throw new BadRequestException("Primary currency is not ACTIVE: " + currency.getCode());
    }
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
        country.getPrimaryCurrencyId(),
        resolveCurrency(country.getPrimaryCurrencyId()),
        country.getModifiedDate(),
        country.getCreatedAt(),
        country.getUpdatedAt());
  }

  private CurrencyRefResponse resolveCurrency(String currencyId) {
    if (currencyId == null || currencyId.isBlank()) {
      return null;
    }
    return currencyRepository.findById(currencyId).map(this::toCurrencyRef).orElse(null);
  }

  private CurrencyRefResponse toCurrencyRef(Currency currency) {
    return new CurrencyRefResponse(
        currency.getCurrencyId(), currency.getCode(), currency.getName());
  }
}
