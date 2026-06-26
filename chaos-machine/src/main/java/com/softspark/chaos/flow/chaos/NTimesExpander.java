package com.softspark.chaos.flow.chaos;

import com.softspark.chaos.base.Ids;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.flow.FlowRequest;
import com.softspark.chaos.flow.FlowRequestBuilder;
import com.softspark.chaos.flow.builder.FlowCatalogProvider;
import com.softspark.chaos.flow.dto.AutogenRule;
import com.softspark.chaos.flow.dto.FlowFieldDescriptor;
import com.softspark.chaos.flow.model.FlowType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * Fans one {@link FlowRequest} carrying {@link NTimesOptions} into N per-iteration
 * {@link FlowRequest}s which, when each is executed by the flow engine, yield N genuinely-distinct
 * transactions against the <em>same</em> source/destination accounts.
 *
 * <p>Distinctness is produced by re-rolling, per iteration, every {@code flowFields} key whose
 * catalog descriptor marks an autogen rule — {@link AutogenRule#UUID_V4} <em>and</em>
 * {@link AutogenRule#ULID} — using the matching generator. This covers the business
 * {@code *_request_id}/{@code transaction_id} <em>and</em> every other ledger reference field the
 * ledger may key its journal on (e.g. {@code provider_reference_id}, {@code merchant_ref_id});
 * re-rolling only the UUID id would leave a constant ULID reference and the ledger would dedupe the
 * journal entries. It falls back to any {@code *_request_id}-suffixed key when no descriptor marks an
 * autogen rule. The engine independently derives a fresh event id (and thus a fresh
 * {@code "<event-type>:<eventId>"} idempotency key) per call, so the expander deliberately does
 * <strong>not</strong> set event ids or idempotency keys.
 *
 * <p>Slots (VA ids), amounts, currency, channel, organization ids and every other field are held
 * constant — that is what makes the N transactions "the same transfer". All N share one correlation
 * id (the caller's override if present, else a single generated id) for grouping.
 *
 * <p>The expander is pure (no publishing, no persistence) so both the synchronous and asynchronous
 * execution paths reuse it. Callers must {@link #validate(NTimesOptions)} before expanding.
 */
@Component
public class NTimesExpander {

  /** Convention fallback when a flow marks no {@code autogen} business-id field. */
  private static final String REQUEST_ID_SUFFIX = "_request_id";

  private final FlowCatalogProvider catalogProvider;
  private final ChaosLimits limits;

  public NTimesExpander(FlowCatalogProvider catalogProvider, ChaosLimits limits) {
    this.catalogProvider = catalogProvider;
    this.limits = limits;
  }

  /**
   * Validates an {@link NTimesOptions} against {@link ChaosLimits}, throwing
   * {@link BadRequestException} (→ 400) on any cap or shape violation.
   *
   * @param options the N-Times options to validate
   */
  public void validate(NTimesOptions options) {
    if (options.pacing() == null) {
      throw new BadRequestException("N-Times pacing is required (BURST | LINEAR | RANDOM)");
    }
    if (options.count() < 1) {
      throw new BadRequestException("N-Times count must be at least 1");
    }
    if (options.count() > limits.maxNTimes()) {
      throw new BadRequestException(
          "N-Times count " + options.count() + " exceeds limit of " + limits.maxNTimes());
    }

    switch (options.pacing()) {
      case BURST -> {
        // no inter-event gap parameters required
      }
      case LINEAR -> {
        Long fixed = options.fixedDelayMs();
        if (fixed == null) {
          throw new BadRequestException("LINEAR pacing requires fixedDelayMs");
        }
        if (fixed < 0) {
          throw new BadRequestException("LINEAR fixedDelayMs must not be negative");
        }
        if (fixed > limits.maxDelayMs()) {
          throw new BadRequestException(
              "LINEAR fixedDelayMs " + fixed + "ms exceeds limit of " + limits.maxDelayMs() + "ms");
        }
      }
      case RANDOM -> {
        Long min = options.minDelayMs();
        Long max = options.maxDelayMs();
        if (min == null || max == null) {
          throw new BadRequestException("RANDOM pacing requires minDelayMs and maxDelayMs");
        }
        if (min < 0) {
          throw new BadRequestException("RANDOM minDelayMs must not be negative");
        }
        if (min > max) {
          throw new BadRequestException(
              "RANDOM minDelayMs " + min + " must not exceed maxDelayMs " + max);
        }
        if (max > limits.maxDelayMs()) {
          throw new BadRequestException(
              "RANDOM maxDelayMs " + max + "ms exceeds limit of " + limits.maxDelayMs() + "ms");
        }
      }
    }
  }

  /**
   * Expands a validated base request into exactly {@code count} per-iteration requests.
   *
   * <p>Each returned request has every {@code autogen = UUID_V4} field re-rolled to a fresh UUID,
   * carries the shared correlation id, and has its {@code chaos} stripped to {@code null} so the
   * engine runs the normal single-publish path (no recursion).
   *
   * @param base the originating request; its {@code chaos.nTimes()} must be non-null and validated
   * @return an ordered list of exactly {@code count} distinct requests
   */
  public List<FlowRequest> expand(FlowRequest base) {
    NTimesOptions options = base.chaos().nTimes();
    Map<String, AutogenRule> requestIdFields = businessIdFields(base);
    String correlationId = base.correlationId() != null ? base.correlationId() : Ids.generate();

    int count = options.count();
    List<FlowRequest> requests = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      Map<String, Object> flowFields = new LinkedHashMap<>(base.flowFields());
      requestIdFields.forEach((field, rule) -> flowFields.put(field, mint(rule)));
      requests.add(
          FlowRequestBuilder.builder()
              .flowType(base.flowType())
              .correlationId(correlationId)
              .tenantId(base.tenantId())
              .channel(base.channel())
              .amount(base.amount())
              .grossAmount(base.grossAmount())
              .netAmount(base.netAmount())
              .currency(base.currency())
              .slotOverrides(base.slotOverrides())
              .chaos(null)
              .flowFields(flowFields)
              .fees(base.fees())
              .build());
    }
    return requests;
  }

  /**
   * Returns the inter-event delay <em>before</em> iteration {@code index} (zero-based). The first
   * iteration ({@code index == 0}) has no preceding gap.
   *
   * @param options the N-Times options
   * @param index the zero-based iteration index
   * @return {@link Duration#ZERO} for {@code BURST} or the first iteration; the fixed gap for
   *     {@code LINEAR}; a value in {@code [minDelayMs, maxDelayMs]} for {@code RANDOM}
   */
  public Duration delayFor(NTimesOptions options, int index) {
    if (index <= 0 || options.pacing() == Pacing.BURST) {
      return Duration.ZERO;
    }
    return switch (options.pacing()) {
      case BURST -> Duration.ZERO;
      case LINEAR -> Duration.ofMillis(options.fixedDelayMs());
      case RANDOM -> Duration.ofMillis(randomGap(options.minDelayMs(), options.maxDelayMs()));
    };
  }

  private long randomGap(long min, long max) {
    if (min >= max) {
      return min;
    }
    return ThreadLocalRandom.current().nextLong(min, max + 1);
  }

  /** Mints a fresh value for an autogen rule: a ULID for {@code ULID}, else a UUID. */
  private static String mint(AutogenRule rule) {
    return rule == AutogenRule.ULID ? Ids.generateULID() : Ids.generateUUID();
  }

  /**
   * Resolves the {@code flowFields} keys to re-roll for distinctness (mapped to their autogen rule):
   * the catalog's {@code autogen != NONE} descriptors ({@code UUID_V4} and {@code ULID}), or the
   * {@code *_request_id}-suffix convention (as {@code UUID_V4}) when the flow marks none.
   */
  private Map<String, AutogenRule> businessIdFields(FlowRequest base) {
    Map<String, AutogenRule> autogen = autogenFieldNames(base.flowType());
    if (!autogen.isEmpty()) {
      return autogen;
    }
    Map<String, AutogenRule> fallback = new LinkedHashMap<>();
    for (String key : base.flowFields().keySet()) {
      if (key.endsWith(REQUEST_ID_SUFFIX)) {
        fallback.put(key, AutogenRule.UUID_V4);
      }
    }
    return fallback;
  }

  private Map<String, AutogenRule> autogenFieldNames(FlowType flowType) {
    Map<String, AutogenRule> names = new LinkedHashMap<>();
    for (FlowFieldDescriptor descriptor : descriptorsFor(flowType)) {
      if (descriptor.autogen() == AutogenRule.UUID_V4 || descriptor.autogen() == AutogenRule.ULID) {
        names.put(descriptor.name(), descriptor.autogen());
      }
    }
    return names;
  }

  private List<FlowFieldDescriptor> descriptorsFor(FlowType flowType) {
    return catalogProvider.catalog().stream()
        .filter(entry -> entry.flowType() == flowType)
        .findFirst()
        .map(entry -> entry.fields())
        .orElse(List.of());
  }
}
