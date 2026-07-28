package com.pgoogol.enrichment.spotify;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.pgoogol.WireMockRestClients;
import com.pgoogol.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@WireMockTest
@ExtendWith(MockitoExtension.class)
class SpotifyPlaylistClientTest {

    private static final String PLAYLIST_ID = "37i9dQZF1DX10zKzsJ2jva";
    private static final String TOKEN_JSON = """
        {"access_token": "test-token", "token_type": "Bearer", "expires_in": 3600}
        """;

    private static final String PLAYLIST_JSON = """
        {
          "id": "37i9dQZF1DX10zKzsJ2jva",
          "name": "Sabor Latino",
          "owner": {"id": "dj-pgoogol", "display_name": "DJ pgoogol"},
          "tracks": {"total": 2}
        }
        """;

    private static final String ITEMS_JSON = """
        {
          "total": 4,
          "items": [
            {
              "track": {
                "id": "4uLU6hMCjMI75M1A2tKUQC",
                "name": "Vivir Mi Vida",
                "type": "track",
                "is_local": false,
                "duration_ms": 252306,
                "explicit": false,
                "popularity": 80,
                "artists": [{"name": "Marc Anthony"}],
                "album": {
                  "name": "3.0",
                  "release_date": "2013-07-22",
                  "images": [{"url": "https://i.scdn.co/image/large"}]
                },
                "external_ids": {"isrc": "USSD11300483"}
              }
            },
            {"track": null},
            {
              "track": {
                "id": null,
                "name": "Nagranie z wesela.mp3",
                "type": "track",
                "is_local": true,
                "artists": [{"name": "nieznany"}]
              }
            },
            {
              "track": {
                "id": "5xyzEpisode000000000000",
                "name": "Podcast o salsie",
                "type": "episode",
                "is_local": false
              }
            }
          ]
        }
        """;

    @Mock
    private SpotifyAccountService accountService;

    @Test
    void getPlaylist_whenPlaylistExists_mapsHeaderWithOwner(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(post(urlPathEqualTo("/api/token")).willReturn(okJson(TOKEN_JSON)));
        stubFor(get(urlPathEqualTo("/v1/playlists/" + PLAYLIST_ID)).willReturn(okJson(PLAYLIST_JSON)));

        // when
        SpotifyPlaylist playlist = playlistClient(wireMock).getPlaylist(PLAYLIST_ID);

        // then
        assertThat(playlist).isEqualTo(new SpotifyPlaylist(
            PLAYLIST_ID, "Sabor Latino", "dj-pgoogol", "DJ pgoogol", 2));
        verify(getRequestedFor(urlPathEqualTo("/v1/playlists/" + PLAYLIST_ID))
            .withHeader("Authorization", equalTo("Bearer test-token")));
    }

    @Test
    void getPlaylist_whenPlaylistUnknown_throwsNotFoundWithoutRetry(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(post(urlPathEqualTo("/api/token")).willReturn(okJson(TOKEN_JSON)));
        stubFor(get(urlPathEqualTo("/v1/playlists/nieistnieje")).willReturn(notFound()));
        SpotifyPlaylistClient client = playlistClient(wireMock);

        // when + then
        assertThatThrownBy(() -> client.getPlaylist("nieistnieje"))
            .isInstanceOf(NotFoundException.class)
            .hasFieldOrPropertyWithValue("errorCode", "SPOTIFY_RESOURCE_NOT_FOUND");
        verify(1, getRequestedFor(urlPathEqualTo("/v1/playlists/nieistnieje")));
    }

    @Test
    void getPlaylistItems_whenPlaylistHasUnplayableEntries_mapsTracksAndMarksTheRest(
            WireMockRuntimeInfo wireMock) {

        // given
        stubFor(post(urlPathEqualTo("/api/token")).willReturn(okJson(TOKEN_JSON)));
        stubFor(get(urlPathEqualTo("/v1/playlists/%s/tracks".formatted(PLAYLIST_ID)))
            .willReturn(okJson(ITEMS_JSON)));

        // when
        List<SpotifyPlaylistItem> items = playlistClient(wireMock).getPlaylistItems(PLAYLIST_ID);

        // then
        assertThat(items).hasSize(4);
        assertThat(items.get(0)).isEqualTo(new SpotifyPlaylistItem.Track(0,
            new SpotifyTrackMetadata("4uLU6hMCjMI75M1A2tKUQC", "Vivir Mi Vida", "Marc Anthony",
                "3.0", 2013, 252306, 80, false, "https://i.scdn.co/image/large", "USSD11300483")));
        assertThat(items.subList(1, 4))
            .allSatisfy(item -> assertThat(item).isInstanceOf(SpotifyPlaylistItem.Unavailable.class))
            .extracting(SpotifyPlaylistItem::position)
            .containsExactly(1, 2, 3);
        assertThat(items.get(3)).isInstanceOfSatisfying(SpotifyPlaylistItem.Unavailable.class,
            item -> assertThat(item.reason()).contains("episode"));
    }

