package com.softspark.chaos.flow.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.account.model.AccountRoleEntity;
import com.softspark.chaos.account.model.FlowSlotConfig;
import com.softspark.chaos.account.repository.AccountRoleRepository;
import com.softspark.chaos.account.repository.FlowSlotConfigRepository;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.FlowRequestBuilder;
import com.softspark.chaos.flow.model.FlowType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link SlotResolver}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SlotResolver")
class SlotResolverTest {

  @Mock private FlowSlotConfigRepository flowSlotConfigRepository;
  @Mock private AccountRoleRepository accountRoleRepository;

  @InjectMocks private SlotResolver slotResolver;

  private FlowSlotConfig slot(String slotName, AccountRole role, String explicitVaId) {
    var config = new FlowSlotConfig();
    config.setId("id-" + slotName);
    config.setFlowType(FlowType.COLLECTION_COMPLETED);
    config.setSlotName(slotName);
    config.setAccountRole(role);
    config.setExplicitVaId(explicitVaId);
    return config;
  }

  private FlowRequest requestWithOverride(String slotName, String vaId) {
    return FlowRequestBuilder.builder()
        .flowType(FlowType.COLLECTION_COMPLETED)
        .slotOverrides(Map.of(slotName, vaId))
        .flowFields(Map.of())
        .build();
  }

  private FlowRequest emptyRequest() {
    return FlowRequestBuilder.builder()
        .flowType(FlowType.COLLECTION_COMPLETED)
        .slotOverrides(Map.of())
        .flowFields(Map.of())
        .build();
  }

  @Nested
  @DisplayName("resolution precedence")
  class ResolutionPrecedence {

    @Test
    @DisplayName("request override takes highest precedence")
    void requestOverrideTakesPrecedence() {
      var config = slot("source", AccountRole.PLATFORM_FLOAT, "EXPLICIT-VA");
      when(flowSlotConfigRepository.findByFlowType(FlowType.COLLECTION_COMPLETED))
          .thenReturn(List.of(config));

      var request = requestWithOverride("source", "OVERRIDE-VA");
      var resolved = slotResolver.resolveAll(FlowType.COLLECTION_COMPLETED, request);

      assertThat(resolved).containsEntry("source", "OVERRIDE-VA");
    }

    @Test
    @DisplayName("explicit VA config used when no request override")
    void explicitVaConfigUsed() {
      var config = slot("source", AccountRole.PLATFORM_FLOAT, "EXPLICIT-VA");
      when(flowSlotConfigRepository.findByFlowType(FlowType.COLLECTION_COMPLETED))
          .thenReturn(List.of(config));

      var resolved = slotResolver.resolveAll(FlowType.COLLECTION_COMPLETED, emptyRequest());

      assertThat(resolved).containsEntry("source", "EXPLICIT-VA");
    }

    @Test
    @DisplayName("role default VA used when no override or explicit VA")
    void roleDefaultVaUsed() {
      var config = slot("source", AccountRole.PLATFORM_FLOAT, null);
      when(flowSlotConfigRepository.findByFlowType(FlowType.COLLECTION_COMPLETED))
          .thenReturn(List.of(config));

      var roleEntity = new AccountRoleEntity();
      roleEntity.setRole(AccountRole.PLATFORM_FLOAT);
      roleEntity.setDefaultVaId("ROLE-DEFAULT-VA");
      when(accountRoleRepository.findById(AccountRole.PLATFORM_FLOAT))
          .thenReturn(Optional.of(roleEntity));

      var resolved = slotResolver.resolveAll(FlowType.COLLECTION_COMPLETED, emptyRequest());

      assertThat(resolved).containsEntry("source", "ROLE-DEFAULT-VA");
    }

    @Test
    @DisplayName("throws BadRequestException when role has no default VA")
    void throwsWhenRoleHasNoDefaultVa() {
      var config = slot("source", AccountRole.PLATFORM_FLOAT, null);
      when(flowSlotConfigRepository.findByFlowType(FlowType.COLLECTION_COMPLETED))
          .thenReturn(List.of(config));

      var roleEntity = new AccountRoleEntity();
      roleEntity.setRole(AccountRole.PLATFORM_FLOAT);
      roleEntity.setDefaultVaId(null);
      when(accountRoleRepository.findById(AccountRole.PLATFORM_FLOAT))
          .thenReturn(Optional.of(roleEntity));

      assertThatThrownBy(
              () -> slotResolver.resolveAll(FlowType.COLLECTION_COMPLETED, emptyRequest()))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("Unresolved required slot: source");
    }
  }

