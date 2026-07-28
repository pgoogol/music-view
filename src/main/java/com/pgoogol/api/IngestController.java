package com.pgoogol.api;

import com.pgoogol.common.ValidationException;
import com.pgoogol.ingestion.FileIngestionService;
import com.pgoogol.ingestion.MyPlaylistsIngestionService;
import com.pgoogol.ingestion.PlaylistIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@RestController
@RequestMapping("/api/ingest")
@Tag(name = "Ingestion", description = "Import utworów do biblioteki")
public class IngestController {

    private final FileIngestionService fileIngestionService;
    private final PlaylistIngestionService playlistIngestionService;
    private final MyPlaylistsIngestionService myPlaylistsIngestionService;
    private final IngestApiMapper mapper;

    public IngestController(FileIngestionService fileIngestionService,
                            PlaylistIngestionService playlistIngestionService,
                            MyPlaylistsIngestionService myPlaylistsIngestionService,
                            IngestApiMapper mapper) {

        this.fileIngestionService = fileIngestionService;
        this.playlistIngestionService = playlistIngestionService;
        this.myPlaylistsIngestionService = myPlaylistsIngestionService;
        this.mapper = mapper;
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import CSV (Exportify) do biblioteki")
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
            + "Playlisty obserwowane, ale cudze, są pomijane — importuj je po linku.")
    public List<IngestPlaylistResponse> ingestMyPlaylists() {

        return myPlaylistsIngestionService.ingestMyPlaylists().stream()
            .map(mapper::toResponse)
            .toList();
    }
}
