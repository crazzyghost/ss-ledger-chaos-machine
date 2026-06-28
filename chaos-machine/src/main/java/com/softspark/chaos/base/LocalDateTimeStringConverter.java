package com.softspark.chaos.base;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDateTime;

/**
 * JPA converter storing a zoneless {@link LocalDateTime} as its ISO-8601 local string (TEXT),
 * mirroring {@link InstantStringConverter}. Used for ledger {@code balance_as_of} values, which the
 * ledger emits as a zoneless {@code LocalDateTime} (ADR-027).
 */
@Converter
public class LocalDateTimeStringConverter implements AttributeConverter<LocalDateTime, String> {

  @Override
  public String convertToDatabaseColumn(LocalDateTime attribute) {
    return attribute == null ? null : attribute.toString();
  }

  @Override
  public LocalDateTime convertToEntityAttribute(String dbData) {
    return dbData == null || dbData.isBlank() ? null : LocalDateTime.parse(dbData.trim());
  }
}
