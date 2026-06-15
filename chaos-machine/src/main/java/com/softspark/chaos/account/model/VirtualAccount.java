package com.softspark.chaos.account.model;

import com.softspark.chaos.account.enumeration.AccountOwnershipType;
import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.account.enumeration.AccountStatus;
import com.softspark.chaos.account.enumeration.Channel;
import com.softspark.chaos.account.enumeration.CreatedVia;
import com.softspark.chaos.base.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Virtual account entity.
 */
@Entity
@Table(name = "virtual_account")
public class VirtualAccount extends AuditableEntity {

    @Id
    @Column(name = "va_id", nullable = false)
    private String vaId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "ownership_type", nullable = false)
    private AccountOwnershipType ownershipType;

    @Column(name = "organization_id")
    private String organizationId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel")
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_role")
    private AccountRole accountRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_via", nullable = false)
    private CreatedVia createdVia;

    public String getVaId() {
        return vaId;
    }

    public void setVaId(String vaId) {
        this.vaId = vaId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AccountOwnershipType getOwnershipType() {
        return ownershipType;
    }

    public void setOwnershipType(AccountOwnershipType ownershipType) {
        this.ownershipType = ownershipType;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public AccountRole getAccountRole() {
        return accountRole;
    }

    public void setAccountRole(AccountRole accountRole) {
        this.accountRole = accountRole;
    }

    public CreatedVia getCreatedVia() {
        return createdVia;
    }

    public void setCreatedVia(CreatedVia createdVia) {
        this.createdVia = createdVia;
    }
}
