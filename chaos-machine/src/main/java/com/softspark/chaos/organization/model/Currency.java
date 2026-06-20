package com.softspark.chaos.organization.model;

import com.softspark.chaos.base.AuditableEntity;
import com.softspark.chaos.organization.enumeration.CurrencyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Currency reference-data entity.
 *
 * <p>The {@code code} column is the ISO-4217 natural key the ledger consumes; {@code currencyId} is
 * the internal UUID v4 foreign-key target. Identifiers are server-assigned.
 */
@Entity
@Table(name = "currency")
public class Currency extends AuditableEntity {

  @Id
  @Column(name = "currency_id", nullable = false)
  private String currencyId;

  @Column(name = "code", nullable = false, unique = true)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "symbol")
  private String symbol;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private CurrencyStatus status;

  public String getCurrencyId() {
    return currencyId;
  }

  public void setCurrencyId(String currencyId) {
    this.currencyId = currencyId;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public CurrencyStatus getStatus() {
    return status;
  }

  public void setStatus(CurrencyStatus status) {
    this.status = status;
  }
}
