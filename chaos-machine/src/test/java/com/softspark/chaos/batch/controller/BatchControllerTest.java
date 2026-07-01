package com.softspark.chaos.batch.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.softspark.chaos.advice.GlobalExceptionHandler;
import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.batch.dto.BatchRowResponse;
import com.softspark.chaos.batch.dto.BatchRunResponse;
import com.softspark.chaos.batch.enumeration.BatchRowStatus;
import com.softspark.chaos.batch.enumeration.BatchRunStatus;
import com.softspark.chaos.batch.enumeration.RunKind;
import com.softspark.chaos.batch.service.BatchService;
import com.softspark.chaos.config.SecurityConfiguration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * WebMvc slice tests for {@link BatchController} — the CSV ingest + run-list endpoints are gone
 * (404), the read-by-id endpoints remain.
 */
@WebMvcTest(BatchController.class)
@Import({GlobalExceptionHandler.class, SecurityConfiguration.class})
@DisplayName("BatchController")
class BatchControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private BatchService batchService;

  private BatchRunResponse sampleRun() {
    return new BatchRunResponse(
        "run-1",
        "COLLECTION_INITIATED",
        null,
        RunKind.N_TIMES,
        "BURST",
        "ASYNC",
        5,
        5,
        0,
        0,
        BatchRunStatus.COMPLETED,
        Instant.parse("2026-06-29T10:00:00Z"),
        Instant.parse("2026-06-29T10:00:05Z"),
        null,
        null);
  }

  @Nested
  @DisplayName("preserved read endpoints")
  class ReadEndpoints {

    @Test
    @WithMockUser
    @DisplayName("GET /{id} returns 200 with the run")
    void getBatch_returns200() throws Exception {
      when(batchService.getRunById("run-1")).thenReturn(sampleRun());

      mockMvc
          .perform(get("/api/v0/batches/run-1").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value("run-1"))
          .andExpect(jsonPath("$.kind").value("N_TIMES"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /{id}/rows returns 200 with the rows")
    void getRows_returns200() throws Exception {
      var row =
          new BatchRowResponse(
              "row-1", "run-1", 0, BatchRowStatus.PUBLISHED, "evt-1", null,
              Instant.parse("2026-06-29T10:00:00Z"));
      when(batchService.getRows(eq("run-1"), anyInt(), anyInt()))
          .thenReturn(new PageResponse<>(List.of(row), 0, 20, 1L));

      mockMvc
          .perform(get("/api/v0/batches/run-1/rows").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items[0].id").value("row-1"))
          .andExpect(jsonPath("$.items[0].status").value("PUBLISHED"));
    }
  }

  @Nested
  @DisplayName("retired CSV-ingest endpoints")
  class RetiredEndpoints {

    @Test
    @WithMockUser
    @DisplayName("POST /api/v0/batches (CSV upload) is gone (404)")
    void createBatch_returns404() throws Exception {
      var file =
          new MockMultipartFile(
              "file", "rows.csv", MediaType.TEXT_PLAIN_VALUE, "a,b,c".getBytes());
      mockMvc
          .perform(multipart("/api/v0/batches").file(file).param("flowType", "COLLECTION_INITIATED"))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v0/batches (run list) is gone (404)")
    void listBatches_returns404() throws Exception {
      mockMvc
          .perform(get("/api/v0/batches").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }
  }
}
