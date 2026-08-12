package com.pgoogol.api;

import com.pgoogol.common.ValidationException;
import com.pgoogol.ingestion.FileIngestionService;
import com.pgoogol.ingestion.MetricsBatchIngestionService;
import com.pgoogol.ingestion.MyPlaylistsIngestionService;
import com.pgoogol.ingestion.NamedCsv;
import com.pgoogol.ingestion.PlaylistIngestionService;
import com.pgoogol.ingestion.PlaylistRefreshProperties;
import com.pgoogol.ingestion.PlaylistRefreshScheduler;
import com.pgoogol.ingestion.PlaylistRefreshStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/ingest")
@Tag(name = "Ingestion", description = "Import utworów do biblioteki")
public class IngestController {

    private final FileIngestionService fileIngestionService;
    private final PlaylistIngestionService playlistIngestionService;
    private final MyPlaylistsIngestionService myPlaylistsIngestionService;
    private final MetricsBatchIngestionService metricsBatchIngestionService;
    private final PlaylistRefreshScheduler playlistRefreshScheduler;
    private final PlaylistRefreshProperties playlistRefreshProperties;
    private final IngestApiMapper mapper;

    public IngestController(FileIngestionService fileIngestionService,
                            PlaylistIngestionService playlistIngestionService,
                            MyPlaylistsIngestionService myPlaylistsIngestionService,
                            MetricsBatchIngestionService metricsBatchIngestionService,
                            PlaylistRefreshScheduler playlistRefreshScheduler,
                            PlaylistRefreshProperties playlistRefreshProperties,
                            IngestApiMapper mapper) {

        this.playlistRefreshScheduler = playlistRefreshScheduler;
        this.playlistRefreshProperties = playlistRefreshProperties;
        this.fileIngestionService = fileIngestionService;
        this.playlistIngestionService = playlistIngestionService;
        this.myPlaylistsIngestionService = myPlaylistsIngestionService;
        this.metricsBatchIngestionService = metricsBatchIngestionService;
        this.mapper = mapper;
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import CSV do biblioteki",
        description = "Eksport z Exportify, z analizatora playlist albo plik własny — "
            + "utwór rozpoznajemy po kolumnie z URI, linkiem lub samym Spotify Track Id, "
            + "a tytuł i wykonawcę po nagłówkach w kilku wariantach nazw.")
    public IngestFileResponse ingestFile(@RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            throw new ValidationException("FILE_EMPTY", "Przesłany plik jest pusty");
        }
        try (InputStream input = file.getInputStream()) {
            return mapper.toResponse(fileIngestionService.ingestFile(input));
        } catch (IOException ex) {
            throw new ValidationException("FILE_UNREADABLE", "Nie udało się odczytać przesłanego pliku");
        }
    }

    @PostMapping(value = "/metrics", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import metryk utworów z CSV (D24)",
        description = "Uzupełnia BPM, tonację, Camelot i cechy audio dla utworów, "
            + "które są już w katalogu — dopasowanie po Spotify Track Id, "
            + "a gdy go brak, po ISRC. Utwory spoza katalogu trafiają do raportu "
            + "jako pominięte; ponowny import nadpisuje metryki. Pole `file` można "
            + "podać wiele razy — każdy plik idzie osobno, a plik odrzucony w całości "
            + "wraca w raporcie z powodem, nie przerywając pozostałych.")
    public IngestMetricsResponse ingestMetrics(@RequestParam("file") List<MultipartFile> files) {

        if (files.stream().allMatch(MultipartFile::isEmpty)) {
            throw new ValidationException("FILE_EMPTY", "Przesłany plik jest pusty");
        }
        List<NamedCsv> uploads = files.stream()
            .map(file -> new NamedCsv(
                Objects.requireNonNullElse(file.getOriginalFilename(), "bez nazwy"), file))
            .toList();
        return mapper.toResponse(metricsBatchIngestionService.ingestAll(uploads));
    }

    @PostMapping("/playlist")
    @Operation(summary = "Import playlisty ze Spotify po linku",
        description = "Utwory trafiają do katalogu i biblioteki (dedup po spotify_id), "
            + "playlista odtwarzana lokalnie wraz z kolejnością. Ponowny import "
            + "aktualizuje nazwę i kolejność, nie duplikuje wpisów.")
    public IngestPlaylistResponse ingestPlaylist(@Valid @RequestBody IngestPlaylistRequest request) {

        return mapper.toResponse(playlistIngestionService.ingest(request.url()));
    }

    @PostMapping("/my-playlists")
    @Operation(summary = "Import wszystkich własnych playlist połączonego konta (tryb C)",
        description = "Wymaga połączonego konta Spotify (GET /api/auth/spotify/login). "
            + "Playlisty obserwowane, ale cudze, są pomijane — importuj je po linku. "
            + "Playlista, która padła, nie przerywa przebiegu: wraca w `failed` "
            + "z powodem i wystarczy powtórzyć ją osobno.")
    public IngestMyPlaylistsResponse ingestMyPlaylists() {

        return mapper.toResponse(myPlaylistsIngestionService.ingestMyPlaylists());
    }

    @GetMapping("/my-playlists/refresh-status")
    @Operation(summary = "Stan automatycznego odświeżania playlist (D35)",
        description = "Kiedy poszedł ostatni przebieg w tle i czym się skończył. "
            + "SKIPPED_NOT_CONNECTED to normalny stan świeżej instalacji, nie awaria — "
            + "bez połączonego konta Spotify nie ma czego odświeżać.")
    public PlaylistRefreshStatusResponse refreshStatus() {

        PlaylistRefreshStatus status = playlistRefreshScheduler.status();
        return new PlaylistRefreshStatusResponse(
            status.outcome().name(),
            status.lastRunAt(),
            status.refreshedPlaylists(),
            status.failedPlaylists(),
            status.message(),
            playlistRefreshProperties.interval().toSeconds());
    }
}
