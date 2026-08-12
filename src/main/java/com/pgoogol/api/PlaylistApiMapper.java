package com.pgoogol.api;

import com.pgoogol.playlist.PlannedTrack;
import com.pgoogol.playlist.Playlist;
import com.pgoogol.playlist.PlaylistExport;
import com.pgoogol.playlist.PlaylistPlan;
import com.pgoogol.playlist.PlaylistSummary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class PlaylistApiMapper {

    private final CatalogApiMapper catalogApiMapper;

    public PlaylistApiMapper(CatalogApiMapper catalogApiMapper) {
        this.catalogApiMapper = catalogApiMapper;
    }

    public PlaylistExportResponse toResponse(PlaylistExport export) {

        return new PlaylistExportResponse(export.playlistId(), export.spotifyPlaylistId(),
            export.name(), export.exportedTracks(), export.created(), export.spotifyUrl());
    }

    public PlaylistSummaryResponse toResponse(PlaylistSummary summary) {

        return new PlaylistSummaryResponse(summary.id(), summary.name(),
            summary.spotifyPlaylistId(), summary.createdAt(), summary.trackCount(),
            summary.version());
    }

    public PlaylistResponse toResponse(PlaylistPlan plan) {

        Playlist playlist = plan.playlist();
        List<PlaylistResponse.PlaylistTrackResponse> tracks = plan.tracks().stream()
            .map(this::toResponse)
            .toList();
        return new PlaylistResponse(playlist.getId(), playlist.getName(),
            playlist.getSpotifyPlaylistId(), playlist.getCreatedAt(), playlist.getVersion(),
            tracks);
    }

    private PlaylistResponse.PlaylistTrackResponse toResponse(PlannedTrack plannedTrack) {

        return new PlaylistResponse.PlaylistTrackResponse(
            plannedTrack.position(),
            Objects.toString(plannedTrack.djSlot(), null),
            plannedTrack.djSlotOverride(),
            Optional.ofNullable(plannedTrack.metrics())
                .map(catalogApiMapper::toResponse)
                .orElse(null),
            catalogApiMapper.toResponse(plannedTrack.track()));
    }
}
