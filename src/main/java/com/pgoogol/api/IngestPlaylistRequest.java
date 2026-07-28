package com.pgoogol.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Zlecenie importu playlisty — {@code POST /api/ingest/playlist}.
 */
public record IngestPlaylistRequest(
    @NotBlank
    @Size(max = 500)
    @Schema(description = "Link, URI spotify:playlist:… lub samo id playlisty",
        example = "https://open.spotify.com/playlist/37i9dQZF1DX10zKzsJ2jva")
    String url) {

}
