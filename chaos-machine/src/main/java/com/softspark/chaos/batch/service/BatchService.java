package com.softspark.chaos.batch.service;

import com.softspark.chaos.base.Ids;
import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.batch.csv.CsvFlowParser;
import com.softspark.chaos.batch.dto.BatchRowResponse;
import com.softspark.chaos.batch.dto.BatchRunResponse;
import com.softspark.chaos.batch.enumeration.BatchRowStatus;
import com.softspark.chaos.batch.enumeration.BatchRunStatus;
import com.softspark.chaos.batch.model.BatchRow;
import com.softspark.chaos.batch.model.BatchRun;
import com.softspark.chaos.batch.repository.BatchRowRepository;
import com.softspark.chaos.batch.repository.BatchRunRepository;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.flow.chaos.ChaosOptions;
import com.softspark.chaos.flow.model.FlowType;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Manages CSV batch publishing runs.
 *
 * <p>Validates uploaded CSV files, creates batch and row records, then delegates asynchronous
 * execution to {@link BatchRunner}.
 */
@Service
public class BatchService {

  private static final Logger log = LoggerFactory.getLogger(BatchService.class);

  private final CsvFlowParser csvFlowParser;
  private final BatchRunRepository batchRunRepository;
  private final BatchRowRepository batchRowRepository;
  private final BatchRunner batchRunner;
  private final int maxRows;

  public BatchService(
      CsvFlowParser csvFlowParser,
      BatchRunRepository batchRunRepository,
      BatchRowRepository batchRowRepository,
      BatchRunner batchRunner,
      @Value("${chaos.batch.max-rows:50000}") int maxRows) {
    this.csvFlowParser = csvFlowParser;
    this.batchRunRepository = batchRunRepository;
    this.batchRowRepository = batchRowRepository;
    this.batchRunner = batchRunner;
    this.maxRows = maxRows;
  }

  /**
   * Creates and starts a new batch run from a CSV file.
   *
   * @param file the uploaded CSV file
   * @param flowType the flow type for all rows
   * @param maxRatePerSecond optional rate cap in events per second
   * @param chaos optional chaos injection options
   * @return the created batch run response (status = RUNNING)
   * @throws BadRequestException if the file is empty or headers are invalid
   */
  @Transactional
  public BatchRunResponse createBatch(
      MultipartFile file,
      FlowType flowType,
      @Nullable Integer maxRatePerSecond,
      @Nullable ChaosOptions chaos) {

    if (file == null || file.isEmpty()) {
      throw new BadRequestException("Batch file must not be empty");
    }

    List<CsvFlowParser.ParsedRow> parsedRows;
    try {
      parsedRows = csvFlowParser.parse(file.getInputStream(), flowType);
    } catch (BadRequestException e) {
      throw e;
    } catch (IOException e) {
      throw new BadRequestException("Failed to read batch file: " + e.getMessage());
    }

    if (parsedRows.isEmpty()) {
      throw new BadRequestException("Batch file contains no data rows");
    }

    if (parsedRows.size() > maxRows) {
      throw new BadRequestException(
          "Batch file contains " + parsedRows.size() + " rows, exceeding maximum of " + maxRows);
    }

    // Create BatchRun
    var batchRun = new BatchRun();
    batchRun.setId(Ids.generate());
    batchRun.setFlowType(flowType.name());
    batchRun.setFilename(file.getOriginalFilename());
    batchRun.setTotal(parsedRows.size());
    batchRun.setStatus(BatchRunStatus.RUNNING);
    batchRun.setCreatedAt(Instant.now());
    batchRunRepository.save(batchRun);

    // Create BatchRow entities and build RowWithRequest list
    List<BatchRunner.RowWithRequest> rowsWithRequests = new ArrayList<>(parsedRows.size());

    for (CsvFlowParser.ParsedRow parsedRow : parsedRows) {
      var batchRow = new BatchRow();
      batchRow.setId(Ids.generate());
      batchRow.setBatchId(batchRun.getId());
      batchRow.setRowNumber(parsedRow.rowNumber());
      batchRow.setCreatedAt(Instant.now());

      if (parsedRow.isInvalid()) {
        batchRow.setStatus(BatchRowStatus.INVALID);
        batchRow.setError(parsedRow.error());
      } else {
        batchRow.setStatus(BatchRowStatus.PENDING);
      }
      batchRowRepository.save(batchRow);

      if (!parsedRow.isInvalid()) {
        rowsWithRequests.add(new BatchRunner.RowWithRequest(batchRow, parsedRow.flowRequest()));
      }
    }

    log.info(
        "Created batch run {} for flow {} with {} rows ({} invalid)",
        batchRun.getId(),
        flowType,
        parsedRows.size(),
        parsedRows.stream().filter(CsvFlowParser.ParsedRow::isInvalid).count());

    batchRunner.execute(batchRun.getId(), rowsWithRequests, maxRatePerSecond, chaos);

    return BatchRunResponse.from(batchRun);
  }

  /**
   * Retrieves a batch run by id.
   *
   * @param id the batch run ULID
   * @return the batch run response
   * @throws NotFoundException if not found
   */
  @Transactional(readOnly = true)
  public BatchRunResponse getRunById(String id) {
    return batchRunRepository
        .findById(id)
        .map(BatchRunResponse::from)
        .orElseThrow(() -> new NotFoundException("Batch run not found: " + id));
  }

  /**
   * Returns a paginated list of all batch runs, newest first.
   *
   * @param page zero-based page number
   * @param size page size
   * @return a paginated list
   */
  @Transactional(readOnly = true)
  public PageResponse<BatchRunResponse> listRuns(int page, int size) {
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    var result = batchRunRepository.findAllByOrderByCreatedAtDesc(pageable);
    var items = result.getContent().stream().map(BatchRunResponse::from).toList();
    return new PageResponse<>(
        items, result.getNumber(), result.getSize(), result.getTotalElements());
  }

  /**
   * Returns a paginated list of rows for a batch run.
   *
   * @param batchId the batch run id
   * @param page zero-based page number
   * @param size page size
   * @return a paginated list of rows
   * @throws NotFoundException if the batch run does not exist
   */
  @Transactional(readOnly = true)
  public PageResponse<BatchRowResponse> getRows(String batchId, int page, int size) {
    if (!batchRunRepository.existsById(batchId)) {
      throw new NotFoundException("Batch run not found: " + batchId);
    }
    var pageable = PageRequest.of(page, size);
    var result = batchRowRepository.findByBatchIdOrderByRowNumber(batchId, pageable);
    var items = result.getContent().stream().map(BatchRowResponse::from).toList();
    return new PageResponse<>(
        items, result.getNumber(), result.getSize(), result.getTotalElements());
  }
}
