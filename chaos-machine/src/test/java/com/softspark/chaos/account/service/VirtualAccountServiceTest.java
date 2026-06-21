package com.softspark.chaos.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softspark.chaos.account.bootstrap.CreateLedgerAccountRequest;
import com.softspark.chaos.account.bootstrap.LedgerAccountProvisioningClient;
import com.softspark.chaos.account.bootstrap.LedgerProvisioningException;
import com.softspark.chaos.account.dto.CreateVirtualAccountRequest;
import com.softspark.chaos.account.repository.VirtualAccountRepository;
import com.softspark.chaos.exception.BadGatewayException;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.exception.ServiceUnavailableException;
import com.softspark.chaos.organization.service.CurrencyService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link VirtualAccountService} (Phase 009 request-via-ledger behavior).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VirtualAccountService")
class VirtualAccountServiceTest {

  @Mock private VirtualAccountRepository virtualAccountRepository;
  @Mock private LedgerAccountProvisioningClient ledgerClient;
  @Mock private CurrencyService currencyService;

  @InjectMocks private VirtualAccountService service;

  private static CreateVirtualAccountRequest system(String code, String category) {
    return new CreateVirtualAccountRequest(
        "Float Account", "SYSTEM", "GHS", null, code, category, null, null, null);
  }

  private static CreateVirtualAccountRequest org(String orgId) {
    return new CreateVirtualAccountRequest(
        "Merchant VA", "ORGANIZATION", "GHS", orgId, null, null, null, null, null);
  }

  // ── requestCreate ──────────────────────────────────────────────────────────

  @Nested
  @DisplayName("requestCreate")
  class RequestCreateTests {

    @Test
    @DisplayName("SYSTEM request forwards to the ledger and writes nothing locally")
    void systemRequestForwarded() {
      var req = system("ASSET.PLATFORM.FLOAT", "ASSET");
      when(ledgerClient.createAccount(any(CreateLedgerAccountRequest.class), any()))
          .thenReturn("acct-1");

      var accepted = service.requestCreate(req, "tok");

      assertThat(accepted.status()).isEqualTo("REQUESTED");
      assertThat(accepted.accountCode()).isEqualTo("ASSET.PLATFORM.FLOAT");

      ArgumentCaptor<CreateLedgerAccountRequest> captor =
          ArgumentCaptor.forClass(CreateLedgerAccountRequest.class);
      verify(ledgerClient).createAccount(captor.capture(), any());
      assertThat(captor.getValue().accountOwnershipType()).isEqualTo("SYSTEM");
      assertThat(captor.getValue().currency()).isEqualTo("GHS");
      verify(virtualAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("ORGANIZATION request defaults the category to LIABILITY")
    void orgRequestDefaultsCategory() {
      var req = org("org-123");
      when(ledgerClient.createAccount(any(CreateLedgerAccountRequest.class), any()))
          .thenReturn("acct-2");

      service.requestCreate(req, "tok");

      ArgumentCaptor<CreateLedgerAccountRequest> captor =
          ArgumentCaptor.forClass(CreateLedgerAccountRequest.class);
      verify(ledgerClient).createAccount(captor.capture(), any());
      assertThat(captor.getValue().accountCategory()).isEqualTo("LIABILITY");
      assertThat(captor.getValue().organizationId()).isEqualTo("org-123");
    }

    @Test
    @DisplayName("SYSTEM without accountCode throws BadRequestException")
    void systemWithoutCodeThrows() {
      assertThatThrownBy(() -> service.requestCreate(system(null, "ASSET"), "tok"))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("accountCode");
    }

    @Test
    @DisplayName("ORGANIZATION without orgId throws BadRequestException")
    void orgWithoutOrgIdThrows() {
      assertThatThrownBy(() -> service.requestCreate(org(null), "tok"))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("organizationId");
    }

    @Test
    @DisplayName("inactive currency is rejected before the ledger call")
    void inactiveCurrencyRejected() {
      doThrow(new ConflictException("Currency is not active: GHS"))
          .when(currencyService)
          .assertUsable("GHS");

      assertThatThrownBy(() -> service.requestCreate(org("org-1"), "tok"))
          .isInstanceOf(ConflictException.class);
      verify(ledgerClient, never()).createAccount(any(CreateLedgerAccountRequest.class), any());
    }

    @Test
    @DisplayName("ledger 5xx maps to BadGatewayException")
    void ledgerServerErrorMapsToBadGateway() {
      when(ledgerClient.createAccount(any(CreateLedgerAccountRequest.class), any()))
          .thenThrow(new LedgerProvisioningException("boom", 503));

      assertThatThrownBy(() -> service.requestCreate(org("org-1"), "tok"))
          .isInstanceOf(BadGatewayException.class);
    }

    @Test
    @DisplayName("ledger transport failure maps to ServiceUnavailableException")
    void ledgerTransportFailureMapsToServiceUnavailable() {
      when(ledgerClient.createAccount(any(CreateLedgerAccountRequest.class), any()))
          .thenThrow(new LedgerProvisioningException("unreachable"));

      assertThatThrownBy(() -> service.requestCreate(org("org-1"), "tok"))
          .isInstanceOf(ServiceUnavailableException.class);
    }
  }

  // ── getVirtualAccount ──────────────────────────────────────────────────────

  @Nested
  @DisplayName("getVirtualAccount")
  class GetVirtualAccountTests {

    @Test
    @DisplayName("throws NotFoundException when VA does not exist")
    void throwsNotFoundWhenMissing() {
      when(virtualAccountRepository.findById(anyString())).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getVirtualAccount("VA-MISSING"))
          .isInstanceOf(NotFoundException.class)
          .hasMessageContaining("VA-MISSING");
    }
  }
}
