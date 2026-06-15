package com.softspark.chaos.account.model;

import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.base.AuditableEntity;
import com.softspark.chaos.flow.model.FlowType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Flow slot configuration entity.
 */
@Entity
@Table(name = "flow_slot_config")
public class FlowSlotConfig extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false)
    private FlowType flowType;

    @Column(name = "slot_name", nullable = false)
    private String slotName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_role")
    private AccountRole accountRole;

    @Column(name = "explicit_va_id")
    private String explicitVaId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public FlowType getFlowType() {
        return flowType;
    }

    public void setFlowType(FlowType flowType) {
        this.flowType = flowType;
    }

    public String getSlotName() {
        return slotName;
    }

    public void setSlotName(String slotName) {
        this.slotName = slotName;
    }

    public AccountRole getAccountRole() {
        return accountRole;
    }

    public void setAccountRole(AccountRole accountRole) {
        this.accountRole = accountRole;
    }

    public String getExplicitVaId() {
        return explicitVaId;
    }

    public void setExplicitVaId(String explicitVaId) {
        this.explicitVaId = explicitVaId;
    }
}
