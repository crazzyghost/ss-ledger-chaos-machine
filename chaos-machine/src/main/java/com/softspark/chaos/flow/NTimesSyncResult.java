package com.softspark.chaos.flow;

import com.softspark.chaos.flow.model.FlowType;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

/**
 * Aggregate result of a synchronous N-Times run, returned by the {@code /n-times} endpoint with
 * HTTP {@code 200}.
 *
 * @param flowType the flow type that was run
 * @param count the number of iterations requested
 * @param succeeded iterations that published successfully
 * @param failed iterations that failed to publish (best-effort; the run continues on failure)
 * @param correlationId the single correlation id shared by all iterations
 * @param eventIds the per-iteration published event ids, in order
 * @param historyIds the per-iteration publish-history record ids, in order
 * @param transactionRequestIds the per-iteration transaction request ids (server-minted by the
 *     N-Times expander), in order; entries may be null for non-transactional flows
 */
@RecordBuilder
public record NTimesSyncResult(
    FlowType flowType,
    int count,
    int succeeded,
    int failed,
    String correlationId,
    List<String> eventIds,
    List<String> historyIds,
    List<String> transactionRequestIds) {}
