package com.pgoogol.enrichment;

import com.pgoogol.enrichment.deezer.DeezerClient;
import com.pgoogol.enrichment.deezer.DeezerProperties;
import com.pgoogol.enrichment.musicbrainz.MusicBrainzClient;
import com.pgoogol.enrichment.musicbrainz.MusicBrainzIsrcCacheRepository;
import com.pgoogol.enrichment.musicbrainz.MusicBrainzProperties;
import com.pgoogol.enrichment.spotify.SpotifyApiExecutor;
import com.pgoogol.enrichment.spotify.SpotifyAppTokenProvider;
import com.pgoogol.enrichment.spotify.SpotifyClient;
import com.pgoogol.enrichment.spotify.SpotifyProperties;
import com.pgoogol.enrichment.spotify.SpotifyTrackMapper;
import com.pgoogol.enrichment.spotify.SpotifyTrackMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Smoke-test manualny na realnych API (DoD M1.3) — domyślnie pomijany.
 * Uruchomienie: {@code MV_SMOKE=true ./mvnw test -Dtest=RealApiSmokeTest}
 * (część Spotify wymaga dodatkowo SPOTIFY_CLIENT_ID/SECRET w środowisku;
 * MusicBrainz — MB_USER_AGENT). Wyniki wypisywane na stdout do ręcznej oceny.
 */
class RealApiSmokeTest {

    /** 5 realnych utworów: latino / pop / rock / EDM / disco polo. */
    private static final Map<String, String> TRACKS = Map.of(
        "USSD11300483", "Marc Anthony — Vivir Mi Vida",
        "USUM71703861", "Luis Fonsi — Despacito",
        "GBARL1600467", "?",
        "USRC11301388", "?",
        "QMFME2130686", "?");

    private static final List<String> SPOTIFY_IDS = List.of(
        "4uLU6hMCjMI75M1A2tKUQC", "6habFhsOp2NvshLv26DqMb", "3ZFTkvIE7kyPt6Nu3PEa7V",
        "2b8fOow8UzyDFAE27YhOZM", "0KKzKGnDWjPdQGcxsHHhCE");

    @Test
    @EnabledIfEnvironmentVariable(named = "MV_SMOKE", matches = "true")
    void deezerAndMusicBrainz_forFiveRealTracks_returnResults() {

        // given
        DeezerClient deezer = new DeezerClient(RestClient.builder(),
            new DeezerProperties("https://api.deezer.com", 5));
        MusicBrainzIsrcCacheRepository noopCache = mock(MusicBrainzIsrcCacheRepository.class);
        given(noopCache.findById(anyString())).willReturn(Optional.empty());
        given(noopCache.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        MusicBrainzClient musicBrainz = new MusicBrainzClient(RestClient.builder(),
            new MusicBrainzProperties("https://musicbrainz.org",
                System.getenv().getOrDefault("MB_USER_AGENT", "music-view-smoke/0.1")),
            noopCache);

        // when + then — oceniamy pokrycie ręcznie, test wymaga min. 1 trafienia per źródło
        long deezerHits = TRACKS.keySet().stream()
            .map(isrc -> {
                Optional<BigDecimal> bpm = deezer.findBpmByIsrc(isrc);
                System.out.printf("Deezer  %s (%s) → bpm=%s%n", isrc, TRACKS.get(isrc), bpm);
                return bpm;
            })
            .filter(Optional::isPresent)
            .count();
        long mbHits = TRACKS.keySet().stream()
            .map(isrc -> {
                Optional<String> mbid = musicBrainz.lookupMbid(isrc);
                System.out.printf("MB      %s (%s) → mbid=%s%n", isrc, TRACKS.get(isrc), mbid);
                return mbid;
            })
            .filter(Optional::isPresent)
            .count();

        assertThat(deezerHits).isPositive();
        assertThat(mbHits).isPositive();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MV_SMOKE", matches = "true")
    @EnabledIfEnvironmentVariable(named = "SPOTIFY_CLIENT_ID", matches = ".+")
    void spotify_forFiveRealTracks_returnsMetadataWithIsrc() {

        // given
        SpotifyProperties properties = new SpotifyProperties(
            "https://api.spotify.com", "https://accounts.spotify.com",
            System.getenv("SPOTIFY_CLIENT_ID"), System.getenv("SPOTIFY_CLIENT_SECRET"), 5,
            "http://127.0.0.1:8080/api/auth/spotify/callback", "playlist-read-private");
        SpotifyClient spotify = new SpotifyClient(RestClient.builder(), properties,
            new SpotifyAppTokenProvider(RestClient.builder(), properties),
            new SpotifyTrackMapper(), new SpotifyApiExecutor(properties));

        // when
        List<SpotifyTrackMetadata> tracks = spotify.getTracks(SPOTIFY_IDS);
        tracks.forEach(track -> System.out.printf("Spotify %s → %s — %s (isrc=%s, rok=%s)%n",
            track.spotifyId(), track.artist(), track.title(), track.isrc(), track.year()));

        // then
        assertThat(tracks).isNotEmpty();
        assertThat(tracks).allSatisfy(track -> assertThat(track.isrc()).isNotBlank());
    }
}
