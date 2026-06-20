package com.softspark.chaos.base;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/**
 * JPA attribute converter that persists a {@code List<String>} as a JSON array string in a TEXT
 * column.
 *
 * <p>The SQLite community dialect has no native JSON column type; this converter serialises the list
 * to a JSON string on write and parses it back on read, consistent with the existing
 * {@code payload_json} text-JSON precedent and {@link InstantStringConverter}.
 *
 * <p>Attribute converters are instantiated by Hibernate rather than Spring, so a private static
 * {@link ObjectMapper} is used instead of dependency injection. Jackson checked exceptions are
 * wrapped in {@link IllegalStateException} to surface serialisation faults without forcing checked
 * exceptions through the JPA layer.
 */
@Converter
public class JsonStringListConverter implements AttributeConverter<List<String>, String> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(List<String> attribute) {
    if (attribute == null || attribute.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(attribute);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialise string list to JSON", e);
    }
  }

  @Override
  public List<String> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return List.of();
    }
    try {
      return OBJECT_MAPPER.readValue(dbData, LIST_TYPE);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Failed to deserialise JSON to string list: " + dbData, e);
    }
  }
}
