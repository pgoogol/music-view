package com.pgoogol.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Zlecenia zmian playlisty — kontrakty {@code /api/playlists*}.
 */
public final class PlaylistRequests {

    private PlaylistRequests() {

    }

    public record SavePlaylistRequest(@NotBlank @Size(max = 255) String name) {

    }

    public record AddPlaylistTrackRequest(@NotBlank @Size(max = 64) String spotifyId) {

    }

    public record ReorderPlaylistRequest(@NotEmpty List<@NotBlank String> spotifyIds) {

    }
}
