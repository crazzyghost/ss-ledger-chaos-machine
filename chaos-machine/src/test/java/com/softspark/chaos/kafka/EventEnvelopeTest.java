package com.softspark.chaos.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softspark.chaos.base.Ids;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for EventEnvelope serialization.
 */
@SpringBootTest
@ActiveProfiles("test")
class EventEnvelopeTest {

    @Autowired
    private ObjectMapper kafkaObjectMapper;

    private EventEnvelope<Map<String, String>> sampleEnvelope;

    @BeforeEach
    void setUp() {
        EventMetadata metadata = new EventMetadata(
                Ids.generate(),
                Ids.generate(),
                "tenant-123"
        );

        Map<String, String> data = Map.of("organization_id", "org-456");

        sampleEnvelope = new EventEnvelope<>(
                Ids.generate(),
                "organization.onboarded",
                Instant.parse("2026-05-24T10:00:00Z"),
                "chaos-machine",
                "1.0",
                data,
                metadata
        );
    }

    @Test
    @DisplayName("EventEnvelope should serialize with snake_case field names")
    void testSerializationSnakeCase() throws Exception {
        String json = kafkaObjectMapper.writeValueAsString(sampleEnvelope);
        
        assertThat(json).contains("\"event_id\":");
        assertThat(json).contains("\"event_type\":");
        assertThat(json).contains("\"correlation_id\":");
        assertThat(json).contains("\"idempotency_key\":");
        assertThat(json).contains("\"tenant_id\":");
        assertThat(json).doesNotContain("eventId");
        assertThat(json).doesNotContain("eventType");
    }

    @Test
    @DisplayName("EventEnvelope should serialize timestamp in ISO-8601 format")
    void testTimestampFormat() throws Exception {
        String json = kafkaObjectMapper.writeValueAsString(sampleEnvelope);
        
        assertThat(json).contains("\"timestamp\":\"2026-05-24T10:00:00Z\"");
    }

    @Test
    @DisplayName("EventEnvelope should round-trip correctly")
    void testRoundTrip() throws Exception {
        String json = kafkaObjectMapper.writeValueAsString(sampleEnvelope);
        EventEnvelope<?> deserialized = kafkaObjectMapper.readValue(json, EventEnvelope.class);
        
        assertThat(deserialized.eventId()).isEqualTo(sampleEnvelope.eventId());
        assertThat(deserialized.eventType()).isEqualTo(sampleEnvelope.eventType());
        assertThat(deserialized.timestamp()).isEqualTo(sampleEnvelope.timestamp());
        assertThat(deserialized.source()).isEqualTo(sampleEnvelope.source());
        assertThat(deserialized.version()).isEqualTo(sampleEnvelope.version());
    }
}
