package com.softspark.chaos.base;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ULID generation.
 */
class IdsTest {

    @Test
    @DisplayName("generate should return a valid ULID string")
    void testGenerate() {
        String id = Ids.generate();
        
        assertThat(id).isNotNull();
        assertThat(id).hasSize(26);
        assertThat(id).matches("[0-9A-Z]{26}");
    }

    @Test
    @DisplayName("generate should return unique IDs")
    void testGenerateUnique() {
        String id1 = Ids.generate();
        String id2 = Ids.generate();
        
        assertThat(id1).isNotEqualTo(id2);
    }
}
