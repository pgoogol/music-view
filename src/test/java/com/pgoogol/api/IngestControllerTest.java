package com.pgoogol.api;

import com.pgoogol.common.ValidationException;
import com.pgoogol.ingestion.FailedPlaylist;
import com.pgoogol.ingestion.FileIngestionService;
import com.pgoogol.ingestion.MetricsBatchIngestionService;
import com.pgoogol.ingestion.MetricsFileReport;
import com.pgoogol.ingestion.MetricsIngestReport;
import com.pgoogol.ingestion.MyPlaylistsIngestReport;
import com.pgoogol.ingestion.MyPlaylistsIngestionService;
import com.pgoogol.ingestion.NamedCsv;
import com.pgoogol.ingestion.PlaylistIngestReport;
import com.pgoogol.ingestion.PlaylistIngestionService;
import com.pgoogol.ingestion.RowError;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kontrakt HTTP importów bez bazy: wiązanie wielu plików w polu {@code file}
 * (D24) i kształt raportu z importu własnych playlist (tryb C).
 */
@WebMvcTest(IngestController.class)
@Import(IngestApiMapper.class)
class IngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileIngestionService fileIngestionService;

    @MockitoBean
    private PlaylistIngestionService playlistIngestionService;

    @MockitoBean
    private MyPlaylistsIngestionService myPlaylistsIngestionService;

    @MockitoBean
    private MetricsBatchIngestionService metricsBatchIngestionService;

    @Test
    void ingestMetrics_whenSeveralFilesSent_passesAllOfThemWithTheirNames() throws Exception {

        // given
        given(metricsBatchIngestionService.ingestAll(anyList())).willReturn(List.of(
            imported("wesela.csv", 3, 1),
            imported("salsa.csv", 2, 0)));

        // when
        mockMvc.perform(multipart("/api/ingest/metrics")
                .file(csv("wesela.csv"))
                .file(csv("salsa.csv")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applied").value(5))
            .andExpect(jsonPath("$.matchedByIsrc").value(1))
            .andExpect(jsonPath("$.files.length()").value(2));

        // then
        ArgumentCaptor<List<NamedCsv>> uploads = ArgumentCaptor.captor();
        verify(metricsBatchIngestionService).ingestAll(uploads.capture());
        assertThat(uploads.getValue()).extracting(NamedCsv::name)
            .containsExactly("wesela.csv", "salsa.csv");
    }

    @Test
    void ingestMetrics_whenOneFileFailed_returnsOkWithReasonForThatFile() throws Exception {

        // given — plik odrzucony w całości nie może wywracać całego żądania
        given(metricsBatchIngestionService.ingestAll(anyList())).willReturn(List.of(
            new MetricsFileReport.Failed("bez-id.csv", "CSV_MISSING_COLUMNS", "Brak kolumny z id"),
            imported("salsa.csv", 2, 0)));

        // when + then
        mockMvc.perform(multipart("/api/ingest/metrics")
                .file(csv("bez-id.csv"))
                .file(csv("salsa.csv")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applied").value(2))
            .andExpect(jsonPath("$.files[0].errorCode").value("CSV_MISSING_COLUMNS"))
            .andExpect(jsonPath("$.files[0].error").value("Brak kolumny z id"))
            .andExpect(jsonPath("$.files[1].error").doesNotExist());
    }

    @Test
    void ingestMetrics_whenAllFilesEmpty_returnsBadRequest() throws Exception {

        // when + then
        mockMvc.perform(multipart("/api/ingest/metrics")
                .file(new MockMultipartFile("file", "pusty.csv", "text/csv", new byte[0])))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("FILE_EMPTY"));
    }

    @Test
    void ingestMetrics_whenRowsRejected_countsThemAcrossAllFiles() throws Exception {

        // given
        given(metricsBatchIngestionService.ingestAll(anyList())).willReturn(List.of(
            new MetricsFileReport.Imported("wesela.csv", new MetricsIngestReport(1, 0,
                List.of(new RowError(3, "brak takiego utworu w katalogu")),
                List.of(new RowError(4, "wiersz bez metryk")))),
            new MetricsFileReport.Imported("salsa.csv", new MetricsIngestReport(1, 0,
                List.of(new RowError(7, "brak takiego utworu w katalogu")), List.of()))));

        // when + then
        mockMvc.perform(multipart("/api/ingest/metrics").file(csv("wesela.csv")).file(csv("salsa.csv")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skippedRows").value(2))
            .andExpect(jsonPath("$.failedRows").value(1))
            .andExpect(jsonPath("$.files[0].skipped[0].line").value(3))
            .andExpect(jsonPath("$.files[1].skipped[0].line").value(7));
    }

    @Test
    void ingestMyPlaylists_whenSomePlaylistsFailed_returnsBothListsWithHttpOk() throws Exception {

        // given
        given(myPlaylistsIngestionService.ingestMyPlaylists()).willReturn(new MyPlaylistsIngestReport(
            List.of(new PlaylistIngestReport(1L, "pl-wesela", "Wesela 2026", 30, 12, 18, List.of())),
            List.of(new FailedPlaylist("pl-bachata", "Bachata", "SPOTIFY_UNAVAILABLE",
                "Spotify nie odpowiedziało"))));

        // when + then
        mockMvc.perform(post("/api/ingest/my-playlists"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported.length()").value(1))
            .andExpect(jsonPath("$.imported[0].name").value("Wesela 2026"))
            .andExpect(jsonPath("$.failed[0].spotifyPlaylistId").value("pl-bachata"))
            .andExpect(jsonPath("$.failed[0].errorCode").value("SPOTIFY_UNAVAILABLE"))
            .andExpect(jsonPath("$.failed[0].reason").value("Spotify nie odpowiedziało"));
    }

    @Test
    void ingestMyPlaylists_whenAccountNotConnected_returnsBadRequestWithErrorCode() throws Exception {

        // given
        given(myPlaylistsIngestionService.ingestMyPlaylists())
            .willThrow(new ValidationException("SPOTIFY_NOT_CONNECTED", "Konto nie jest połączone"));

        // when + then
        mockMvc.perform(post("/api/ingest/my-playlists"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("SPOTIFY_NOT_CONNECTED"));
    }

    private MetricsFileReport imported(String file, int applied, int matchedByIsrc) {

        return new MetricsFileReport.Imported(file,
            new MetricsIngestReport(applied, matchedByIsrc, List.of(), List.of()));
    }

    private MockMultipartFile csv(String name) {

        return new MockMultipartFile("file", name, "text/csv",
            "Spotify Track Id,BPM\n2c7nzxJYmPtkimDdrhcfJx,96\n".getBytes(StandardCharsets.UTF_8));
    }
}
