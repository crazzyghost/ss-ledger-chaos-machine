package com.softspark.chaos.account.repository;

import com.softspark.chaos.account.model.FlowSlotConfig;
import com.softspark.chaos.flow.model.FlowType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for flow slot configuration entities.
 */
@Repository
public interface FlowSlotConfigRepository extends JpaRepository<FlowSlotConfig, String> {

  /**
   * Finds all flow slot configurations for a specific flow type.
   *
   * @param flowType the flow type
   * @return a list of flow slot configurations
   */
  List<FlowSlotConfig> findByFlowType(FlowType flowType);

  /**
   * Finds a flow slot configuration by flow type and slot name.
   *
   * @param flowType the flow type
   * @param slotName the slot name
   * @return an optional containing the flow slot configuration if found
   */
  Optional<FlowSlotConfig> findByFlowTypeAndSlotName(FlowType flowType, String slotName);
}
