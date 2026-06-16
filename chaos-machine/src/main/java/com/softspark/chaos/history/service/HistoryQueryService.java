package com.softspark.chaos.history.service;

import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.history.dto.HistoryQuery;
import com.softspark.chaos.history.dto.PublishRecordResponse;
import com.softspark.chaos.history.repository.PublishRecordRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for querying the publish history.
 */
@Service
public class HistoryQueryService {

  private final PublishRecordRepository repository;

  public HistoryQueryService(PublishRecordRepository repository) {
    this.repository = repository;
  }

  /**
   * Retrieves a single publish record by id.
   *
   * @param id the history record ULID
   * @return the record response
   * @throws NotFoundException if the record does not exist
   */
  @Transactional(readOnly = true)
  public PublishRecordResponse getRecord(String id) {
    return repository
        .findById(id)
        .map(PublishRecordResponse::from)
        .orElseThrow(() -> new NotFoundException("Publish record not found: " + id));
  }

  /**
   * Queries publish history with optional filters and pagination.
   *
   * @param query the query parameters
   * @return a paginated list of matching records
   */
  @Transactional(readOnly = true)
  public PageResponse<PublishRecordResponse> queryHistory(HistoryQuery query) {
    var pageable =
        PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.DESC, "createdAt"));

    org.springframework.data.domain.Page<com.softspark.chaos.history.model.PublishRecord> page;

    if (query.correlationId() != null) {
      page = repository.findByCorrelationId(query.correlationId(), pageable);
    } else if (query.batchId() != null) {
      page = repository.findByBatchId(query.batchId(), pageable);
    } else if (query.eventType() != null) {
      page = repository.findByEventType(query.eventType(), pageable);
    } else if (query.sourceVaId() != null || query.destinationVaId() != null) {
      String vaId = query.sourceVaId() != null ? query.sourceVaId() : query.destinationVaId();
      page = repository.findBySourceVaIdOrDestinationVaId(vaId, vaId, pageable);
    } else if (query.from() != null && query.to() != null) {
      page = repository.findByCreatedAtBetween(query.from(), query.to(), pageable);
    } else if (query.status() != null) {
      page = repository.findByStatus(query.status(), pageable);
    } else {
      page = repository.findAll(pageable);
    }

    var items = page.getContent().stream().map(PublishRecordResponse::from).toList();
    return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
  }
}
