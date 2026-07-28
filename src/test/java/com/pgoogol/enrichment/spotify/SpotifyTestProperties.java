package com.pgoogol.enrichment.spotify;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;

/**
 * Konfiguracja klientów Spotify wycelowana w WireMocka (Object Mother) —
 * jeden punkt zmiany, gdy {@link SpotifyProperties} rośnie.
 */
public final class SpotifyTestProperties {

    public static final String CLIENT_ID = "test-client-id";
    public static final String CLIENT_SECRET = "test-client-secret";

    private SpotifyTestProperties() {

    }

    public static SpotifyProperties pointingAt(WireMockRuntimeInfo wireMock) {

        return new SpotifyProperties(wireMock.getHttpBaseUrl(), wireMock.getHttpBaseUrl(),
            CLIENT_ID, CLIENT_SECRET, 100);
    }
}
