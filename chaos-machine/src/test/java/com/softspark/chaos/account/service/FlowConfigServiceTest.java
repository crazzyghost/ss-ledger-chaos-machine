package com.softspark.chaos.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softspark.chaos.account.dto.UpdateFlowConfigRequest;
import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.account.model.AccountRoleEntity;
import com.softspark.chaos.account.model.FlowSlotConfig;
import com.softspark.chaos.account.repository.AccountRoleRepository;
import com.softspark.chaos.account.repository.FlowSlotConfigRepository;
import com.softspark.chaos.account.repository.VirtualAccountRepository;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.flow.model.FlowType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link FlowConfigService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlowConfigService")
class FlowConfigServiceTest {

  @Mock private FlowSlotConfigRepository flowSlotConfigRepository;
  @Mock private AccountRoleRepository accountRoleRepository;
  @Mock private VirtualAccountRepository virtualAccountRepository;

  @InjectMocks private FlowConfigService service;

  // ── Helpers ────────────────────────────────────────────────────────────────

  private FlowSlotConfig slot(FlowType flowType, String slotName, AccountRole role) {
    var s = new FlowSlotConfig();
    s.setId("id-" + slotName);
    s.setFlowType(flowType);
    s.setSlotName(slotName);
    s.setAccountRole(role);
    return s;
  }

  private FlowSlotConfig slotWithExplicit(
      FlowType flowType, String slotName, AccountRole role, String explicitVaId) {
    var s = slot(flowType, slotName, role);
    s.setExplicitVaId(explicitVaId);
    return s;
  }

  private AccountRoleEntity roleEntity(AccountRole role, String defaultVaId) {
    var entity = new AccountRoleEntity();
    entity.setRole(role);
    entity.setDefaultVaId(defaultVaId);
    return entity;
  }

  // ── getAllFlowConfigs ──────────────────────────────────────────────────────

  @Nested
  @DisplayName("getAllFlowConfigs")
  class GetAllFlowConfigsTests {

    @Test
    @DisplayName("returns grouped flow configs")
    void returnsGroupedConfigs() {
      var slot1 = slot(FlowType.COLLECTION_COMPLETED, "source", AccountRole.PLATFORM_FLOAT);
      var slot2 = slot(FlowType.COLLECTION_COMPLETED, "fee", AccountRole.PLATFORM_FEE);
      var slot3 = slot(FlowType.TOPUP_CONFIRMED, "destination", AccountRole.PLATFORM_FLOAT);
      when(flowSlotConfigRepository.findAll()).thenReturn(List.of(slot1, slot2, slot3));
      when(accountRoleRepository.findById(any())).thenReturn(Optional.empty());

      var result = service.getAllFlowConfigs();

      assertThat(result).hasSize(2);
      var collectionConfig =
          result.stream()
              .filter(r -> r.flowType() == FlowType.COLLECTION_COMPLETED)
              .findFirst()
              .orElseThrow();
      assertThat(collectionConfig.slots()).hasSize(2);
    }
  }

  // ── updateFlowConfig ──────────────────────────────────────────────────────

  @Nested
  @DisplayName("updateFlowConfig")
  class UpdateFlowConfigTests {

    @Test
    @DisplayName("updates existing slot with a valid role")
    void updatesExistingSlotWithValidRole() {
      var slotUpdate = new UpdateFlowConfigRequest.SlotUpdate("source", "PLATFORM_FLOAT", null);
      var request = new UpdateFlowConfigRequest(List.of(slotUpdate));

      when(accountRoleRepository.existsById(AccountRole.PLATFORM_FLOAT)).thenReturn(true);
      var existingSlot = slot(FlowType.COLLECTION_COMPLETED, "source", AccountRole.PLATFORM_FLOAT);
      when(flowSlotConfigRepository.findByFlowTypeAndSlotName(
              FlowType.COLLECTION_COMPLETED, "source"))
          .thenReturn(Optional.of(existingSlot));
      when(flowSlotConfigRepository.save(any())).thenReturn(existingSlot);
      when(flowSlotConfigRepository.findByFlowType(FlowType.COLLECTION_COMPLETED))
          .thenReturn(List.of(existingSlot));
      when(accountRoleRepository.findById(AccountRole.PLATFORM_FLOAT)).thenReturn(Optional.empty());

      var result = service.updateFlowConfig(FlowType.COLLECTION_COMPLETED, request);

      assertThat(result.flowType()).isEqualTo(FlowType.COLLECTION_COMPLETED);
      verify(flowSlotConfigRepository).save(any());
    }