    @Test
    void getPlaylistItems_whenPlaylistLongerThanOnePage_followsOffsetPagination(
            WireMockRuntimeInfo wireMock) {

        // given — 250 utworów = 3 strony po 100
        stubFor(post(urlPathEqualTo("/api/token")).willReturn(okJson(TOKEN_JSON)));
        String tracksPath = "/v1/playlists/%s/tracks".formatted(PLAYLIST_ID);
        stubFor(get(urlPathEqualTo(tracksPath)).willReturn(okJson(itemsPage(0, 100, 250))));
        stubFor(get(urlPathEqualTo(tracksPath)).withQueryParam("offset", equalTo("100"))
            .willReturn(okJson(itemsPage(100, 100, 250))));
        stubFor(get(urlPathEqualTo(tracksPath)).withQueryParam("offset", equalTo("200"))
            .willReturn(okJson(itemsPage(200, 50, 250))));

        // when
        List<SpotifyPlaylistItem> items = playlistClient(wireMock).getPlaylistItems(PLAYLIST_ID);

        // then
        assertThat(items).hasSize(250);
        assertThat(items).extracting(SpotifyPlaylistItem::position)
            .containsExactlyElementsOf(IntStream.range(0, 250).boxed().toList());
        assertThat(items.getLast()).isInstanceOfSatisfying(SpotifyPlaylistItem.Track.class,
            track -> assertThat(track.metadata().spotifyId()).isEqualTo("trk-000000000000000249"));
        verify(3, getRequestedFor(urlPathEqualTo(tracksPath)));
        verify(getRequestedFor(urlPathEqualTo(tracksPath)).withQueryParam("limit", equalTo("100")));
    }

    @Test
    void getMyPlaylists_whenAccountConnected_usesUserTokenAndPagesThroughAll(
            WireMockRuntimeInfo wireMock) {

        // given — 60 playlist = 2 strony po 50; prywatne widać tylko na tokenie właściciela
        given(accountService.userAccessToken()).willReturn("user-token");
        stubFor(get(urlPathEqualTo("/v1/me/playlists"))
            .willReturn(okJson(myPlaylistsPage(0, 50, 60))));
        stubFor(get(urlPathEqualTo("/v1/me/playlists")).withQueryParam("offset", equalTo("50"))
            .willReturn(okJson(myPlaylistsPage(50, 10, 60))));

        // when
        List<SpotifyPlaylist> playlists = playlistClient(wireMock).getMyPlaylists();

        // then
        assertThat(playlists).hasSize(60);
        assertThat(playlists.getFirst()).isEqualTo(
            new SpotifyPlaylist("pl-000", "Playlista 0", "dj-pgoogol", "DJ pgoogol", 7));
        verify(2, getRequestedFor(urlPathEqualTo("/v1/me/playlists"))
            .withHeader("Authorization", equalTo("Bearer user-token")));
    }

    private String myPlaylistsPage(int offset, int size, int total) {

        String items = IntStream.range(offset, offset + size)
            .mapToObj(index -> """
                {"id": "pl-%03d", "name": "Playlista %d",
                 "owner": {"id": "dj-pgoogol", "display_name": "DJ pgoogol"},
                 "tracks": {"total": 7}}"""
                .formatted(index, index))
            .collect(Collectors.joining(",\n"));
        return "{\"total\": %d, \"items\": [%s]}".formatted(total, items);
    }

    private String itemsPage(int offset, int size, int total) {

        String items = IntStream.range(offset, offset + size)
            .mapToObj(index -> """
                {"track": {"id": "trk-%018d", "name": "Utwór %d", "type": "track",
                 "artists": [{"name": "Wykonawca"}], "album": {"name": "Album"}}}"""
                .formatted(index, index))
            .collect(Collectors.joining(",\n"));
        return "{\"total\": %d, \"items\": [%s]}".formatted(total, items);
    }

    private SpotifyPlaylistClient playlistClient(WireMockRuntimeInfo wireMock) {

        SpotifyProperties properties = SpotifyTestProperties.pointingAt(wireMock);
        return new SpotifyPlaylistClient(WireMockRestClients.builder(), properties,
            new SpotifyAppTokenProvider(WireMockRestClients.builder(), properties),
            accountService, new SpotifyTrackMapper(), new SpotifyApiExecutor(properties));
    }
}
