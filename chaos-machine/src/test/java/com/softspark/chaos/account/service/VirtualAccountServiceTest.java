package com.softspark.chaos.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softspark.chaos.account.dto.CreateVirtualAccountRequest;
import com.softspark.chaos.account.enumeration.AccountOwnershipType;
import com.softspark.chaos.account.enumeration.AccountStatus;
import com.softspark.chaos.account.enumeration.CreatedVia;
import com.softspark.chaos.account.model.VirtualAccount;
import com.softspark.chaos.account.repository.OrganizationRepository;
import com.softspark.chaos.account.repository.VirtualAccountRepository;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.NotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for {@link VirtualAccountService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VirtualAccountService")
class VirtualAccountServiceTest {

  @Mock private VirtualAccountRepository virtualAccountRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private VirtualAccountService service;

  // ── Helpers ────────────────────────────────────────────────────────────────

  private VirtualAccount buildSystemVa(String vaId) {
    var va = new VirtualAccount();
    va.setVaId(vaId);
    va.setName("System Float");
    va.setOwnershipType(AccountOwnershipType.SYSTEM);
    va.setCurrency("GHS");
    va.setStatus(AccountStatus.ACTIVE);
    va.setCreatedVia(CreatedVia.API);
    return va;
  }

  private VirtualAccount buildOrgVa(String vaId, String orgId) {
    var va = new VirtualAccount();
    va.setVaId(vaId);
    va.setName("Org VA");
    va.setOwnershipType(AccountOwnershipType.ORGANIZATION);
    va.setOrganizationId(orgId);
    va.setCurrency("GHS");
    va.setStatus(AccountStatus.ACTIVE);
    va.setCreatedVia(CreatedVia.API);
    return va;
  }

  // ── createVirtualAccount ───────────────────────────────────────────────────

  @Nested
  @DisplayName("createVirtualAccount")
  class CreateVirtualAccountTests {

    @Test
    @DisplayName("SYSTEM VA is created without an organization")
    void systemVaCreatedWithoutOrg() {
      var req =
          new CreateVirtualAccountRequest(
              "Float Account", "SYSTEM", "GHS", null, null, null, null, "VA-001", false);
      when(virtualAccountRepository.existsById("VA-001")).thenReturn(false);
      var saved = buildSystemVa("VA-001");
      when(virtualAccountRepository.save(any())).thenReturn(saved);

      var response = service.createVirtualAccount(req);

      assertThat(response.ownershipType()).isEqualTo(AccountOwnershipType.SYSTEM);
      verify(organizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("ORGANIZATION VA without orgId throws BadRequestException")
    void orgVaWithoutOrgIdThrows() {
      var req =
          new CreateVirtualAccountRequest(
              "Merchant VA", "ORGANIZATION", "GHS", null, null, null, null, null, false);

      assertThatThrownBy(() -> service.createVirtualAccount(req))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("Organization ID is required");
    }

    @Test
    @DisplayName("new organization is saved when it does not exist")
    void newOrgIsSaved() {
      var req =
          new CreateVirtualAccountRequest(
              "Merchant VA", "ORGANIZATION", "GHS", "org-new", "New Org", null, null, null, false);
      when(organizationRepository.existsById("org-new")).thenReturn(false);
      var saved = buildOrgVa("va-gen", "org-new");
      when(virtualAccountRepository.save(any())).thenReturn(saved);

      service.createVirtualAccount(req);

      verify(organizationRepository).save(any());
    }

    @Test
    @DisplayName("existing organization is linked without saving a new org")
    void existingOrgLinkedWithoutSave() {
      var req =
          new CreateVirtualAccountRequest(
              "Merchant VA", "ORGANIZATION", "GHS", "org-existing", null, null, null, null, false);
      when(organizationRepository.existsById("org-existing")).thenReturn(true);
      var saved = buildOrgVa("va-gen", "org-existing");
      when(virtualAccountRepository.save(any())).thenReturn(saved);

      service.createVirtualAccount(req);

      verify(organizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("duplicate vaId throws ConflictException")
    void duplicateVaIdThrowsConflict() {
      var req =
          new CreateVirtualAccountRequest(
              "Float Account", "SYSTEM", "GHS", null, null, null, null, "VA-DUP", false);
      when(virtualAccountRepository.existsById("VA-DUP")).thenReturn(true);

      assertThatThrownBy(() -> service.createVirtualAccount(req))
          .isInstanceOf(ConflictException.class)
          .hasMessageContaining("VA-DUP");
    }

    @Test
    @DisplayName("announce=true publishes VirtualAccountCreatedEvent")
    void announceTruePublishesEvent() {
      var req =
          new CreateVirtualAccountRequest(
              "Merchant VA", "ORGANIZATION", "GHS", "org-123", null, null, null, null, true);
      when(organizationRepository.existsById("org-123")).thenReturn(true);
      var saved = buildOrgVa("va-gen", "org-123");
      when(virtualAccountRepository.save(any())).thenReturn(saved);

      service.createVirtualAccount(req);

      ArgumentCaptor<VirtualAccountService.VirtualAccountCreatedEvent> captor =
          ArgumentCaptor.forClass(VirtualAccountService.VirtualAccountCreatedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue().virtualAccount().getVaId()).isEqualTo("va-gen");
    }

    @Test
    @DisplayName("announce=false does not publish any event")
    void announceFalseDoesNotPublish() {
      var req =
          new CreateVirtualAccountRequest(
              "Float Account", "SYSTEM", "GHS", null, null, null, null, "VA-002", false);
      when(virtualAccountRepository.existsById("VA-002")).thenReturn(false);
      when(virtualAccountRepository.save(any())).thenReturn(buildSystemVa("VA-002"));

      service.createVirtualAccount(req);

      verify(eventPublisher, never()).publishEvent(any());
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
