package com.softspark.chaos.organization.controller;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.organization.dto.CountryResponse;
import com.softspark.chaos.organization.dto.CreateCountryRequest;
import com.softspark.chaos.organization.dto.UpdateCountryRequest;
import com.softspark.chaos.organization.service.CountryService;
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
 * REST controller for country reference-data operations.
 *
 * <p>Provides endpoints for creating, listing, retrieving, and updating countries.
 */
@RestController
@RequestMapping("/api/v0/countries")
@Tag(name = "Countries", description = "Country reference data")
public class CountryController {

  private final CountryService countryService;

  public CountryController(CountryService countryService) {
    this.countryService = countryService;
  }

  /**
   * Creates a new country.
   *
   * @param request the creation request
   * @return the created country
   */
  @PostMapping
  @Operation(
      summary = "Create a country",
      description = "Creates a new country with a server-generated UUID identifier")
  public ResponseEntity<CountryResponse> createCountry(
      @Valid @RequestBody CreateCountryRequest request) {
    var created = countryService.createCountry(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /**
   * Retrieves a country by ID.
   *
   * @param countryId the country ID
   * @return the country
   */
  @GetMapping("/{countryId}")
  @Operation(summary = "Get a country", description = "Retrieves a country by its ID")
  public ResponseEntity<CountryResponse> getCountry(@PathVariable String countryId) {
    var country = countryService.getCountry(countryId);
    return ResponseEntity.ok(country);
  }

  /**
   * Lists countries with pagination.
   *
   * @param page    the page number (0-indexed)
   * @param perPage the number of items per page
   * @return a paginated list of countries
   */
  @GetMapping
  @Operation(summary = "List countries", description = "Lists countries with pagination")
  public ResponseEntity<PageResponse<CountryResponse>> listCountries(
      @Parameter(description = "Page number (0-indexed)") @RequestParam(required = false)
          Integer page,
      @Parameter(description = "Number of items per page (max 100)") @RequestParam(required = false)
          Integer perPage) {
    var result = countryService.listCountries(page, perPage);
    return ResponseEntity.ok(result);
  }

  /**
   * Updates an existing country.
   *
   * @param countryId the country ID
   * @param request   the update request
   * @return the updated country
   */
  @PutMapping("/{countryId}")
  @Operation(
      summary = "Update a country",
      description = "Updates a country's name, ISO code, and status")
  public ResponseEntity<CountryResponse> updateCountry(
      @PathVariable String countryId, @Valid @RequestBody UpdateCountryRequest request) {
    var updated = countryService.updateCountry(countryId, request);
    return ResponseEntity.ok(updated);
  }
}
