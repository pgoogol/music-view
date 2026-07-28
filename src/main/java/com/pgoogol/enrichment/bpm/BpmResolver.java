package com.pgoogol.enrichment.bpm;

import com.pgoogol.catalog.AudioFeatures;
import com.pgoogol.catalog.AudioFeaturesRepository;
import com.pgoogol.catalog.BpmSource;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.enrichment.deezer.DeezerClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Kaskada BPM (D6): {@code audio_features} (AcousticBrainz) → Deezer
 * (ISRC, potem artist+title) → brak (pole zostaje dla AI, M1.5/M1.6).
 * Sanity-check half-time (§16.1) stosowany do każdego źródła: latin + BPM &lt; 100
 * → podwojenie, o ile wynik pozostaje wiarygodny taneczne. Resolver nie zapisuje
 * do bazy — persystencja należy do writera joba wzbogacania (M1.6).
 */
@Component
public class BpmResolver {

    private final AudioFeaturesRepository audioFeaturesRepository;
    private final DeezerClient deezerClient;
    private final HalfTimeCorrector halfTimeCorrector;

    public BpmResolver(AudioFeaturesRepository audioFeaturesRepository, DeezerClient deezerClient,
                       HalfTimeCorrector halfTimeCorrector) {

        this.audioFeaturesRepository = audioFeaturesRepository;
        this.deezerClient = deezerClient;
        this.halfTimeCorrector = halfTimeCorrector;
    }

    public Optional<BpmResolution> resolve(TrackCatalog track) {

        Objects.requireNonNull(track, "track");
        return fromAcousticBrainz(track)
            .or(() -> fromDeezer(track))
            .map(resolution -> withHalfTimeCorrection(track, resolution));
    }

    /** Raport pokrycia per źródło dla próbki utworów (DoD M1.4). */
    public BpmCoverageReport coverage(List<TrackCatalog> tracks) {

        Objects.requireNonNull(tracks, "tracks");
        Map<BpmSource, Long> counts = tracks.stream()
            .map(this::resolve)
            .flatMap(Optional::stream)
            .collect(Collectors.groupingBy(BpmResolution::source, Collectors.counting()));
        long resolved = counts.values().stream().mapToLong(Long::longValue).sum();
        return new BpmCoverageReport(
            counts.getOrDefault(BpmSource.ACOUSTICBRAINZ, 0L).intValue(),
            counts.getOrDefault(BpmSource.DEEZER, 0L).intValue(),
            tracks.size() - (int) resolved);
    }

    private Optional<BpmResolution> fromAcousticBrainz(TrackCatalog track) {

        return audioFeaturesRepository.findByTrackSpotifyId(track.getSpotifyId())
            .map(AudioFeatures::getBpm)
            .map(bpm -> new BpmResolution(round(bpm), BpmSource.ACOUSTICBRAINZ));
    }

    private Optional<BpmResolution> fromDeezer(TrackCatalog track) {

        return Optional.ofNullable(track.getIsrc())
            .flatMap(deezerClient::findBpmByIsrc)
            .or(() -> searchFallback(track))
            .map(bpm -> new BpmResolution(round(bpm), BpmSource.DEEZER));
    }

    private Optional<BigDecimal> searchFallback(TrackCatalog track) {

        if (Objects.isNull(track.getArtist()) || Objects.isNull(track.getTitle())) {
            return Optional.empty();
        }
        return deezerClient.findBpmByArtistTitle(track.getArtist(), track.getTitle());
    }

    private BpmResolution withHalfTimeCorrection(TrackCatalog track, BpmResolution resolution) {

        int corrected = halfTimeCorrector.correct(track.getGenreFamily(), resolution.bpm());
        return corrected == resolution.bpm()
            ? resolution
            : new BpmResolution(corrected, resolution.source());
    }

    private int round(BigDecimal bpm) {
        return bpm.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }
}
