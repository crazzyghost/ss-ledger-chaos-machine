package com.softspark.chaos.base;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JsonStringListConverter}.
 */
@DisplayName("JsonStringListConverter")
class JsonStringListConverterTest {

  private final JsonStringListConverter converter = new JsonStringListConverter();

  // ── convertToDatabaseColumn ──────────────────────────────────────────────────

  @Nested
  @DisplayName("convertToDatabaseColumn")
  class ToDatabaseColumnTests {

    @Test
    @DisplayName("null entity value maps to null DB value")
    void nullMapsToNull() {
      assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    @DisplayName("empty list maps to null DB value")
    void emptyListMapsToNull() {
      assertThat(converter.convertToDatabaseColumn(List.of())).isNull();
    }

    @Test
    @DisplayName("single element serialises to a JSON array")
    void singleElementSerialises() {
      assertThat(converter.convertToDatabaseColumn(List.of("+233200000000")))
          .isEqualTo("[\"+233200000000\"]");
    }
  }

  // ── convertToEntityAttribute ─────────────────────────────────────────────────

  @Nested
  @DisplayName("convertToEntityAttribute")
  class ToEntityAttributeTests {

    @Test
    @DisplayName("null DB value maps to empty list")
    void nullMapsToEmptyList() {
      assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    @DisplayName("blank DB value maps to empty list")
    void blankMapsToEmptyList() {
      assertThat(converter.convertToEntityAttribute("   ")).isEmpty();
    }

    @Test
    @DisplayName("JSON array deserialises to a list")
    void jsonArrayDeserialises() {
      assertThat(converter.convertToEntityAttribute("[\"a\",\"b\"]")).containsExactly("a", "b");
    }
  }

  // ── round-trip ───────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("round-trip")
  class RoundTripTests {

    @Test
    @DisplayName("single element round-trips")
    void singleRoundTrips() {
      var input = List.of("one");
      var db = converter.convertToDatabaseColumn(input);
      assertThat(converter.convertToEntityAttribute(db)).isEqualTo(input);
    }

    @Test
    @DisplayName("multiple elements round-trip in order")
    void multipleRoundTrip() {
      var input = List.of("one", "two", "three");
      var db = converter.convertToDatabaseColumn(input);
      assertThat(converter.convertToEntityAttribute(db)).isEqualTo(input);
    }

    @Test
    @DisplayName("empty list round-trips to empty list via null")
    void emptyRoundTrips() {
      var db = converter.convertToDatabaseColumn(List.of());
      assertThat(converter.convertToEntityAttribute(db)).isEmpty();
    }
  }
}