  @Nested
  @DisplayName("channel-aware resolution")
  class ChannelAwareResolution {

    @Test
    @DisplayName("MTN channel redirects PLATFORM_FLOAT to PLATFORM_FLOAT_MTN")
    void mtnChannelRedirects() {
      var config = slot("destination", AccountRole.PLATFORM_FLOAT, null);
      config.setFlowType(FlowType.TOPUP_CONFIRMED);
      when(flowSlotConfigRepository.findByFlowType(FlowType.TOPUP_CONFIRMED))
          .thenReturn(List.of(config));

      var mtnRoleEntity = new AccountRoleEntity();
      mtnRoleEntity.setRole(AccountRole.PLATFORM_FLOAT_MTN);
      mtnRoleEntity.setDefaultVaId("MTN-FLOAT-VA");
      when(accountRoleRepository.findById(AccountRole.PLATFORM_FLOAT_MTN))
          .thenReturn(Optional.of(mtnRoleEntity));

      var request =
          FlowRequestBuilder.builder()
              .flowType(FlowType.TOPUP_CONFIRMED)
              .channel("MTN")
              .slotOverrides(Map.of())
              .flowFields(Map.of())
              .build();

      var resolved = slotResolver.resolveAll(FlowType.TOPUP_CONFIRMED, request);

      assertThat(resolved).containsEntry("destination", "MTN-FLOAT-VA");
    }

    @Test
    @DisplayName("TELECEL channel redirects PLATFORM_FLOAT to PLATFORM_FLOAT_TELECEL")
    void telecelChannelRedirects() {
      var config = slot("destination", AccountRole.PLATFORM_FLOAT, null);
      config.setFlowType(FlowType.TOPUP_CONFIRMED);
      when(flowSlotConfigRepository.findByFlowType(FlowType.TOPUP_CONFIRMED))
          .thenReturn(List.of(config));

      var telecelRoleEntity = new AccountRoleEntity();
      telecelRoleEntity.setRole(AccountRole.PLATFORM_FLOAT_TELECEL);
      telecelRoleEntity.setDefaultVaId("TELECEL-FLOAT-VA");
      when(accountRoleRepository.findById(AccountRole.PLATFORM_FLOAT_TELECEL))
          .thenReturn(Optional.of(telecelRoleEntity));

      var request =
          FlowRequestBuilder.builder()
              .flowType(FlowType.TOPUP_CONFIRMED)
              .channel("TELECEL")
              .slotOverrides(Map.of())
              .flowFields(Map.of())
              .build();

      var resolved = slotResolver.resolveAll(FlowType.TOPUP_CONFIRMED, request);

      assertThat(resolved).containsEntry("destination", "TELECEL-FLOAT-VA");
    }

    @Test
    @DisplayName("no channel retains PLATFORM_FLOAT")
    void noChannelRetainsPlatformFloat() {
      var config = slot("destination", AccountRole.PLATFORM_FLOAT, null);
      config.setFlowType(FlowType.TOPUP_CONFIRMED);
      when(flowSlotConfigRepository.findByFlowType(FlowType.TOPUP_CONFIRMED))
          .thenReturn(List.of(config));

      var roleEntity = new AccountRoleEntity();
      roleEntity.setRole(AccountRole.PLATFORM_FLOAT);
      roleEntity.setDefaultVaId("FLOAT-VA");
      when(accountRoleRepository.findById(AccountRole.PLATFORM_FLOAT))
          .thenReturn(Optional.of(roleEntity));

      var request =
          FlowRequestBuilder.builder()
              .flowType(FlowType.TOPUP_CONFIRMED)
              .slotOverrides(Map.of())
              .flowFields(Map.of())
              .build();

      var resolved = slotResolver.resolveAll(FlowType.TOPUP_CONFIRMED, request);

      assertThat(resolved).containsEntry("destination", "FLOAT-VA");
    }
  }

  @Nested
  @DisplayName("flows without slots")
  class FlowsWithoutSlots {

    @Test
    @DisplayName("returns empty map for flows with no slot config")
    void returnsEmptyMapForFlowsWithNoSlots() {
      when(flowSlotConfigRepository.findByFlowType(FlowType.ORGANIZATION_ONBOARDED))
          .thenReturn(List.of());

      var request =
          FlowRequestBuilder.builder()
              .flowType(FlowType.ORGANIZATION_ONBOARDED)
              .slotOverrides(Map.of())
              .flowFields(Map.of())
              .build();

      var resolved = slotResolver.resolveAll(FlowType.ORGANIZATION_ONBOARDED, request);

      assertThat(resolved).isEmpty();
    }
  }
}
