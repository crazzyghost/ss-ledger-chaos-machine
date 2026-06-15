package com.softspark.chaos.base;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for @RecordBuilder annotation processing.
 */
class RecordBuilderTest {

  @Test
  @DisplayName("RecordBuilder should generate a functional builder")
  void testRecordBuilder() {
    SampleRecord record = SampleRecordBuilder.builder().name("test").value(42).build();

    assertThat(record.name()).isEqualTo("test");
    assertThat(record.value()).isEqualTo(42);
  }
}
