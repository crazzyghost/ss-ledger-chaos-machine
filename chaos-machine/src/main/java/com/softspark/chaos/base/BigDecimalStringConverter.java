package com.softspark.chaos.base;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

/**
 * JPA converter storing {@link BigDecimal} as its exact decimal string (TEXT), mirroring {@link
 * InstantStringConverter}. Avoids SQLite NUMERIC precision/dialect surprises and preserves the
 * ledger's exact decimal scale on disk (ADR-027).
 */
@Converter
public class BigDecimalStringConverter implements AttributeConverter<BigDecimal, String> {

  @Override
  public String convertToDatabaseColumn(BigDecimal attribute) {
    return attribute == null ? null : attribute.toPlainString();
  }

  @Override
  public BigDecimal convertToEntityAttribute(String dbData) {
    return dbData == null || dbData.isBlank() ? null : new BigDecimal(dbData.trim());
  }
}
