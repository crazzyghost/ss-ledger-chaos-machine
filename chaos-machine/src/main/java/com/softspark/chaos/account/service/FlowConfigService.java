package com.softspark.chaos.account.service;

import com.softspark.chaos.account.dto.FlowConfigResponse;
import com.softspark.chaos.account.dto.UpdateFlowConfigRequest;
import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.account.model.AccountRoleEntity;
import com.softspark.chaos.account.model.FlowSlotConfig;
import com.softspark.chaos.account.repository.AccountRoleRepository;
import com.softspark.chaos.account.repository.FlowSlotConfigRepository;
import com.softspark.chaos.account.repository.VirtualAccountRepository;
import com.softspark.chaos.base.Ids;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.flow.model.FlowType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing flow slot configurations.
 * <p>
 * Provides operations for viewing and editing which account fills each slot of each transaction flow.
 */
@Service
public class FlowConfigService {

    private static final Logger log = LoggerFactory.getLogger(FlowConfigService.class);

    private final FlowSlotConfigRepository flowSlotConfigRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final VirtualAccountRepository virtualAccountRepository;

    public FlowConfigService(
            FlowSlotConfigRepository flowSlotConfigRepository,
            AccountRoleRepository accountRoleRepository,
            VirtualAccountRepository virtualAccountRepository) {
        this.flowSlotConfigRepository = flowSlotConfigRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.virtualAccountRepository = virtualAccountRepository;
    }

    /**
     * Retrieves all flow configurations grouped by flow type.
     *
     * @return a list of all flow configurations
     */
    @Transactional(readOnly = true)
    public List<FlowConfigResponse> getAllFlowConfigs() {
        log.debug("Fetching all flow configurations");

        // Group configs by flow type
        Map<FlowType, List<FlowSlotConfig>> configsByFlow = flowSlotConfigRepository.findAll().stream()
                .collect(Collectors.groupingBy(FlowSlotConfig::getFlowType));

        return configsByFlow.entrySet().stream()
                .map(entry -> mapToResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Retrieves flow configuration for a specific flow type.
     *
     * @param flowType the flow type
     * @return the flow configuration
     */
    @Transactional(readOnly = true)
    public FlowConfigResponse getFlowConfig(FlowType flowType) {
        log.debug("Fetching flow configuration for: {}", flowType);
        var configs = flowSlotConfigRepository.findByFlowType(flowType);
        return mapToResponse(flowType, configs);
    }

    /**
     * Updates flow slot configuration for a specific flow type.
     *
     * @param flowType the flow type to update
     * @param request  the update request containing slot updates
     * @return the updated flow configuration
     * @throws BadRequestException if the specified role or VA does not exist
     */
    @Transactional
    public FlowConfigResponse updateFlowConfig(FlowType flowType, UpdateFlowConfigRequest request) {
        log.info("Updating flow configuration for: {}", flowType);

        for (var slotUpdate : request.slots()) {
            // Validate that at least one of accountRole or explicitVaId is provided
            if ((slotUpdate.accountRole() == null || slotUpdate.accountRole().isBlank()) &&
                    (slotUpdate.explicitVaId() == null || slotUpdate.explicitVaId().isBlank())) {
                throw new BadRequestException(
                        "Either accountRole or explicitVaId must be provided for slot: " + slotUpdate.slotName(),
                        null);
            }

            // Validate account role if provided
            AccountRole role = null;
            if (slotUpdate.accountRole() != null && !slotUpdate.accountRole().isBlank()) {
                try {
                    role = AccountRole.valueOf(slotUpdate.accountRole());
                    if (!accountRoleRepository.existsById(role)) {
                        throw new BadRequestException(
                                "Account role not found: " + slotUpdate.accountRole(),
                                null);
                    }
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException(
                            "Invalid account role: " + slotUpdate.accountRole(),
                            null);
                }
            }

            // Validate VA if provided
            if (slotUpdate.explicitVaId() != null && !slotUpdate.explicitVaId().isBlank()) {
                if (!virtualAccountRepository.existsById(slotUpdate.explicitVaId())) {
                    throw new BadRequestException(
                            "Virtual account not found: " + slotUpdate.explicitVaId(),
                            null);
                }
            }

            // Update or create the slot config
            var existingConfig = flowSlotConfigRepository
                    .findByFlowTypeAndSlotName(flowType, slotUpdate.slotName());

            if (existingConfig.isPresent()) {
                var config = existingConfig.get();
                config.setAccountRole(role);
                config.setExplicitVaId(slotUpdate.explicitVaId());
                flowSlotConfigRepository.save(config);
                log.debug("Updated flow slot config: {}.{}", flowType, slotUpdate.slotName());
            } else {
                var config = new FlowSlotConfig();
                config.setId(Ids.generate());
                config.setFlowType(flowType);
                config.setSlotName(slotUpdate.slotName());
                config.setAccountRole(role);
                config.setExplicitVaId(slotUpdate.explicitVaId());
                flowSlotConfigRepository.save(config);
                log.debug("Created flow slot config: {}.{}", flowType, slotUpdate.slotName());
            }
        }

        log.info("Updated flow configuration for {}", flowType);
        return getFlowConfig(flowType);
    }

    private FlowConfigResponse mapToResponse(FlowType flowType, List<FlowSlotConfig> configs) {
        var slots = configs.stream()
                .map(this::mapSlotToResponse)
                .toList();
        return new FlowConfigResponse(flowType, slots);
    }

    private FlowConfigResponse.SlotConfig mapSlotToResponse(FlowSlotConfig config) {
        String effectiveVaId = null;

        // Resolution precedence:
        // 1. Explicit VA ID if set
        if (config.getExplicitVaId() != null && !config.getExplicitVaId().isBlank()) {
            effectiveVaId = config.getExplicitVaId();
        }
        // 2. Role's default VA ID if role is set
        else if (config.getAccountRole() != null) {
            var role = accountRoleRepository.findById(config.getAccountRole());
            effectiveVaId = role.map(AccountRoleEntity::getDefaultVaId).orElse(null);
        }

        return new FlowConfigResponse.SlotConfig(
                config.getSlotName(),
                config.getAccountRole() != null ? config.getAccountRole().name() : null,
                config.getExplicitVaId(),
                effectiveVaId
        );
    }
}
