package com.pgoogol.enrichment.spotify;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import com.pgoogol.WireMockRestClients;

import java.util.List;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class SpotifyClientTest {

    private static final String TOKEN_JSON = """
        {"access_token": "test-token", "token_type": "Bearer", "expires_in": 3600}
        """;

    private static final String TRACKS_JSON = """
        {
          "tracks": [
            {
              "id": "4uLU6hMCjMI75M1A2tKUQC",
              "name": "Vivir Mi Vida",
              "duration_ms": 252306,
              "explicit": false,
              "popularity": 80,
              "artists": [{"name": "Marc Anthony"}],
              "album": {
                "name": "3.0",
                "release_date": "2013-07-22",
                "images": [{"url": "https://i.scdn.co/image/large", "height": 640}]
              },
              "external_ids": {"isrc": "USSD11300483"}
            },
            null
          ]
        }
        """;

    @Test
    void getTracks_whenSpotifyResponds_mapsMetadataAndSkipsUnknownIds(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(post(urlPathEqualTo("/api/token")).willReturn(okJson(TOKEN_JSON)));
        stubFor(get(urlPathEqualTo("/v1/tracks")).willReturn(okJson(TRACKS_JSON)));
        SpotifyClient client = spotifyClient(wireMock);

        // when
        List<SpotifyTrackMetadata> tracks =
            client.getTracks(List.of("4uLU6hMCjMI75M1A2tKUQC", "0000000000000000000000"));

        // then
        assertThat(tracks).singleElement().isEqualTo(new SpotifyTrackMetadata(
            "4uLU6hMCjMI75M1A2tKUQC", "Vivir Mi Vida", "Marc Anthony", "3.0",
            2013, 252306, 80, false, "https://i.scdn.co/image/large", "USSD11300483"));
        verify(getRequestedFor(urlPathEqualTo("/v1/tracks"))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withQueryParam("ids", equalTo("4uLU6hMCjMI75M1A2tKUQC,0000000000000000000000")));
    }

    @Test
    void getTracks_whenMoreThanFiftyIds_splitsIntoBatchesAndReusesToken(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(post(urlPathEqualTo("/api/token")).willReturn(okJson(TOKEN_JSON)));
        stubFor(get(urlPathEqualTo("/v1/tracks")).willReturn(okJson("{\"tracks\": []}")));
        SpotifyClient client = spotifyClient(wireMock);
        List<String> ids = IntStream.range(0, 60)
            .mapToObj("%022d"::formatted)
            .toList();

        // when
        client.getTracks(ids);

        // then
        verify(2, getRequestedFor(urlPathEqualTo("/v1/tracks")));
        verify(1, postRequestedFor(urlPathEqualTo("/api/token")));
        verify(getRequestedFor(urlPathEqualTo("/v1/tracks"))
            .withQueryParam("ids", matching("(\\d{22},){49}\\d{22}")));
    }

    @Test
    void getTracks_whenTokenRequested_usesClientCredentialsWithBasicAuth(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(post(urlPathEqualTo("/api/token")).willReturn(okJson(TOKEN_JSON)));
        stubFor(get(urlPathEqualTo("/v1/tracks")).willReturn(okJson("{\"tracks\": []}")));
        SpotifyClient client = spotifyClient(wireMock);

        // when
        client.getTracks(List.of("4uLU6hMCjMI75M1A2tKUQC"));

        // then
        verify(postRequestedFor(urlPathEqualTo("/api/token"))
            .withBasicAuth(new com.github.tomakehurst.wiremock.client.BasicCredentials(
                SpotifyTestProperties.CLIENT_ID, SpotifyTestProperties.CLIENT_SECRET))
            .withRequestBody(equalTo("grant_type=client_credentials")));
    }

    private SpotifyClient spotifyClient(WireMockRuntimeInfo wireMock) {

        SpotifyProperties properties = SpotifyTestProperties.pointingAt(wireMock);
        return new SpotifyClient(WireMockRestClients.builder(), properties,
            new SpotifyAppTokenProvider(WireMockRestClients.builder(), properties),
            new SpotifyTrackMapper(), new SpotifyApiExecutor(properties));
    }
}
