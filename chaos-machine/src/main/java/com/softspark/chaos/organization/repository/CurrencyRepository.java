package com.softspark.chaos.organization.repository;

import com.softspark.chaos.organization.model.Currency;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for currency reference-data entities.
 */
@Repository
public interface CurrencyRepository extends JpaRepository<Currency, String> {

  /**
   * Checks whether a currency with the given ISO-4217 code already exists.
   *
   * @param code the ISO-4217 code to check (expected upper-cased)
   * @return {@code true} if a currency with the code exists
   */
  boolean existsByCode(String code);

  /**
   * Finds a currency by its ISO-4217 code.
   *
   * @param code the ISO-4217 code to look up (expected upper-cased)
   * @return the currency, if present
   */
  Optional<Currency> findByCode(String code);

  /**
   * Searches currencies whose code or name contains the given term (case-insensitive).
   *
   * @param code     the term matched against {@code code}
   * @param name     the term matched against {@code name}
   * @param pageable the page + sort request
   * @return a page of matching currencies
   */
  Page<Currency> findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
      String code, String name, Pageable pageable);
}
