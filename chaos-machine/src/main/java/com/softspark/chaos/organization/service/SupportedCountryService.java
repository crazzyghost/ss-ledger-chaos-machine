package com.softspark.chaos.organization.service;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.organization.dto.CreateSupportedCountryRequest;
import com.softspark.chaos.organization.dto.CurrencyRefResponse;
import com.softspark.chaos.organization.dto.SupportedCountryResponse;
import com.softspark.chaos.organization.enumeration.SupportedCountryStatus;
import com.softspark.chaos.organization.model.Country;
import com.softspark.chaos.organization.model.Currency;
import com.softspark.chaos.organization.model.SupportedCountry;
import com.softspark.chaos.organization.repository.CountryRepository;
import com.softspark.chaos.organization.repository.CurrencyRepository;
import com.softspark.chaos.organization.repository.SupportedCountryRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing the supported-country subset shown on the onboarding form.
 *
 * <p>Validates the referenced country exists, enforces a unique membership per country, and resolves
 * the country + primary currency for display. The list endpoint batch-fetches countries and
 * currencies to avoid N+1 queries.
 */
@Service
public class SupportedCountryService {

  private static final Logger log = LoggerFactory.getLogger(SupportedCountryService.class);
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final SupportedCountryRepository supportedCountryRepository;
  private final CountryRepository countryRepository;
  private final CurrencyRepository currencyRepository;

  public SupportedCountryService(
      SupportedCountryRepository supportedCountryRepository,
      CountryRepository countryRepository,
      CurrencyRepository currencyRepository) {
    this.supportedCountryRepository = supportedCountryRepository;
    this.countryRepository = countryRepository;
    this.currencyRepository = currencyRepository;
  }

  /**
   * Marks a country supported.
   *
   * @param request the creation request
   * @return the created supported-country membership
   * @throws NotFoundException   if the referenced country does not exist
   * @throws ConflictException   if the country is already supported
   * @throws BadRequestException if the status is invalid
   */
  @Transactional
  public SupportedCountryResponse createSupportedCountry(CreateSupportedCountryRequest request) {
    log.info("Marking country supported: {}", request.countryId());

    var country =
        countryRepository
            .findById(request.countryId())
            .orElseThrow(
                () -> new NotFoundException("Country not found: " + request.countryId()));

    if (supportedCountryRepository.existsByCountryId(country.getCountryId())) {
      throw new ConflictException("Country already supported: " + country.getCountryId());
    }

    SupportedCountryStatus status = parseStatus(request.status());

    var supported = new SupportedCountry();
    supported.setSupportedCountryId(UUID.randomUUID().toString());
    supported.setCountryId(country.getCountryId());
    supported.setStatus(status);

    var saved = supportedCountryRepository.save(supported);
    log.info("Marked country supported: {} ({})", saved.getSupportedCountryId(), country.getName());

    return mapToResponse(saved, country, resolveCurrency(country.getPrimaryCurrencyId()));
  }

  /**
   * Retrieves a supported-country membership by ID.
   *
   * @param supportedCountryId the membership ID
   * @return the membership
   * @throws NotFoundException if the membership is not found
   */
  @Transactional(readOnly = true)
  public SupportedCountryResponse getSupportedCountry(String supportedCountryId) {
    log.debug("Fetching supported country: {}", supportedCountryId);
    var supported = findOrThrow(supportedCountryId);
    var country = countryRepository.findById(supported.getCountryId()).orElse(null);
    var currency =
        country != null ? resolveCurrency(country.getPrimaryCurrencyId()) : null;
    return mapToResponse(supported, country, currency);
  }

