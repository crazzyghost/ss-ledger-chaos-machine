package com.softspark.chaos.account.model;

import com.softspark.chaos.account.enumeration.AccountCategory;
import com.softspark.chaos.account.enumeration.AccountRole;
import com.softspark.chaos.account.enumeration.Channel;
import com.softspark.chaos.account.enumeration.ProvisioningStatus;
import com.softspark.chaos.base.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Account role entity representing system accounts in the chart of accounts.
 */
@Entity
@Table(name = "account_role")
public class AccountRoleEntity extends AuditableEntity {

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false)
  private AccountRole role;

  @Column(name = "account_code", nullable = false, unique = true)
  private String accountCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false)
  private AccountCategory category;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel")
  private Channel channel;

  @Column(name = "default_va_id")
  private String defaultVaId;

  @Enumerated(EnumType.STRING)
  @Column(name = "provisioning_status", nullable = false)
  private ProvisioningStatus provisioningStatus = ProvisioningStatus.PENDING;

  @Column(name = "provisioned_at")
  private String provisionedAt;

  @Column(name = "last_error", columnDefinition = "TEXT")
  private String lastError;

  public AccountRole getRole() {
    return role;
  }

  public void setRole(AccountRole role) {
    this.role = role;
  }

  public String getAccountCode() {
    return accountCode;
  }

  public void setAccountCode(String accountCode) {
    this.accountCode = accountCode;
  }

  public AccountCategory getCategory() {
    return category;
  }

  public void setCategory(AccountCategory category) {
    this.category = category;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Channel getChannel() {
    return channel;
  }

  public void setChannel(Channel channel) {
    this.channel = channel;
  }

  public String getDefaultVaId() {
    return defaultVaId;
  }

  public void setDefaultVaId(String defaultVaId) {
    this.defaultVaId = defaultVaId;
  }

  public ProvisioningStatus getProvisioningStatus() {
    return provisioningStatus;
  }

  public void setProvisioningStatus(ProvisioningStatus provisioningStatus) {
    this.provisioningStatus = provisioningStatus;
  }

  public String getProvisionedAt() {
    return provisionedAt;
  }

  public void setProvisionedAt(String provisionedAt) {
    this.provisionedAt = provisionedAt;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }
}
