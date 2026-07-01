package com.softspark.chaos.run.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.softspark.chaos.advice.GlobalExceptionHandler;
import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.config.SecurityConfiguration;
import com.softspark.chaos.run.dto.RunStatusRollup;
import com.softspark.chaos.run.dto.RunSummaryResponse;
import com.softspark.chaos.run.dto.RunSummaryResponseBuilder;
import com.softspark.chaos.run.dto.RunsQuery;
import com.softspark.chaos.run.service.RunSummaryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * WebMvc slice tests for {@link RunController}.
 */
@WebMvcTest(RunController.class)
@Import({GlobalExceptionHandler.class, SecurityConfiguration.class})
@DisplayName("RunController")
class RunControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private RunSummaryService runSummaryService;

  private RunSummaryResponse sampleRun() {
    return RunSummaryResponseBuilder.builder()
        .runKey("run-1")
        .tracked(true)
        .kind("N_TIMES")
        .flowTypes(List.of("collection.completed"))
        .eventCount(5)
        .status(RunStatusRollup.ALL_PUBLISHED)
        .publishedCount(5)
        .failedCount(0)
        .intentionalFailure(false)
        .firstActivityAt(Instant.parse("2026-06-29T10:00:00Z"))
        .lastActivityAt(Instant.parse("2026-06-29T10:01:00Z"))
        .externalBatchId(null)
        .correlationId(null)
        .batchId("run-1")
        .build();
  }

  @Test
  @WithMockUser
  @DisplayName("returns 200 with the paged run feed")
  void returns200WithRuns() throws Exception {
    when(runSummaryService.listRuns(any()))
        .thenReturn(new PageResponse<>(List.of(sampleRun()), 0, 20, 1L));

    mockMvc
        .perform(get("/api/v0/runs").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].runKey").value("run-1"))
        .andExpect(jsonPath("$.items[0].tracked").value(true))
        .andExpect(jsonPath("$.items[0].batchId").value("run-1"));
  }

  @Test
  @WithMockUser
  @DisplayName("clamps the page size to 100 and forwards filters to the service")
  void clampsPagingAndForwardsFilters() throws Exception {
    when(runSummaryService.listRuns(any()))
        .thenReturn(new PageResponse<>(List.of(), 0, 100, 0L));

    mockMvc
        .perform(
            get("/api/v0/runs")
                .param("size", "500")
                .param("kind", "N_TIMES")
                .param("from", "2026-06-01T00:00:00Z")
                .param("to", "2026-06-30T00:00:00Z")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    var captor = ArgumentCaptor.forClass(RunsQuery.class);
    verify(runSummaryService).listRuns(captor.capture());
    var query = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(query.size()).isEqualTo(100);
    org.assertj.core.api.Assertions.assertThat(query.kind()).isEqualTo("N_TIMES");
    org.assertj.core.api.Assertions.assertThat(query.from())
        .isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
  }

  @Test
  @WithMockUser
  @DisplayName("returns 200 with an empty feed when there are no runs")
  void returns200WithEmptyFeed() throws Exception {
    when(runSummaryService.listRuns(any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0L));

    mockMvc
        .perform(get("/api/v0/runs").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  @DisplayName("requires authentication (401 without a verified token)")
  void requiresAuth() throws Exception {
    mockMvc
        .perform(get("/api/v0/runs").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }
}
