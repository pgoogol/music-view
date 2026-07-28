package com.pgoogol.api;

import com.pgoogol.catalog.TrackCatalog;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class CatalogApiMapper {

    public TrackResponse toResponse(TrackCatalog track) {

        return new TrackResponse(
            track.getSpotifyId(),
            track.getTitle(),
            track.getArtist(),
            track.getAlbum(),
            track.getYear(),
            track.getDurationMs(),
            track.getPopularity(),
            track.getExplicit(),
            track.getAlbumImageUrl(),
            track.getIsrc(),
            Objects.toString(track.getGenreFamily(), null),
            track.getStyle(),
            track.getBpm(),
            Objects.toString(track.getBpmSource(), null),
            track.getDanceability(),
            track.getMusicalKey(),
            Objects.toString(track.getTempoClass(), null),
            track.getEnergy(),
            track.getLyricsTheme(),
            track.getDescriptionPl(),
            track.getConfidence(),
            track.getEnrichedAt(),
            track.getModelUsed(),
            track.getEnrichVersion());
    }
}
