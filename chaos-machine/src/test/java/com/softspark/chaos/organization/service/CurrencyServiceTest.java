package com.softspark.chaos.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.organization.dto.CreateCurrencyRequest;
import com.softspark.chaos.organization.dto.UpdateCurrencyRequest;
import com.softspark.chaos.organization.enumeration.CurrencyStatus;
import com.softspark.chaos.organization.model.Currency;
import com.softspark.chaos.organization.repository.CurrencyRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for {@link CurrencyService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CurrencyService")
class CurrencyServiceTest {

  @Mock private CurrencyRepository currencyRepository;

  @InjectMocks private CurrencyService service;

  private Currency buildCurrency(String id, String code, CurrencyStatus status) {
    var currency = new Currency();
    currency.setCurrencyId(id);
    currency.setCode(code);
    currency.setName("Ghanaian cedi");
    currency.setSymbol("₵");
    currency.setStatus(status);
    return currency;
  }

  @Nested
  @DisplayName("createCurrency")
  class CreateCurrencyTests {

    @Test
    @DisplayName("assigns a UUID id, uppercases code, defaults ACTIVE")
    void createAssignsDefaults() {
      var req = new CreateCurrencyRequest("ghs", "Ghanaian cedi", "₵", null);
      when(currencyRepository.existsByCode("GHS")).thenReturn(false);
      when(currencyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      var response = service.createCurrency(req);

      ArgumentCaptor<Currency> captor = ArgumentCaptor.forClass(Currency.class);
      verify(currencyRepository).save(captor.capture());
      var persisted = captor.getValue();

      assertThat(UUID.fromString(persisted.getCurrencyId())).isNotNull();
      assertThat(persisted.getCode()).isEqualTo("GHS");
      assertThat(persisted.getStatus()).isEqualTo(CurrencyStatus.ACTIVE);
      assertThat(response.code()).isEqualTo("GHS");
      assertThat(response.status()).isEqualTo(CurrencyStatus.ACTIVE);
    }

    @Test
    @DisplayName("duplicate code throws ConflictException")
    void duplicateCodeThrowsConflict() {
      var req = new CreateCurrencyRequest("USD", "US Dollar", "$", null);
      when(currencyRepository.existsByCode("USD")).thenReturn(true);

      assertThatThrownBy(() -> service.createCurrency(req))
          .isInstanceOf(ConflictException.class)
          .hasMessageContaining("USD");
    }
  }

  @Nested
  @DisplayName("updateCurrency")
  class UpdateCurrencyTests {

    @Test
    @DisplayName("throws NotFoundException when currency does not exist")
    void throwsNotFoundWhenMissing() {
      when(currencyRepository.findById("missing")).thenReturn(Optional.empty());

      var req = new UpdateCurrencyRequest("USD", "US Dollar", "$", null);
      assertThatThrownBy(() -> service.updateCurrency("missing", req))
          .isInstanceOf(NotFoundException.class)
          .hasMessageContaining("missing");
    }

    @Test
    @DisplayName("changing code to one owned by another currency throws ConflictException")
    void conflictingCodeThrows() {
      var existing = buildCurrency("c-1", "GHS", CurrencyStatus.ACTIVE);
      var other = buildCurrency("c-2", "USD", CurrencyStatus.ACTIVE);
      when(currencyRepository.findById("c-1")).thenReturn(Optional.of(existing));
      when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(other));

      var req = new UpdateCurrencyRequest("usd", "US Dollar", "$", null);
      assertThatThrownBy(() -> service.updateCurrency("c-1", req))
          .isInstanceOf(ConflictException.class)
          .hasMessageContaining("USD");
    }
  }

  @Nested
  @DisplayName("listCurrencies")
  class ListCurrenciesTests {

    @Test
    @DisplayName("no search uses findAll with the resolved sort")
    void noSearchUsesFindAll() {
      var page =
          new PageImpl<>(
              List.of(buildCurrency("c-1", "GHS", CurrencyStatus.ACTIVE)),
              PageRequest.of(0, 20),
              1);
      when(currencyRepository.findAll(any(Pageable.class))).thenReturn(page);

      var result = service.listCurrencies(0, 20, null, "name", "desc");

      assertThat(result.total()).isEqualTo(1);
      ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
      verify(currencyRepository).findAll(captor.capture());
      var order = captor.getValue().getSort().getOrderFor("name");
      assertThat(order).isNotNull();
      assertThat(order.isDescending()).isTrue();
    }

    @Test
    @DisplayName("search delegates to the code/name search query")
    void searchDelegates() {
      var page =
          new PageImpl<>(
              List.of(buildCurrency("c-1", "GHS", CurrencyStatus.ACTIVE)),
              PageRequest.of(0, 20),
              1);
      when(currencyRepository.findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
              org.mockito.ArgumentMatchers.eq("gh"),
              org.mockito.ArgumentMatchers.eq("gh"),
              any(Pageable.class)))
          .thenReturn(page);

      var result = service.listCurrencies(0, 20, "gh", null, null);

      assertThat(result.items()).hasSize(1);
      verify(currencyRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("unknown sort field falls back to the default (code asc)")
    void unknownSortFallsBack() {
      var page = new PageImpl<Currency>(List.of(), PageRequest.of(0, 20), 0);
      when(currencyRepository.findAll(any(Pageable.class))).thenReturn(page);

      service.listCurrencies(0, 20, null, "totallyNotAField", "desc");

      ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
      verify(currencyRepository).findAll(captor.capture());
      var order = captor.getValue().getSort().getOrderFor("code");
      assertThat(order).isNotNull();
      assertThat(order.isAscending()).isTrue();
    }
  }

  @Nested
  @DisplayName("upsertIfAbsent")
  class UpsertIfAbsentTests {

    @Test
    @DisplayName("returns the existing row without saving when the code already exists")
    void noOpWhenPresent() {
      var existing = buildCurrency("c-1", "GHS", CurrencyStatus.ACTIVE);
      when(currencyRepository.findByCode("GHS")).thenReturn(Optional.of(existing));

      var result = service.upsertIfAbsent("ghs", "Should be ignored", "X");

      assertThat(result).isSameAs(existing);
      verify(currencyRepository, never()).save(any());
    }

    @Test
    @DisplayName("inserts a new ACTIVE row when the code is absent")
    void insertsWhenAbsent() {
      when(currencyRepository.findByCode("EUR")).thenReturn(Optional.empty());
      when(currencyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      var result = service.upsertIfAbsent("eur", "Euro", "€");

      assertThat(result.getCode()).isEqualTo("EUR");
      assertThat(result.getName()).isEqualTo("Euro");
      assertThat(result.getStatus()).isEqualTo(CurrencyStatus.ACTIVE);
      assertThat(UUID.fromString(result.getCurrencyId())).isNotNull();
    }
  }
}
