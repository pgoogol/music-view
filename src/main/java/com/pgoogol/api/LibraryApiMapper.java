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
            scale(overview.scale()),
            quality(overview.quality()),
            sound(overview.sound()),
            timeline(overview.timeline()),
            taste(overview.taste()),
            overview.recentlyAdded().stream()
                .map(track -> new LibraryOverviewResponse.RecentTrackResponse(
                    track.spotifyId(), track.title(), track.artist(),
                    track.albumImageUrl(), track.addedAt()))
                .toList());
    }

    private LibraryOverviewResponse.ScaleResponse scale(LibraryOverview.Scale scale) {

        return new LibraryOverviewResponse.ScaleResponse(
            scale.catalogTracks(),
            scale.libraryTracks(),
            scale.tracksWithMetrics(),
            scale.libraryDurationMs(),
            scale.distinctArtists(),
            scale.distinctAlbums(),
            scale.averageBpm(),
            scale.averageDurationMs(),
            scale.averagePopularity());
    }

    private LibraryOverviewResponse.QualityResponse quality(LibraryOverview.Quality quality) {

        return new LibraryOverviewResponse.QualityResponse(
            quality.metadataMissing(),
            quality.audioMissing(),
            quality.aiMissing(),
            buckets(quality.bpmSources()),
            buckets(quality.confidences()));
    }

    private LibraryOverviewResponse.SoundResponse sound(LibraryOverview.Sound sound) {

        return new LibraryOverviewResponse.SoundResponse(
            buckets(sound.genres()),
            buckets(sound.styles()),
            buckets(sound.tempoClasses()),
            buckets(sound.energies()),
            buckets(sound.bpmHistogram()),
            buckets(sound.camelotKeys()),
            buckets(sound.durations()),
            buckets(sound.popularity()),
            sound.tempoEnergy().stream()
                .map(cell -> new LibraryOverviewResponse.MatrixCellResponse(
                    cell.tempoClass(), cell.energy(), cell.count()))
                .toList(),
            sound.audioProfile().stream()
                .map(metric -> new LibraryOverviewResponse.MetricResponse(
                    metric.label(), metric.value()))
                .toList());
    }

    private LibraryOverviewResponse.TimelineResponse timeline(LibraryOverview.Timeline timeline) {

        return new LibraryOverviewResponse.TimelineResponse(
            buckets(timeline.monthlyGrowth()),
            buckets(timeline.decades()));
    }

    private LibraryOverviewResponse.TasteResponse taste(LibraryOverview.Taste taste) {

        return new LibraryOverviewResponse.TasteResponse(
            buckets(taste.topArtists()),
            buckets(taste.topAlbums()),
            buckets(taste.topTags()),
            buckets(taste.ratings()));
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
