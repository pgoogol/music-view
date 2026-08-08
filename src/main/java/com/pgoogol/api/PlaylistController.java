package com.pgoogol.api;

import com.pgoogol.api.PlaylistRequests.AddPlaylistTrackRequest;
import com.pgoogol.api.PlaylistRequests.ReorderPlaylistRequest;
import com.pgoogol.api.PlaylistRequests.SavePlaylistRequest;
import com.pgoogol.playlist.PlaylistExportService;
import com.pgoogol.playlist.PlaylistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/playlists")
@Tag(name = "Playlists", description = "Playlisty i planowanie setów (D9)")
public class PlaylistController {

    private final PlaylistService playlistService;
    private final PlaylistExportService playlistExportService;
    private final PlaylistApiMapper mapper;

    public PlaylistController(PlaylistService playlistService,
                              PlaylistExportService playlistExportService,
                              PlaylistApiMapper mapper) {

        this.playlistService = playlistService;
        this.playlistExportService = playlistExportService;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Lista playlist z liczbą utworów")
    public List<PlaylistSummaryResponse> list() {

        return playlistService.list().stream()
            .map(mapper::toResponse)
            .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Playlista ze składem setu i slotami wieczoru",
        description = "djSlot = override DJ-a, a gdy go nie ma — wyliczenie z bpm/energy/"
            + "genre_family (D9); null oznacza utwór jeszcze niewzbogacony.")
    public PlaylistResponse get(@PathVariable Long id) {
        return mapper.toResponse(playlistService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nowa (pusta) playlista")
    public PlaylistSummaryResponse create(@Valid @RequestBody SavePlaylistRequest request) {
        return mapper.toResponse(playlistService.create(request.name()));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Zmiana nazwy playlisty")
    public PlaylistSummaryResponse rename(@PathVariable Long id,
                                          @Valid @RequestBody SavePlaylistRequest request) {

        return mapper.toResponse(playlistService.rename(id, request.name(), request.version()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Usunięcie playlisty (utwory zostają w katalogu i bibliotece)")
    public void delete(@PathVariable Long id) {
        playlistService.delete(id);
    }

    @PostMapping("/{id}/tracks")
    @Operation(summary = "Dołożenie utworu na koniec setu")
    public PlaylistResponse addTrack(@PathVariable Long id,
                                     @Valid @RequestBody AddPlaylistTrackRequest request) {

        return mapper.toResponse(playlistService.addTrack(id, request.spotifyId()));
    }

    @DeleteMapping("/{id}/tracks/{spotifyId}")
    @Operation(summary = "Usunięcie utworu z setu (pozostałe pozycje przenumerowane)")
    public PlaylistResponse removeTrack(@PathVariable Long id, @PathVariable String spotifyId) {
        return mapper.toResponse(playlistService.removeTrack(id, spotifyId));
    }

    @PutMapping("/{id}/tracks")
    @Operation(summary = "Nowa kolejność setu (drag&drop)",
        description = "Lista musi być permutacją obecnego składu playlisty.")
    public PlaylistResponse reorder(@PathVariable Long id,
                                    @Valid @RequestBody ReorderPlaylistRequest request) {

        return mapper.toResponse(
            playlistService.reorder(id, request.spotifyIds(), request.version()));
    }

    @PostMapping("/{id}/export-to-spotify")
    @Operation(summary = "Eksport setu na konto Spotify",
        description = "Pierwszy eksport zakłada prywatną playlistę na koncie właściciela, "
            + "kolejne nadpisują jej zawartość — kolejność na Spotify odpowiada setowi. "
            + "Wymaga połączonego konta (GET /api/auth/spotify/login).")
    public PlaylistExportResponse exportToSpotify(@PathVariable Long id) {
        return mapper.toResponse(playlistExportService.export(id));
    }
}
