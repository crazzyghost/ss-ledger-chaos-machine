package com.softspark.chaos.batch.csv;

import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.FlowRequestBuilder;
import com.softspark.chaos.flow.builder.FlowCatalogProvider;
import com.softspark.chaos.flow.dto.FlowCatalogEntry;
import com.softspark.chaos.flow.model.FlowType;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Parses CSV files into {@link FlowRequest} instances for batch processing.
 *
 * <p>Validates that required column headers are present for the given {@link FlowType}. Per-row
 * parse errors are captured in {@link ParsedRow#error()} without aborting the entire parse; the
 * caller decides how to handle invalid rows.
 */
@Component
public class CsvFlowParser {

  private static final Logger log = LoggerFactory.getLogger(CsvFlowParser.class);

  private static final Set<String> AMOUNT_COLUMNS = Set.of("amount", "gross_amount", "net_amount");

  private final FlowCatalogProvider catalogProvider;

  public CsvFlowParser(FlowCatalogProvider catalogProvider) {
    this.catalogProvider = catalogProvider;
  }

  /**
   * Parses the given CSV input stream for the specified flow type.
   *
   * @param inputStream the CSV byte stream
   * @param flowType the flow type this CSV targets
   * @return ordered list of parsed rows; invalid rows have {@link ParsedRow#error()} set and a
   *     {@code null} flow request
   * @throws BadRequestException if the CSV header is missing required columns
   * @throws IOException if the stream cannot be read
   */
  public List<ParsedRow> parse(InputStream inputStream, FlowType flowType) throws IOException {
    FlowCatalogEntry catalogEntry = findCatalogEntry(flowType);
    List<String> requiredColumns = catalogEntry.csvColumns();

    try (var reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        CSVParser csvParser =
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build()
                .parse(reader)) {

      Set<String> headers =
          csvParser.getHeaderMap().keySet().stream()
              .map(String::toLowerCase)
              .collect(Collectors.toSet());

      // Validate that all required columns are present
      List<String> missing =
          requiredColumns.stream().filter(col -> !headers.contains(col.toLowerCase())).toList();
      if (!missing.isEmpty()) {
        throw new BadRequestException(
            "CSV is missing required columns for flow " + flowType + ": " + missing);
      }

      List<ParsedRow> results = new ArrayList<>();
      int rowNumber = 1;

      for (var csvRecord : csvParser) {
        if (isBlankRecord(csvRecord)) {
          rowNumber++;
          continue;
        }

        try {
          Map<String, Object> flowFields = new LinkedHashMap<>();
          Map<String, String> rowMap = new HashMap<>();

          for (Map.Entry<String, Integer> headerEntry : csvParser.getHeaderMap().entrySet()) {
            String colName = headerEntry.getKey().toLowerCase();
            String value = csvRecord.get(headerEntry.getKey());
            if (value != null && !value.isBlank()) {
              rowMap.put(colName, value);
            }
          }

          BigDecimal amount = parseBigDecimal(rowMap, "amount");
          BigDecimal grossAmount = parseBigDecimal(rowMap, "gross_amount");
          BigDecimal netAmount = parseBigDecimal(rowMap, "net_amount");
          String currency = rowMap.get("currency");

          // All non-amount fields go into flowFields
          for (Map.Entry<String, String> entry : rowMap.entrySet()) {
            if (!AMOUNT_COLUMNS.contains(entry.getKey()) && !"currency".equals(entry.getKey())) {
              flowFields.put(entry.getKey(), entry.getValue());
            }
          }

          var flowRequest =
              FlowRequestBuilder.builder()
                  .flowType(flowType)
                  .amount(amount)
                  .grossAmount(grossAmount)
                  .netAmount(netAmount)
                  .currency(currency)
                  .slotOverrides(Map.of())
                  .flowFields(flowFields)
                  .build();

          results.add(new ParsedRow(rowNumber, flowRequest, null));
        } catch (Exception e) {
          log.warn("Parse error at row {}: {}", rowNumber, e.getMessage());
          results.add(new ParsedRow(rowNumber, null, e.getMessage()));
        }

        rowNumber++;
      }

      return results;
    }
  }

  private boolean isBlankRecord(org.apache.commons.csv.CSVRecord record) {
    for (String value : record) {
      if (value != null && !value.isBlank()) {
        return false;
      }
    }
    return true;
  }

  private BigDecimal parseBigDecimal(Map<String, String> rowMap, String key) {
    String value = rowMap.get(key);
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException e) {
      throw new BadRequestException("Invalid numeric value for column '" + key + "': " + value);
    }
  }

  private FlowCatalogEntry findCatalogEntry(FlowType flowType) {
    return catalogProvider.catalog().stream()
        .filter(entry -> entry.flowType() == flowType)
        .findFirst()
        .orElseThrow(
            () -> new BadRequestException("No catalog entry found for flow type: " + flowType));
  }

  /**
   * A single parsed CSV row.
   *
   * @param rowNumber the one-based row number (not counting the header)
   * @param flowRequest the constructed flow request; {@code null} when parsing failed
   * @param error the parse error message; {@code null} on success
   */
  public record ParsedRow(int rowNumber, FlowRequest flowRequest, String error) {

    /**
     * Returns {@code true} if this row has a parse error.
     *
     * @return whether the row is invalid
     */
    public boolean isInvalid() {
      return error != null;
    }
  }
}
