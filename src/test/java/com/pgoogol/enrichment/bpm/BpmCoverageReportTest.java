package com.pgoogol.enrichment.bpm;

import com.pgoogol.catalog.AudioFeatures;
import com.pgoogol.catalog.AudioFeaturesRepository;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.ManualMetricsRepository;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.enrichment.deezer.DeezerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * Raport pokrycia dla próbki 60 utworów (DoD M1.4: ≥50): 25 z AcousticBrainz,
 * 20 z Deezera, 15 bez BPM (zostają dla AI).
 */
@ExtendWith(MockitoExtension.class)
class BpmCoverageReportTest {

    private static final int WITH_ACOUSTICBRAINZ = 25;
    private static final int WITH_DEEZER = 20;
    private static final int WITHOUT_BPM = 15;

    @Mock
    private AudioFeaturesRepository audioFeaturesRepository;

    @Mock
    private ManualMetricsRepository manualMetricsRepository;

    @Mock
    private DeezerClient deezerClient;

    private BpmResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new BpmResolver(manualMetricsRepository, audioFeaturesRepository,
            deezerClient, new HalfTimeCorrector());
    }

    @Test
    void coverage_whenSampleOfSixtyTracks_reportsCountsPerSource() {

        // given
        List<TrackCatalog> sample = IntStream.range(0, WITH_ACOUSTICBRAINZ + WITH_DEEZER + WITHOUT_BPM)
            .mapToObj(this::sampleTrack)
            .toList();
        given(audioFeaturesRepository.findByTrackSpotifyId(anyString())).willAnswer(invocation ->
            acousticBrainzFeaturesFor(invocation.getArgument(0)));
        given(deezerClient.findBpmByIsrc(anyString())).willAnswer(invocation ->
            deezerBpmFor(invocation.getArgument(0)));
        given(deezerClient.findBpmByArtistTitle(anyString(), anyString()))
            .willReturn(Optional.empty());

        // when
        BpmCoverageReport report = resolver.coverage(sample);

        // then
        assertThat(report).isEqualTo(new BpmCoverageReport(
            0, WITH_ACOUSTICBRAINZ, WITH_DEEZER, WITHOUT_BPM));
        assertThat(report.total()).isEqualTo(sample.size());
    }

    private TrackCatalog sampleTrack(int index) {

        TrackCatalog track = new TrackCatalog("sp-%03d".formatted(index),
            "Utwór %d".formatted(index), "Wykonawca %d".formatted(index));
        track.setIsrc("ISRC%08d".formatted(index));
        track.setGenreFamily(GenreFamily.OTHER);
        return track;
    }

    private Optional<AudioFeatures> acousticBrainzFeaturesFor(String spotifyId) {

        if (indexOf(spotifyId) >= WITH_ACOUSTICBRAINZ) {
            return Optional.empty();
        }
        AudioFeatures features = new AudioFeatures("mbid-" + spotifyId,
            new TrackCatalog(spotifyId, "t", "a"));
        features.setBpm(new BigDecimal("120"));
        return Optional.of(features);
    }

    private Optional<BigDecimal> deezerBpmFor(String isrc) {

        int index = Integer.parseInt(isrc.substring(4));
        boolean coveredByDeezer = index >= WITH_ACOUSTICBRAINZ
            && index < WITH_ACOUSTICBRAINZ + WITH_DEEZER;
        return coveredByDeezer ? Optional.of(new BigDecimal("128")) : Optional.empty();
    }

    private int indexOf(String spotifyId) {
        return Integer.parseInt(spotifyId.substring(3));
    }
}
