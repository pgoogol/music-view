package com.pgoogol.api;

import com.pgoogol.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DoD M1.7: wszystkie endpointy Etapu 1 widoczne w definicji OpenAPI
 * (wywoływalne ze Swagger UI pod /swagger-ui.html).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiDocsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_whenFetched_containStageOneEndpoints() throws Exception {

        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("music-view API"))
            .andExpect(jsonPath("$.paths['/api/ingest/file'].post").exists())
            .andExpect(jsonPath("$.paths['/api/catalog/tracks'].get").exists())
            .andExpect(jsonPath("$.paths['/api/catalog/tracks/{spotifyId}'].get").exists())
            .andExpect(jsonPath("$.paths['/api/library/tracks'].get").exists())
            .andExpect(jsonPath("$.paths['/api/library/tracks'].post").exists())
            .andExpect(jsonPath("$.paths['/api/library/tracks/{spotifyId}'].patch").exists())
            .andExpect(jsonPath("$.paths['/api/library/tracks/{spotifyId}'].delete").exists())
            .andExpect(jsonPath("$.paths['/api/enrich'].post").exists())
            .andExpect(jsonPath("$.paths['/api/enrich/jobs'].get").exists())
            .andExpect(jsonPath("$.paths['/api/enrich/jobs/{executionId}'].get").exists())
            .andExpect(jsonPath("$.paths['/api/enrich/jobs/{executionId}/restart'].post").exists())
            .andExpect(jsonPath("$.paths['/api/enrich/missing-count'].get").exists());
    }

    @Test
    void swaggerUi_whenOpened_isAvailable() throws Exception {

        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is3xxRedirection());
    }
}
