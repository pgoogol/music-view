package com.pgoogol.ingestion;

import com.pgoogol.TestcontainersConfiguration;
import com.pgoogol.catalog.BpmSource;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.ManualMetricsRepository;
import com.pgoogol.catalog.TempoClass;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pełny stos HTTP dla POST /api/ingest/metrics na realnym Postgresie (D24):
 * dopasowanie po spotify_id i po ISRC, utwór spoza katalogu, wiersze odrzucone
 * oraz projekcja metryk na katalog (BPM z korektą half-time).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IngestMetricsIntegrationTest {

    private static final String LAMPARA = "2c7nzxJYmPtkimDdrhcfJx";
    private static final String CARNAVAL = "1BwrMGGhPA6GarWIYaFrW8";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrackCatalogRepository trackCatalogRepository;

    @Autowired
    private ManualMetricsRepository manualMetricsRepository;

    @BeforeEach
    void seedCatalog() {

        TrackCatalog lampara = new TrackCatalog(LAMPARA, "La Lámpara", "Alain Pérez");
        lampara.setIsrc("ES71G2337397");
        lampara.setGenreFamily(GenreFamily.LATIN);
        TrackCatalog carnaval = new TrackCatalog(CARNAVAL, "La Vida Es Un Carnaval", "Celia Cruz");
        carnaval.setIsrc("USUL10110340");
        trackCatalogRepository.saveAll(List.of(lampara, carnaval));
    }

    @AfterEach
    void cleanDatabase() {

        manualMetricsRepository.deleteAll();
        trackCatalogRepository.deleteAll();
    }

    @Test
    void ingestMetrics_whenSampleFileUploaded_appliesMetricsAndReportsRest() throws Exception {

        // when + then
        mockMvc.perform(multipart("/api/ingest/metrics").file(sampleCsv()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applied").value(2))
            .andExpect(jsonPath("$.matchedByIsrc").value(1))
            .andExpect(jsonPath("$.skipped.length()").value(1))
            .andExpect(jsonPath("$.skipped[0].line").value(3))
            .andExpect(jsonPath("$.failed.length()").value(2));

        assertThat(manualMetricsRepository.count()).isEqualTo(2);
    }

    @Test
    void ingestMetrics_whenTrackIsLatin_projectsBpmWithHalfTimeCorrection() throws Exception {

        // when
        mockMvc.perform(multipart("/api/ingest/metrics").file(sampleCsv()))
            .andExpect(status().isOk());

        // then — 96 z pliku to half-time salsy; katalog dostaje realne 192
        assertThat(trackCatalogRepository.findById(LAMPARA)).hasValueSatisfying(track -> {
            assertThat(track.getBpm()).isEqualTo(192);
            assertThat(track.getBpmSource()).isEqualTo(BpmSource.MANUAL);
            assertThat(track.getTempoClass()).isEqualTo(TempoClass.VERY_FAST);
            assertThat(track.getMusicalKey()).isEqualTo("G minor");
            assertThat(track.getDanceability()).isEqualByComparingTo("0.66");
            assertThat(track.getEnergy()).isEqualTo("high");
        });
    }

    @Test
    void ingestMetrics_whenRowHasOnlyIsrc_matchesRecordingInCatalog() throws Exception {

        // when
        mockMvc.perform(multipart("/api/ingest/metrics").file(sampleCsv()))
            .andExpect(status().isOk());

        // then
        assertThat(trackCatalogRepository.findById(CARNAVAL)).hasValueSatisfying(track -> {
            assertThat(track.getBpm()).isEqualTo(104);
            assertThat(track.getBpmSource()).isEqualTo(BpmSource.MANUAL);
            assertThat(track.getMusicalKey()).isEqualTo("C minor");
        });
    }

    @Test
    void ingestMetrics_whenSameFileUploadedTwice_updatesInsteadOfDuplicating() throws Exception {

        // given
        mockMvc.perform(multipart("/api/ingest/metrics").file(sampleCsv()))
            .andExpect(status().isOk());

        // when + then
        mockMvc.perform(multipart("/api/ingest/metrics").file(sampleCsv()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applied").value(2));

        assertThat(manualMetricsRepository.count()).isEqualTo(2);
    }

    @Test
    void ingestMetrics_whenFileEmpty_returnsBadRequestWithErrorCode() throws Exception {

        // given
        MockMultipartFile file = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

        // when + then
        mockMvc.perform(multipart("/api/ingest/metrics").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("FILE_EMPTY"));
    }

    @Test
    void getTrackMetrics_whenMetricsImported_returnsRawValuesFromFile() throws Exception {

        // given
        mockMvc.perform(multipart("/api/ingest/metrics").file(sampleCsv()))
            .andExpect(status().isOk());

        // when + then — w katalogu BPM jest po korekcie, tutaj surowe 96 z pliku
        mockMvc.perform(get("/api/catalog/tracks/{id}/metrics", LAMPARA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bpm").value(96.00))
            .andExpect(jsonPath("$.camelot").value("6A"))
            .andExpect(jsonPath("$.energy").value(0.890))
            .andExpect(jsonPath("$.timeSignature").value(4))
            .andExpect(jsonPath("$.source").value("metryki-sample.csv"));
    }

    @Test
    void getTrackMetrics_whenTrackHasNoMetrics_returnsNoContent() throws Exception {

        // when + then
        mockMvc.perform(get("/api/catalog/tracks/{id}/metrics", LAMPARA))
            .andExpect(status().isNoContent());
    }

    private MockMultipartFile sampleCsv() throws Exception {

        ClassPathResource resource = new ClassPathResource("test-data/metryki-sample.csv");
        return new MockMultipartFile("file", "metryki-sample.csv", "text/csv",
            resource.getInputStream());
    }
}
