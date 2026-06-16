package com.softspark.chaos.flow.resolver;

import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.account.model.AccountRoleEntity;
import com.softspark.chaos.account.repository.AccountRoleRepository;
import com.softspark.chaos.account.repository.FlowSlotConfigRepository;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.model.FlowType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Resolves the VA id for each configured slot in a transaction flow.
 *
 * <p>Resolution precedence (highest to lowest):
 *
 * <ol>
 *   <li>{@link FlowRequest#slotOverrides()} — explicit per-request VA id override
 *   <li>{@code FlowSlotConfig.explicitVaId} — static explicit VA configured in the database
 *   <li>{@code FlowSlotConfig.accountRole} → {@link AccountRoleEntity#getDefaultVaId()} — the
 *       default VA for the assigned system account role
 * </ol>
 *
 * <p>Channel-aware resolution: when {@link FlowRequest#channel()} is {@code "MTN"} and the
 * configured role is {@link AccountRole#PLATFORM_FLOAT}, the resolver redirects to {@link
 * AccountRole#PLATFORM_FLOAT_MTN}. Similarly, {@code "TELECEL"} redirects to {@link
 * AccountRole#PLATFORM_FLOAT_TELECEL}.
 */
@Component
public class SlotResolver {

  private static final String CHANNEL_MTN = "MTN";
  private static final String CHANNEL_TELECEL = "TELECEL";

  private final FlowSlotConfigRepository flowSlotConfigRepository;
  private final AccountRoleRepository accountRoleRepository;

  public SlotResolver(
      FlowSlotConfigRepository flowSlotConfigRepository,
      AccountRoleRepository accountRoleRepository) {
    this.flowSlotConfigRepository = flowSlotConfigRepository;
    this.accountRoleRepository = accountRoleRepository;
  }

  /**
   * Resolves all configured slots for the given flow type and request.
   *
   * <p>Flows without any slot configuration return an empty map without error.
   *
   * @param flowType the flow type whose slots to resolve
   * @param request the originating flow request
   * @return slot-name-to-VA-id mapping for all configured slots
   * @throws BadRequestException if a slot has a configuration but the VA id cannot be resolved
   */
  public Map<String, String> resolveAll(FlowType flowType, FlowRequest request) {
    var configs = flowSlotConfigRepository.findByFlowType(flowType);
    Map<String, String> resolved = new LinkedHashMap<>();

    for (var config : configs) {
      String slotName = config.getSlotName();
      String vaId =
          resolve(flowType, slotName, config.getAccountRole(), config.getExplicitVaId(), request);
      if (vaId != null) {
        resolved.put(slotName, vaId);
      }
    }

    return Map.copyOf(resolved);
  }

  /**
   * Resolves a single slot VA id using the precedence rules described in the class Javadoc.
   *
   * @param flowType the flow type (for error messages)
   * @param slotName the slot name
   * @param configuredRole the role assigned to this slot in the database (may be null)
   * @param explicitVaId the static explicit VA id in the database (may be null)
   * @param request the originating flow request
   * @return the resolved VA id, or {@code null} if neither an explicit VA nor a role default is
   *     available
   * @throws BadRequestException if the slot is required but the VA id cannot be resolved
   */
  @Nullable
  private String resolve(
      FlowType flowType,
      String slotName,
      @Nullable AccountRole configuredRole,
      @Nullable String explicitVaId,
      FlowRequest request) {

    // 1. Request-level override
    String override = request.slotOverrides().get(slotName);
    if (override != null && !override.isBlank()) {
      return override;
    }

    // 2. Static explicit VA id from the slot configuration
    if (explicitVaId != null && !explicitVaId.isBlank()) {
      return explicitVaId;
    }

    // 3. Role default VA id (with channel-aware redirect)
    if (configuredRole != null) {
      AccountRole effectiveRole = channelAwareRole(configuredRole, request.channel());
      return accountRoleRepository
          .findById(effectiveRole)
          .map(AccountRoleEntity::getDefaultVaId)
          .orElseThrow(
              () ->
                  new BadRequestException(
                      "Unresolved required slot: "
                          + slotName
                          + " for flow: "
                          + flowType
                          + " (role "
                          + effectiveRole
                          + " has no default VA id)"));
    }

    return null;
  }

  /**
   * Applies channel-aware role redirection for {@link AccountRole#PLATFORM_FLOAT}.
   *
   * @param role the configured role
   * @param channel the channel from the request (may be null)
   * @return the effective role to use for resolution
   */
  private AccountRole channelAwareRole(AccountRole role, @Nullable String channel) {
    if (role != AccountRole.PLATFORM_FLOAT || channel == null) {
      return role;
    }
    return switch (channel.toUpperCase()) {
      case CHANNEL_MTN -> AccountRole.PLATFORM_FLOAT_MTN;
      case CHANNEL_TELECEL -> AccountRole.PLATFORM_FLOAT_TELECEL;
      default -> role;
    };
  }
}
