package com.pgoogol.api;

import java.util.List;

/**
 * Raport z importu playlisty — kontrakt {@code POST /api/ingest/playlist}
 * i {@code POST /api/ingest/my-playlists}.
 */
public record IngestPlaylistResponse(
    Long playlistId,
    String spotifyPlaylistId,
    String name,
    int tracks,
    int imported,
    int alreadyExisted,
    List<SkippedItemResponse> skipped) {

    public record SkippedItemResponse(int position, String reason) {

    }
}