  /**
   * Lists supported countries with pagination, search, and sorting, resolving each country and its
   * primary currency.
   *
   * <p>Because the displayed fields (country name, ISO code, primary currency) live on joined
   * reference tables rather than on {@code supported_country} itself, and the supported set is a
   * small curated subset, search and sort are applied in-service over the fully-resolved list.
   *
   * @param page    the page number (0-indexed)
   * @param perPage the number of items per page
   * @param search  optional term matched against country name, ISO code, primary currency code, or
   *                status
   * @param sortBy  optional sort key ({@code country}, {@code isoCode}, {@code status},
   *                {@code createdAt})
   * @param sortDir optional sort direction ({@code asc}/{@code desc})
   * @return a paginated list of supported countries
   */
  @Transactional(readOnly = true)
  public PageResponse<SupportedCountryResponse> listSupportedCountries(
      Integer page, Integer perPage, String search, String sortBy, String sortDir) {
    log.debug("Listing supported countries");

    int pageNum = page != null && page >= 0 ? page : 0;
    int pageSize =
        perPage != null && perPage > 0 ? Math.min(perPage, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

    var resolved = resolveAll(supportedCountryRepository.findAll());

    if (search != null && !search.isBlank()) {
      String q = search.trim().toLowerCase();
      resolved = resolved.stream().filter(r -> matchesSearch(r, q)).toList();
    }

    resolved = sortResolved(resolved, sortBy, sortDir);

    long total = resolved.size();
    int from = Math.min(pageNum * pageSize, resolved.size());
    int to = Math.min(from + pageSize, resolved.size());
    var items = List.copyOf(resolved.subList(from, to));

    return new PageResponse<>(items, pageNum, pageSize, total);
  }

  /** Resolves supported-country rows into responses, batch-fetching countries + currencies. */
  private List<SupportedCountryResponse> resolveAll(List<SupportedCountry> rows) {
    var countriesById =
        countryRepository.findAllById(rows.stream().map(SupportedCountry::getCountryId).toList())
            .stream()
            .collect(Collectors.toMap(Country::getCountryId, Function.identity()));
    var currencyIds =
        countriesById.values().stream()
            .map(Country::getPrimaryCurrencyId)
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();
    Map<String, Currency> currenciesById =
        currencyRepository.findAllById(currencyIds).stream()
            .collect(Collectors.toMap(Currency::getCurrencyId, Function.identity()));

    return rows.stream()
        .map(
            supported -> {
              var country = countriesById.get(supported.getCountryId());
              CurrencyRefResponse currencyRef =
                  country != null && country.getPrimaryCurrencyId() != null
                      ? toCurrencyRef(currenciesById.get(country.getPrimaryCurrencyId()))
                      : null;
              return mapToResponse(supported, country, currencyRef);
            })
        .toList();
  }

  private boolean matchesSearch(SupportedCountryResponse r, String q) {
    String name = r.country() != null ? r.country().name() : null;
    String iso = r.country() != null ? r.country().isoCode() : null;
    String code =
        r.country() != null && r.country().primaryCurrency() != null
            ? r.country().primaryCurrency().code()
            : null;
    String status = r.status() != null ? r.status().name() : null;
    return contains(name, q) || contains(iso, q) || contains(code, q) || contains(status, q);
  }

  private static boolean contains(String value, String q) {
    return value != null && value.toLowerCase().contains(q);
  }

  private List<SupportedCountryResponse> sortResolved(
      List<SupportedCountryResponse> resolved, String sortBy, String sortDir) {
    Comparator<SupportedCountryResponse> comparator =
        switch (sortBy == null ? "" : sortBy) {
          case "isoCode" -> Comparator.comparing(SupportedCountryServiceComparators::isoCode,
              Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
          case "status" -> Comparator.comparing(
              r -> r.status() != null ? r.status().name() : "", String.CASE_INSENSITIVE_ORDER);
          case "createdAt" -> Comparator.comparing(
              SupportedCountryResponse::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
          default -> Comparator.comparing(SupportedCountryServiceComparators::countryName,
              Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        };
    if ("desc".equalsIgnoreCase(sortDir)) {
      comparator = comparator.reversed();
    }
    return resolved.stream().sorted(comparator).toList();
  }

  /** Null-safe field extractors used by the in-service comparators. */
  private static final class SupportedCountryServiceComparators {
    private static String countryName(SupportedCountryResponse r) {
      return r.country() != null ? r.country().name() : null;
    }

    private static String isoCode(SupportedCountryResponse r) {
      return r.country() != null ? r.country().isoCode() : null;
    }
  }

  /**
   * Removes a supported-country membership.
   *
   * @param supportedCountryId the membership ID
   * @throws NotFoundException if the membership is not found
   */
  @Transactional
  public void deleteSupportedCountry(String supportedCountryId) {
    log.info("Removing supported country: {}", supportedCountryId);
    var supported = findOrThrow(supportedCountryId);
    supportedCountryRepository.delete(supported);
  }

  private SupportedCountry findOrThrow(String supportedCountryId) {
    return supportedCountryRepository
        .findById(supportedCountryId)
        .orElseThrow(
            () -> new NotFoundException("Supported country not found: " + supportedCountryId));
  }

  private SupportedCountryStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return SupportedCountryStatus.ACTIVE;
    }
    try {
      return SupportedCountryStatus.valueOf(status.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Invalid status: " + status, e);
    }
  }

  private CurrencyRefResponse resolveCurrency(String currencyId) {
    if (currencyId == null || currencyId.isBlank()) {
      return null;
    }
    return currencyRepository.findById(currencyId).map(this::toCurrencyRef).orElse(null);
  }

  private CurrencyRefResponse toCurrencyRef(Currency currency) {
    if (currency == null) {
      return null;
    }
    return new CurrencyRefResponse(
        currency.getCurrencyId(), currency.getCode(), currency.getName());
  }

  private SupportedCountryResponse mapToResponse(
      SupportedCountry supported, Country country, CurrencyRefResponse currency) {
    SupportedCountryResponse.Country countryBlock =
        country != null
            ? new SupportedCountryResponse.Country(
                country.getCountryId(), country.getName(), country.getIsoCode(), currency)
            : null;
    return new SupportedCountryResponse(
        supported.getSupportedCountryId(),
        supported.getCountryId(),
        supported.getStatus(),
        countryBlock,
        supported.getCreatedAt(),
        supported.getUpdatedAt());
  }

  /**
   * Returns the active supported-country IDs as a guard source for onboarding.
   *
   * @param countryId the country ID to check
   * @return {@code true} if the country is in the supported set and ACTIVE
   */
  @Transactional(readOnly = true)
  public boolean isActivelySupported(String countryId) {
    return supportedCountryRepository
        .findByCountryId(countryId)
        .map(s -> s.getStatus() == SupportedCountryStatus.ACTIVE)
        .orElse(false);
  }
}
