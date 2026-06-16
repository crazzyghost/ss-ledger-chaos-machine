package com.softspark.chaos.flow.chaos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.softspark.chaos.kafka.EventEnvelope;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies field-level mutations to a serialized event envelope to produce intentionally malformed
 * payloads for chaos testing.
 *
 * <p>Supported mutation directives:
 *
 * <ul>
 *   <li>{@code dropField:<field>} — removes {@code data.<field>} from the JSON
 *   <li>{@code blankField:<field>} — sets {@code data.<field>} to {@code ""}
 *   <li>{@code negativeAmount} — negates {@code data.amount} or {@code data.gross_amount}
 *   <li>{@code invalidCurrency} — sets {@code data.currency} to {@code "INVALID"}
 *   <li>{@code truncateJson} — returns a JSON string truncated to half its length
 * </ul>
 */
class MalformedMutators {

  private static final Logger log = LoggerFactory.getLogger(MalformedMutators.class);

  private MalformedMutators() {}

  /**
   * Applies the given mutation directives to the serialized envelope and returns the mangled JSON.
   *
   * @param envelope the base event envelope
   * @param mutations list of mutation directives
   * @param mapper the Jackson object mapper
   * @return the mutated JSON string
   */
  static String apply(EventEnvelope<?> envelope, List<String> mutations, ObjectMapper mapper) {
    try {
      String json = mapper.writeValueAsString(envelope);
      JsonNode root = mapper.readTree(json);
      JsonNode dataNode = root.path("data");

      for (String mutation : mutations) {
        if (mutation.startsWith("dropField:")) {
          String field = mutation.substring("dropField:".length());
          if (dataNode instanceof ObjectNode objNode) {
            objNode.remove(field);
          }
        } else if (mutation.startsWith("blankField:")) {
          String field = mutation.substring("blankField:".length());
          if (dataNode instanceof ObjectNode objNode) {
            objNode.put(field, "");
          }
        } else if ("negativeAmount".equals(mutation)) {
          if (dataNode instanceof ObjectNode objNode) {
            applyNegation(objNode, "amount");
            applyNegation(objNode, "gross_amount");
          }
        } else if ("invalidCurrency".equals(mutation)) {
          if (dataNode instanceof ObjectNode objNode) {
            objNode.put("currency", "INVALID");
          }
        } else if ("truncateJson".equals(mutation)) {
          String partial = mapper.writeValueAsString(root);
          return partial.substring(0, partial.length() / 2);
        } else {
          log.warn("Unknown malformed mutation directive: {}", mutation);
        }
      }

      return mapper.writeValueAsString(root);
    } catch (Exception e) {
      log.error("Failed to apply malformed mutations: {}", e.getMessage(), e);
      return "{}";
    }
  }

  private static void applyNegation(ObjectNode node, String field) {
    JsonNode fieldNode = node.get(field);
    if (fieldNode != null && fieldNode.isNumber()) {
      node.put(field, fieldNode.decimalValue().negate());
    }
  }
}
