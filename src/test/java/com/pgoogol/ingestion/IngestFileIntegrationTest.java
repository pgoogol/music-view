package com.pgoogol.ingestion;

import com.pgoogol.TestcontainersConfiguration;
import com.pgoogol.catalog.TrackCatalogRepository;
import com.pgoogol.library.LibraryEntryRepository;
import com.pgoogol.library.LibrarySource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pełny stos HTTP dla POST /api/ingest/file na realnym Postgresie —
 * próbka w stylu Exportify: 4 poprawne utwory, duplikat w pliku, wiersz odrzucany.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IngestFileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrackCatalogRepository trackCatalogRepository;

    @Autowired
    private LibraryEntryRepository libraryEntryRepository;

    @AfterEach
    void cleanDatabase() {

        libraryEntryRepository.deleteAll();
        trackCatalogRepository.deleteAll();
    }

    @Test
    void ingestFile_whenSampleCsvUploaded_importsTracksAndReportsFailures() throws Exception {

        // given
        MockMultipartFile file = sampleCsv();

        // when + then
        mockMvc.perform(multipart("/api/ingest/file").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported").value(4))
            .andExpect(jsonPath("$.alreadyExisted").value(1))
            .andExpect(jsonPath("$.failed.length()").value(1))
            .andExpect(jsonPath("$.failed[0].line").value(6));

        assertThat(trackCatalogRepository.count()).isEqualTo(4);
        assertThat(libraryEntryRepository.count()).isEqualTo(4);
        assertThat(libraryEntryRepository.findByTrackSpotifyId("4uLU6hMCjMI75M1A2tKUQC"))
            .hasValueSatisfying(entry -> assertThat(entry.getSource()).isEqualTo(LibrarySource.FILE));
        assertThat(trackCatalogRepository.findById("4uLU6hMCjMI75M1A2tKUQC"))
            .hasValueSatisfying(track -> {
                assertThat(track.getTitle()).isEqualTo("Vivir Mi Vida");
                assertThat(track.getAlbum()).isEqualTo("3.0");
            });
    }

    @Test
    void ingestFile_whenSameFileUploadedTwice_secondImportAddsNothing() throws Exception {

        // given
        mockMvc.perform(multipart("/api/ingest/file").file(sampleCsv()))
            .andExpect(status().isOk());

        // when + then
        mockMvc.perform(multipart("/api/ingest/file").file(sampleCsv()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported").value(0))
            .andExpect(jsonPath("$.alreadyExisted").value(5));

        assertThat(libraryEntryRepository.count()).isEqualTo(4);
    }

    @Test
    void ingestFile_whenEmptyFileUploaded_returnsBadRequestWithErrorCode() throws Exception {

        // given
        MockMultipartFile file = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

        // when + then
        mockMvc.perform(multipart("/api/ingest/file").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("FILE_EMPTY"));
    }

    @Test
    void ingestFile_whenLargeLibraryUploaded_importsAllRows() throws Exception {

        // given — plik rzędu realnego eksportu (~2500 wierszy, DoD M1.2)
        String rows = IntStream.range(0, 2500)
            .mapToObj(i -> "spotify:track:%022d,Utwór %d,Wykonawca %d,Album %d".formatted(i, i, i, i))
            .collect(Collectors.joining("\n"));
        MockMultipartFile file = new MockMultipartFile("file", "large.csv", "text/csv",
            ("Track URI,Track Name,Artist Name(s),Album Name\n" + rows).getBytes(StandardCharsets.UTF_8));

        // when + then
        mockMvc.perform(multipart("/api/ingest/file").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported").value(2500))
            .andExpect(jsonPath("$.failed.length()").value(0));

        assertThat(libraryEntryRepository.count()).isEqualTo(2500);
    }

    private MockMultipartFile sampleCsv() throws Exception {

        ClassPathResource resource = new ClassPathResource("test-data/exportify-sample.csv");
        return new MockMultipartFile("file", "exportify-sample.csv", "text/csv",
            resource.getInputStream());
    }
}
