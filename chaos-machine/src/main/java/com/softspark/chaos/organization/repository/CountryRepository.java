package com.softspark.chaos.organization.repository;

import com.softspark.chaos.organization.model.Country;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for country reference-data entities.
 */
@Repository
public interface CountryRepository extends JpaRepository<Country, String> {

  /**
   * Checks whether a country with the given ISO code already exists.
   *
   * @param isoCode the ISO code to check (expected upper-cased)
   * @return {@code true} if a country with the ISO code exists
   */
  boolean existsByIsoCode(String isoCode);

  /**
   * Finds a country by its ISO code.
   *
   * @param isoCode the ISO code to look up (expected upper-cased)
   * @return the country, if present
   */
  Optional<Country> findByIsoCode(String isoCode);
}
