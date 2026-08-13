package com.pgoogol.api;

import com.pgoogol.catalog.CamelotKey;
import com.pgoogol.catalog.ManualMetrics;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.library.LibraryEntry;
import com.pgoogol.library.LibraryRow;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class CatalogApiMapper {

    public TrackMetricsResponse toResponse(ManualMetrics metrics) {

        return new TrackMetricsResponse(
            metrics.getSpotifyId(),
            metrics.getBpm(),
            metrics.getMusicalKey(),
            metrics.getCamelot(),
            metrics.getDanceability(),
            metrics.getEnergy(),
            metrics.getValence(),
            metrics.getAcousticness(),
            metrics.getInstrumentalness(),
            metrics.getSpeechiness(),
            metrics.getLiveness(),
            metrics.getLoudnessDb(),
            metrics.getTimeSignature(),
            metrics.getSource(),
            metrics.getImportedAt());
    }

    /** Wiersz ekranu Biblioteka — katalog plus dane DJ-a, o ile utwór jest u niego (D3). */
    public CatalogRowResponse toResponse(LibraryRow row) {

        return new CatalogRowResponse(
            toResponse(row.track()),
            row.libraryEntry().map(this::toLibraryResponse).orElse(null));
    }

    private CatalogRowResponse.TrackLibraryResponse toLibraryResponse(LibraryEntry entry) {

        return new CatalogRowResponse.TrackLibraryResponse(
            entry.getRating(),
            entry.getCustomTags(),
            entry.getAddedAt(),
            Objects.toString(entry.getSource(), null));
    }

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
            CamelotKey.ofMusicalKey(track.getMusicalKey()).map(CamelotKey::label).orElse(null),
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
