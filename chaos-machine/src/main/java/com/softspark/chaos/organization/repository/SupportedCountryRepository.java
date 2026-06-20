package com.softspark.chaos.organization.repository;

import com.softspark.chaos.organization.model.SupportedCountry;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for supported-country entities.
 */
@Repository
public interface SupportedCountryRepository extends JpaRepository<SupportedCountry, String> {

  /**
   * Checks whether the given country is already in the supported set.
   *
   * @param countryId the referenced country ID
   * @return {@code true} if a supported-country row references the country
   */
  boolean existsByCountryId(String countryId);

  /**
   * Finds the supported-country membership for a country, if any.
   *
   * @param countryId the referenced country ID
   * @return the supported-country row, if present
   */
  Optional<SupportedCountry> findByCountryId(String countryId);
}
