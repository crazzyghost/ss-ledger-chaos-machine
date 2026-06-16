package com.softspark.chaos.base;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;

/**
 * JPA attribute converter that persists {@link Instant} values as ISO-8601 strings in TEXT columns.
 *
 * <p>The SQLite community dialect does not support native JDBC timestamp types; storing timestamps
 * as ISO-8601 strings ensures reliable round-trip serialisation without relying on SQLite JDBC's
 * {@code getTimestamp()} parser, which only accepts formatted date strings rather than epoch
 * milliseconds.
 */
@Converter
public class InstantStringConverter implements AttributeConverter<Instant, String> {

  @Override
  public String convertToDatabaseColumn(Instant attribute) {
    return attribute == null ? null : attribute.toString();
  }

  @Override
  public Instant convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    try {
      return Instant.parse(dbData);
    } catch (java.time.format.DateTimeParseException ignored) {
      // Rows written by earlier code stored epoch-millis as plain longs; parse those too.
      return Instant.ofEpochMilli(Long.parseLong(dbData));
    }
  }
}