    @Test
    @DisplayName("throws BadRequestException for invalid role")
    void throwsForInvalidRole() {
      var slotUpdate = new UpdateFlowConfigRequest.SlotUpdate("source", "NONEXISTENT_ROLE", null);
      var request = new UpdateFlowConfigRequest(List.of(slotUpdate));

      assertThatThrownBy(() -> service.updateFlowConfig(FlowType.COLLECTION_COMPLETED, request))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("Invalid account role");
    }

    @Test
    @DisplayName("throws BadRequestException for invalid explicit VA")
    void throwsForInvalidExplicitVa() {
      var slotUpdate = new UpdateFlowConfigRequest.SlotUpdate("source", null, "VA-MISSING");
      var request = new UpdateFlowConfigRequest(List.of(slotUpdate));

      when(virtualAccountRepository.existsById("VA-MISSING")).thenReturn(false);

      assertThatThrownBy(() -> service.updateFlowConfig(FlowType.COLLECTION_COMPLETED, request))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("Virtual account not found");
    }
  }

  // ── VA resolution precedence ──────────────────────────────────────────────

  @Nested
  @DisplayName("VA resolution precedence")
  class ResolutionPrecedenceTests {

    @Test
    @DisplayName("explicit VA on slot → effectiveVaId is the explicit VA")
    void explicitVaTakesPrecedence() {
      var slotConfig =
          slotWithExplicit(
              FlowType.COLLECTION_COMPLETED, "source", AccountRole.PLATFORM_FLOAT, "VA-EXPLICIT");
      when(flowSlotConfigRepository.findByFlowType(FlowType.COLLECTION_COMPLETED))
          .thenReturn(List.of(slotConfig));

      var result = service.getFlowConfig(FlowType.COLLECTION_COMPLETED);

      var slot = result.slots().get(0);
      assertThat(slot.effectiveVaId()).isEqualTo("VA-EXPLICIT");
    }

    @Test
    @DisplayName("no explicit VA, role set → effectiveVaId is the role's defaultVaId")
    void roleDefaultVaUsedWhenNoExplicit() {
      var slotConfig = slot(FlowType.COLLECTION_COMPLETED, "source", AccountRole.PLATFORM_FLOAT);
      when(flowSlotConfigRepository.findByFlowType(FlowType.COLLECTION_COMPLETED))
          .thenReturn(List.of(slotConfig));
      when(accountRoleRepository.findById(AccountRole.PLATFORM_FLOAT))
          .thenReturn(Optional.of(roleEntity(AccountRole.PLATFORM_FLOAT, "VA-ROLE-DEFAULT")));

      var result = service.getFlowConfig(FlowType.COLLECTION_COMPLETED);

      assertThat(result.slots().get(0).effectiveVaId()).isEqualTo("VA-ROLE-DEFAULT");
    }

    @Test
    @DisplayName("no explicit VA, no role → effectiveVaId is null")
    void nullEffectiveVaWhenNeitherSet() {
      var slotConfig = slot(FlowType.COLLECTION_COMPLETED, "source", null);
      when(flowSlotConfigRepository.findByFlowType(FlowType.COLLECTION_COMPLETED))
          .thenReturn(List.of(slotConfig));

      var result = service.getFlowConfig(FlowType.COLLECTION_COMPLETED);

      assertThat(result.slots().get(0).effectiveVaId()).isNull();
    }
  }
}
