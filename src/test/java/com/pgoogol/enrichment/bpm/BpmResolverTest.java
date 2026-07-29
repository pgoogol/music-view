package com.pgoogol.enrichment.bpm;

import com.pgoogol.catalog.AudioFeatures;
import com.pgoogol.catalog.AudioFeaturesRepository;
import com.pgoogol.catalog.BpmSource;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.ManualMetrics;
import com.pgoogol.catalog.ManualMetricsRepository;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogFixtures;
import com.pgoogol.enrichment.deezer.DeezerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BpmResolverTest {

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
    void resolve_whenManualMetricsPresent_winOverRemainingSources() {

        // given — plik wgrany ręcznie (D24) jest pierwszy w kaskadzie
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack("sp-9");
        track.setGenreFamily(GenreFamily.POP);
        ManualMetrics metrics = new ManualMetrics(track);
        metrics.setBpm(new BigDecimal("128.4"));
        given(manualMetricsRepository.findById("sp-9")).willReturn(Optional.of(metrics));

        // when
        Optional<BpmResolution> resolution = resolver.resolve(track);

        // then
        assertThat(resolution).contains(new BpmResolution(128, BpmSource.MANUAL));
        then(audioFeaturesRepository).shouldHaveNoInteractions();
        then(deezerClient).shouldHaveNoInteractions();
    }

    @Test
    void resolve_whenAudioFeaturesPresent_returnsBpmFromAcousticBrainz() {

        // given
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack("sp-1");
        track.setGenreFamily(GenreFamily.POP);
        givenAcousticBrainzBpm(track, "104.60");

        // when
        Optional<BpmResolution> resolution = resolver.resolve(track);

        // then
        assertThat(resolution).contains(new BpmResolution(105, BpmSource.ACOUSTICBRAINZ));
        then(deezerClient).shouldHaveNoInteractions();
    }

    @Test
    void resolve_whenNoAudioFeatures_usesDeezerByIsrc() {

        // given
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack("sp-1");
        track.setGenreFamily(GenreFamily.POP);
        given(audioFeaturesRepository.findByTrackSpotifyId("sp-1")).willReturn(Optional.empty());
        given(deezerClient.findBpmByIsrc(track.getIsrc())).willReturn(Optional.of(new BigDecimal("128")));

        // when
        Optional<BpmResolution> resolution = resolver.resolve(track);

        // then
        assertThat(resolution).contains(new BpmResolution(128, BpmSource.DEEZER));
    }

    @Test
    void resolve_whenDeezerIsrcMisses_fallsBackToArtistTitleSearch() {

        // given
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack("sp-1");
        track.setGenreFamily(GenreFamily.POP);
        given(audioFeaturesRepository.findByTrackSpotifyId("sp-1")).willReturn(Optional.empty());
        given(deezerClient.findBpmByIsrc(track.getIsrc())).willReturn(Optional.empty());
        given(deezerClient.findBpmByArtistTitle(track.getArtist(), track.getTitle()))
            .willReturn(Optional.of(new BigDecimal("128")));

        // when
        Optional<BpmResolution> resolution = resolver.resolve(track);

        // then
        assertThat(resolution).contains(new BpmResolution(128, BpmSource.DEEZER));
    }

    @Test
    void resolve_whenTrackWithoutIsrc_skipsIsrcLookupAndSearchesByArtistTitle() {

        // given
        TrackCatalog track = TrackCatalogFixtures.skeletonTrack("sp-1");
        given(audioFeaturesRepository.findByTrackSpotifyId("sp-1")).willReturn(Optional.empty());
        given(deezerClient.findBpmByArtistTitle(track.getArtist(), track.getTitle()))
            .willReturn(Optional.empty());

        // when
        Optional<BpmResolution> resolution = resolver.resolve(track);

        // then
        assertThat(resolution).isEmpty();
        then(deezerClient).should(never()).findBpmByIsrc(anyString());
    }

    @Test
    void resolve_whenAllSourcesEmpty_returnsEmptyForAi() {

        // given
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack("sp-1");
        given(audioFeaturesRepository.findByTrackSpotifyId("sp-1")).willReturn(Optional.empty());
        given(deezerClient.findBpmByIsrc(anyString())).willReturn(Optional.empty());
        given(deezerClient.findBpmByArtistTitle(anyString(), anyString())).willReturn(Optional.empty());

        // when
        Optional<BpmResolution> resolution = resolver.resolve(track);

        // then
        assertThat(resolution).isEmpty();
    }

    @Test
    void resolve_whenLatinTrackWithHalfTimeBpm_doublesBpm() {

        // given — salsa zmierzona half-time: 92 → 184 (§16.1)
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack("sp-1");
        givenAcousticBrainzBpm(track, "92.00");

        // when
        Optional<BpmResolution> resolution = resolver.resolve(track);

        // then
        assertThat(resolution).contains(new BpmResolution(184, BpmSource.ACOUSTICBRAINZ));
    }

    @Test
    void resolve_whenLatinHalfTimeBpmFromDeezer_alsoDoublesBpm() {

        // given — korekta half-time dotyczy każdego źródła (D6)
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack("sp-1");
        given(audioFeaturesRepository.findByTrackSpotifyId("sp-1")).willReturn(Optional.empty());
        given(deezerClient.findBpmByIsrc(track.getIsrc())).willReturn(Optional.of(new BigDecimal("95")));

        // when
        Optional<BpmResolution> resolution = resolver.resolve(track);

        // then
        assertThat(resolution).contains(new BpmResolution(190, BpmSource.DEEZER));
    }

    @Test
    void resolve_whenNonLatinTrackWithLowBpm_keepsOriginalBpm() {

        // given
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack("sp-1");
        track.setGenreFamily(GenreFamily.POP);
        givenAcousticBrainzBpm(track, "92.00");

        // when
        Optional<BpmResolution> resolution = resolver.resolve(track);

        // then
        assertThat(resolution).contains(new BpmResolution(92, BpmSource.ACOUSTICBRAINZ));
    }

    @Test
    void resolve_whenLatinTrackWithFastBpm_keepsOriginalBpm() {

        // given — 160 BPM to już pełne tempo, nie half-time
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack("sp-1");
        givenAcousticBrainzBpm(track, "160.00");

        // when
        Optional<BpmResolution> resolution = resolver.resolve(track);

        // then
        assertThat(resolution).contains(new BpmResolution(160, BpmSource.ACOUSTICBRAINZ));
    }

    private void givenAcousticBrainzBpm(TrackCatalog track, String bpm) {

        AudioFeatures features = new AudioFeatures("mbid-test", track);
        features.setBpm(new BigDecimal(bpm));
        given(audioFeaturesRepository.findByTrackSpotifyId(track.getSpotifyId()))
            .willReturn(Optional.of(features));
    }
}
