package com.pgoogol.api;

import com.pgoogol.TestcontainersConfiguration;
import com.pgoogol.common.NotFoundException;
import com.pgoogol.enrichment.EnrichmentJobStatus;
import com.pgoogol.enrichment.EnrichmentScope;
import com.pgoogol.enrichment.EnrichmentService;
import com.pgoogol.enrichment.FieldGroup;
import com.pgoogol.enrichment.MissingFieldsCount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kontrakt HTTP endpointów Enrich — mechanika joba przetestowana w M1.6
 * (EnrichmentJobIntegrationTest), tu serwis jest zamockowany.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EnrichApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnrichmentService enrichmentService;

    @Test
    void startEnrichment_whenValidRequest_returns202WithExecutionId() throws Exception {

        // given
        given(enrichmentService.start(EnrichmentScope.MISSING,
            Set.of(FieldGroup.METADATA, FieldGroup.AI), List.of())).willReturn(42L);

        // when + then
        mockMvc.perform(post("/api/enrich")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\": \"MISSING\", \"fields\": [\"METADATA\", \"AI\"]}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.executionId").value(42));
    }

    @Test
    void startEnrichment_whenFieldsMissing_returns400WithoutTouchingService() throws Exception {

        mockMvc.perform(post("/api/enrich")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\": \"MISSING\", \"fields\": []}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        then(enrichmentService).shouldHaveNoInteractions();
    }

    @Test
    void jobStatus_whenExecutionExists_returnsProgress() throws Exception {

        // given
        given(enrichmentService.status(42L)).willReturn(new EnrichmentJobStatus(
            42L, 7L, "COMPLETED", "MISSING", "METADATA,AUDIO,AI", 12, 12,
            LocalDateTime.of(2026, 7, 5, 12, 0), LocalDateTime.of(2026, 7, 5, 12, 5), ""));

        // when + then
        mockMvc.perform(get("/api/enrich/jobs/42"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.writeCount").value(12))
            .andExpect(jsonPath("$.fields").value("METADATA,AUDIO,AI"));
    }

    @Test
    void jobStatus_whenExecutionMissing_returns404() throws Exception {

        // given
        given(enrichmentService.status(anyLong()))
            .willThrow(new NotFoundException("JOB_NOT_FOUND", "Brak wykonania"));

        // when + then
        mockMvc.perform(get("/api/enrich/jobs/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    @Test
    void restartJob_whenCalled_returns202WithNewExecutionId() throws Exception {

        // given
        given(enrichmentService.restart(42L)).willReturn(43L);

        // when + then
        mockMvc.perform(post("/api/enrich/jobs/42/restart"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.executionId").value(43));
    }

    @Test
    void missingCount_whenCalled_returnsCountsPerFieldGroup() throws Exception {

        // given
        given(enrichmentService.missingCount()).willReturn(new MissingFieldsCount(10, 20, 30));

        // when + then
        mockMvc.perform(get("/api/enrich/missing-count"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metadata").value(10))
            .andExpect(jsonPath("$.audio").value(20))
            .andExpect(jsonPath("$.ai").value(30));
    }
}
