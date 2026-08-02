package com.pgoogol.api;

import com.pgoogol.common.ValidationException;
import com.pgoogol.library.LibraryEntry;
import com.pgoogol.library.LibraryEntryUpdate;
import com.pgoogol.library.LibraryOverview;
import com.pgoogol.playlist.DjSlot;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class LibraryApiMapper {

    public LibraryOverviewResponse toResponse(LibraryOverview overview) {

        return new LibraryOverviewResponse(
            overview.catalogTracks(),
            overview.libraryTracks(),
            overview.tracksWithMetrics(),
            overview.metadataMissing(),
            overview.audioMissing(),
            overview.aiMissing(),
            buckets(overview.genres()),
            buckets(overview.tempoClasses()),
            buckets(overview.energies()),
            buckets(overview.bpmSources()),
            buckets(overview.ratings()),
            buckets(overview.bpmHistogram()),
            buckets(overview.topArtists()),
            buckets(overview.monthlyGrowth()));
    }

    private List<LibraryOverviewResponse.BucketResponse> buckets(List<LibraryOverview.Bucket> source) {

        return source.stream()
            .map(bucket -> new LibraryOverviewResponse.BucketResponse(
                bucket.label(), bucket.count()))
            .toList();
    }

    private final CatalogApiMapper catalogApiMapper;

    public LibraryApiMapper(CatalogApiMapper catalogApiMapper) {
        this.catalogApiMapper = catalogApiMapper;
    }

    public LibraryEntryResponse toResponse(LibraryEntry entry) {

        return new LibraryEntryResponse(
            entry.getId(),
            entry.getTrack().getSpotifyId(),
            Objects.toString(entry.getSource(), null),
            entry.getAddedAt(),
            entry.getDjNotes(),
            entry.getCustomTags(),
            entry.getRating(),
            entry.getDjSlotOverride(),
            entry.getVersion(),
            catalogApiMapper.toResponse(entry.getTrack()));
    }

    public LibraryEntryUpdate toUpdate(UpdateLibraryEntryRequest request) {

        return new LibraryEntryUpdate(
            request.djNotes(),
            request.customTags(),
            request.rating(),
            canonicalSlotOverride(request.djSlotOverride()),
            request.version());
    }

    /** Override slotu musi być jedną z wartości {@link DjSlot}; pusty = wyczyszczenie. */
    private String canonicalSlotOverride(String djSlotOverride) {

        if (Objects.isNull(djSlotOverride) || djSlotOverride.isBlank()) {
            return djSlotOverride;
        }
        return DjSlot.parse(djSlotOverride)
            .map(DjSlot::name)
            .orElseThrow(() -> new ValidationException("DJ_SLOT_INVALID",
                "Nieznany slot '%s' — dopuszczalne: %s".formatted(djSlotOverride,
                    Arrays.stream(DjSlot.values()).map(DjSlot::name)
                        .collect(Collectors.joining(", ")))));
    }
}
