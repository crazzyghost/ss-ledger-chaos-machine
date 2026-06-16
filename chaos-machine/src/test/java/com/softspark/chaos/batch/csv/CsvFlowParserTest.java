package com.softspark.chaos.batch.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.flow.builder.FlowCatalogProvider;
import com.softspark.chaos.flow.model.FlowType;
import com.softspark.chaos.kafka.TopicCatalog;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CsvFlowParser}.
 */
@DisplayName("CsvFlowParser")
class CsvFlowParserTest {

  private CsvFlowParser csvFlowParser;

  @BeforeEach
  void setUp() {
    // Use a real TopicCatalog with default topic names to avoid 12-type stub setup
    var topicCatalog = new TopicCatalog();
    var catalogProvider = new FlowCatalogProvider(topicCatalog);
    csvFlowParser = new CsvFlowParser(catalogProvider);
  }

  private ByteArrayInputStream csv(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }

  @Nested
  @DisplayName("valid CSV")
  class ValidCsv {

    @Test
    @DisplayName("parses rows into FlowRequests with flowFields populated")
    void parsesRowsWithFlowFields() throws IOException {
      String content =
          "collection_request_id,gross_amount,net_amount,currency,merchant_reference,"
              + "provider_collection_id\n"
              + "COL-001,110.00,100.00,GHS,MERCH-REF-1,PROV-001\n"
              + "COL-002,220.00,200.00,GHS,MERCH-REF-2,PROV-002\n";

      var rows = csvFlowParser.parse(csv(content), FlowType.COLLECTION_COMPLETED);

      assertThat(rows).hasSize(2);
      assertThat(rows).allMatch(r -> !r.isInvalid());

      var row1 = rows.get(0);
      assertThat(row1.rowNumber()).isEqualTo(1);
      assertThat(row1.flowRequest().flowFields()).containsEntry("collection_request_id", "COL-001");
      assertThat(row1.flowRequest().currency()).isEqualTo("GHS");
      assertThat(row1.flowRequest().grossAmount().toPlainString()).isEqualTo("110.00");
      assertThat(row1.flowRequest().netAmount().toPlainString()).isEqualTo("100.00");
    }

    @Test
    @DisplayName("skips blank lines")
    void skipsBlankLines() throws IOException {
      String content =
          "collection_request_id,gross_amount,net_amount,currency,merchant_reference,"
              + "provider_collection_id\n"
              + "COL-001,110.00,100.00,GHS,MERCH-REF-1,PROV-001\n"
              + "\n"
              + "COL-002,220.00,200.00,GHS,MERCH-REF-2,PROV-002\n";

      var rows = csvFlowParser.parse(csv(content), FlowType.COLLECTION_COMPLETED);

      assertThat(rows).hasSize(2);
    }
  }

  @Nested
  @DisplayName("invalid CSV")
  class InvalidCsv {

    @Test
    @DisplayName("throws BadRequestException when required columns are missing")
    void throwsWhenRequiredColumnsMissing() {
      String content = "gross_amount,net_amount,currency\n" + "110.00,100.00,GHS\n";

      assertThatThrownBy(() -> csvFlowParser.parse(csv(content), FlowType.COLLECTION_COMPLETED))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("missing required columns");
    }

    @Test
    @DisplayName("captures per-row parse error without aborting parsing")
    void capturesPerRowError() throws IOException {
      String content =
          "collection_request_id,gross_amount,net_amount,currency,merchant_reference,"
              + "provider_collection_id\n"
              + "COL-001,not-a-number,100.00,GHS,MERCH-REF-1,PROV-001\n"
              + "COL-002,220.00,200.00,GHS,MERCH-REF-2,PROV-002\n";

      var rows = csvFlowParser.parse(csv(content), FlowType.COLLECTION_COMPLETED);

      assertThat(rows).hasSize(2);

      var row1 = rows.get(0);
      assertThat(row1.isInvalid()).isTrue();
      assertThat(row1.error()).isNotBlank();
      assertThat(row1.flowRequest()).isNull();

      var row2 = rows.get(1);
      assertThat(row2.isInvalid()).isFalse();
      assertThat(row2.flowRequest()).isNotNull();
    }
  }
}
