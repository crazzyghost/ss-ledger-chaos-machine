package com.softspark.chaos.organization.service;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.base.SortSupport;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.organization.dto.CreateCurrencyRequest;
import com.softspark.chaos.organization.dto.CurrencyResponse;
import com.softspark.chaos.organization.dto.UpdateCurrencyRequest;
import com.softspark.chaos.organization.enumeration.CurrencyStatus;
import com.softspark.chaos.organization.model.Currency;
import com.softspark.chaos.organization.repository.CurrencyRepository;
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
 * Service for managing currency reference data.
 *
 * <p>Provides create, list (paginated), get-by-id, and update operations. ISO-4217 codes are
 * normalised to upper-case and enforced unique; identifiers are server-assigned UUID v4 values. The
 * {@link #upsertIfAbsent(String, String, String)} method backs the restcountries.com seeder and is
 * idempotent by {@code code}, never clobbering operator-edited rows.
 */
@Service
public class CurrencyService {

  private static final Logger log = LoggerFactory.getLogger(CurrencyService.class);
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;
  private static final Set<String> SORTABLE =
      Set.of("code", "name", "status", "createdAt", "updatedAt");
  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "code");

  private final CurrencyRepository currencyRepository;

  public CurrencyService(CurrencyRepository currencyRepository) {
    this.currencyRepository = currencyRepository;
  }

  /**
   * Creates a new currency.
   *
   * @param request the creation request
   * @return the created currency
   * @throws BadRequestException if the status is invalid
   * @throws ConflictException   if the ISO-4217 code already exists
   */
  @Transactional
  public CurrencyResponse createCurrency(CreateCurrencyRequest request) {
    log.info("Creating currency: {}", request.code());

    String code = request.code().toUpperCase();
    if (currencyRepository.existsByCode(code)) {
      throw new ConflictException("Currency already exists with code: " + code);
    }

    CurrencyStatus status = parseStatus(request.status());

    var currency = new Currency();
    currency.setCurrencyId(UUID.randomUUID().toString());
    currency.setCode(code);
    currency.setName(request.name());
    currency.setSymbol(request.symbol());
    currency.setStatus(status);

    var saved = currencyRepository.save(currency);
    log.info("Created currency: {} ({})", saved.getCurrencyId(), saved.getCode());

    return mapToResponse(saved);
  }

  /**
   * Retrieves a currency by ID.
   *
   * @param currencyId the currency ID
   * @return the currency
   * @throws NotFoundException if the currency is not found
   */
  @Transactional(readOnly = true)
  public CurrencyResponse getCurrency(String currencyId) {
    log.debug("Fetching currency: {}", currencyId);
    return mapToResponse(findOrThrow(currencyId));
  }

  /**
   * Lists currencies with pagination.
   *
   * @param page    the page number (0-indexed)
   * @param perPage the number of items per page
   * @return a paginated list of currencies
   */
  @Transactional(readOnly = true)
  public PageResponse<CurrencyResponse> listCurrencies(
      Integer page, Integer perPage, String search, String sortBy, String sortDir) {
    log.debug("Listing currencies");

    int pageNum = page != null && page >= 0 ? page : 0;
    int pageSize =
        perPage != null && perPage > 0 ? Math.min(perPage, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
    Sort sort = SortSupport.resolve(sortBy, sortDir, SORTABLE, DEFAULT_SORT);
    Pageable pageable = PageRequest.of(pageNum, pageSize, sort);

    Page<Currency> currencyPage =
        search != null && !search.isBlank()
            ? currencyRepository.findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
                search.trim(), search.trim(), pageable)
            : currencyRepository.findAll(pageable);
    var items = currencyPage.getContent().stream().map(this::mapToResponse).toList();

    return new PageResponse<>(items, pageNum, pageSize, currencyPage.getTotalElements());
  }

  /**
   * Updates an existing currency's code, name, symbol, and status.
   *
   * @param currencyId the currency ID
   * @param request    the update request
   * @return the updated currency
   * @throws NotFoundException   if the currency is not found
   * @throws BadRequestException if the status is invalid
   * @throws ConflictException   if the new code belongs to another currency
   */
  @Transactional
  public CurrencyResponse updateCurrency(String currencyId, UpdateCurrencyRequest request) {
    log.info("Updating currency: {}", currencyId);

    var currency = findOrThrow(currencyId);

    String code = request.code().toUpperCase();
    if (!code.equals(currency.getCode())
        && currencyRepository
            .findByCode(code)
            .filter(existing -> !existing.getCurrencyId().equals(currencyId))
            .isPresent()) {
      throw new ConflictException("Currency already exists with code: " + code);
    }

    CurrencyStatus status = parseStatus(request.status());

    currency.setCode(code);
    currency.setName(request.name());
    currency.setSymbol(request.symbol());
    currency.setStatus(status);

    var saved = currencyRepository.save(currency);
    log.info("Updated currency: {} ({})", saved.getCurrencyId(), saved.getCode());

    return mapToResponse(saved);
  }

  /**
   * Inserts a currency keyed by ISO-4217 code if one does not already exist; otherwise returns the
   * existing row unchanged.
   *
   * <p>Used by the restcountries.com seeder. Idempotent and seed-if-absent: existing rows (including
   * operator-edited ones) are never modified. New rows are created {@link CurrencyStatus#ACTIVE}.
   *
   * @param code   the ISO-4217 code (upper-cased internally)
   * @param name   the currency name (used only on insert)
   * @param symbol the optional currency symbol (used only on insert)
   * @return the existing or newly-created currency
   */
  @Transactional
  public Currency upsertIfAbsent(String code, String name, String symbol) {
    String normalized = code.toUpperCase();
    return currencyRepository
        .findByCode(normalized)
        .orElseGet(
            () -> {
              var currency = new Currency();
              currency.setCurrencyId(UUID.randomUUID().toString());
              currency.setCode(normalized);
              currency.setName(name != null && !name.isBlank() ? name : normalized);
              currency.setSymbol(symbol);
              currency.setStatus(CurrencyStatus.ACTIVE);
              return currencyRepository.save(currency);
            });
  }

  /**
   * Validates that the given ISO-4217 code is usable for creating an account: it must reference an
   * {@link CurrencyStatus#ACTIVE} currency in the reference table.
   *
   * <p>Soft dependency on Phase 010 reference data: when the {@code currency} table is empty (the
   * seeder has not run) validation degrades to a no-op, leaving upstream {@code @ISO4217} format
   * validation as the only guard. When the table is populated, an unknown code is rejected as a
   * {@link BadRequestException} and an inactive code as a {@link ConflictException}.
   *
   * @param code the ISO-4217 code to validate (case-insensitive)
   * @throws BadRequestException if the code is blank or unknown
   * @throws ConflictException   if the currency exists but is not ACTIVE
   */
  @Transactional(readOnly = true)
  public void assertUsable(String code) {
    if (code == null || code.isBlank()) {
      throw new BadRequestException("Currency is required", null);
    }
    if (currencyRepository.count() == 0) {
      log.debug("Currency reference table empty — skipping currency validation for {}", code);
      return;
    }
    var currency =
        currencyRepository
            .findByCode(code.toUpperCase())
            .orElseThrow(() -> new BadRequestException("Unknown currency: " + code, null));
    if (currency.getStatus() != CurrencyStatus.ACTIVE) {
      throw new ConflictException("Currency is not active: " + code);
    }
  }

  private Currency findOrThrow(String currencyId) {
    return currencyRepository
        .findById(currencyId)
        .orElseThrow(() -> new NotFoundException("Currency not found: " + currencyId));
  }

  private CurrencyStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return CurrencyStatus.ACTIVE;
    }
    try {
      return CurrencyStatus.valueOf(status.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Invalid status: " + status, e);
    }
  }

  private CurrencyResponse mapToResponse(Currency currency) {
    return new CurrencyResponse(
        currency.getCurrencyId(),
        currency.getCode(),
        currency.getName(),
        currency.getSymbol(),
        currency.getStatus(),
        currency.getCreatedAt(),
        currency.getUpdatedAt());
  }
}
