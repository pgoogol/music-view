package com.pgoogol.enrichment.lyrics;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.pgoogol.WireMockRestClients;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Klient LRCLIB (D32) na nagranych odpowiedziach — teksty w stubach są
 * wymyślone na potrzeby testu, żeby repozytorium nie przechowywało cudzych
 * utworów.
 */
@WireMockTest
class LrcLibClientTest {

    private static final String FAKE_LYRICS = "Pierwszy wers testowego tekstu\\nDrugi wers testowego tekstu";

    @Test
    void find_whenExactMatchExists_returnsPlainLyrics(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(get(urlPathEqualTo("/api/get")).willReturn(okJson("""
            {"id": 3396226, "instrumental": false, "plainLyrics": "%s", "syncedLyrics": null}
            """.formatted(FAKE_LYRICS))));
        LrcLibClient client = lrcLibClient(wireMock);

        // when
        Optional<LrcLibLyrics> lyrics = client.find("Marc Anthony", "Vivir Mi Vida", "3.0", 252306);

        // then
        assertThat(lyrics).hasValueSatisfying(found -> {
            assertThat(found.lrclibId()).isEqualTo(3396226L);
            assertThat(found.instrumental()).isFalse();
            assertThat(found.plainLyrics()).isEqualTo("Pierwszy wers testowego tekstu\nDrugi wers testowego tekstu");
        });
        // czas trwania idzie w sekundach — LRCLIB traktuje go jako warunek dopasowania
        verify(getRequestedFor(urlPathEqualTo("/api/get"))
            .withQueryParam("artist_name", equalTo("Marc Anthony"))
            .withQueryParam("track_name", equalTo("Vivir Mi Vida"))
            .withQueryParam("album_name", equalTo("3.0"))
            .withQueryParam("duration", equalTo("252")));
    }

    @Test
    void find_whenExactMatchMisses_fallsBackToSearch(WireMockRuntimeInfo wireMock) {

        // given — inne wydanie tego samego nagrania ma inny czas trwania, więc
        // dokładne dopasowanie odpada i zostaje wyszukiwanie po wykonawcy i tytule
        stubFor(get(urlPathEqualTo("/api/get")).willReturn(aResponse().withStatus(404)));
        stubFor(get(urlPathEqualTo("/api/search")).willReturn(okJson("""
            [{"id": 77, "instrumental": false, "plainLyrics": "%s", "syncedLyrics": null}]
            """.formatted(FAKE_LYRICS))));
        LrcLibClient client = lrcLibClient(wireMock);

        // when
        Optional<LrcLibLyrics> lyrics = client.find("Celia Cruz", "La Vida Es Un Carnaval", null, 240000);

        // then
        assertThat(lyrics).hasValueSatisfying(found -> assertThat(found.lrclibId()).isEqualTo(77L));
        verify(getRequestedFor(urlPathEqualTo("/api/search"))
            .withQueryParam("track_name", equalTo("La Vida Es Un Carnaval")));
    }

    @Test
    void find_whenRecordingIsInstrumental_returnsInstrumentalAnswer(WireMockRuntimeInfo wireMock) {

        // given — „bez tekstu" to odpowiedź serwisu, nie brak danych
        stubFor(get(urlPathEqualTo("/api/get")).willReturn(okJson("""
            {"id": 12, "instrumental": true, "plainLyrics": null, "syncedLyrics": null}
            """)));
        LrcLibClient client = lrcLibClient(wireMock);

        // when
        Optional<LrcLibLyrics> lyrics = client.find("Nieznany", "Interludium", null, null);

        // then
        assertThat(lyrics).hasValueSatisfying(found -> {
            assertThat(found.instrumental()).isTrue();
            assertThat(found.plainLyrics()).isNull();
        });
    }

    @Test
    void find_whenOnlySyncedLyrics_stripsTimestamps(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(get(urlPathEqualTo("/api/get")).willReturn(okJson("""
            {"id": 5, "instrumental": false, "plainLyrics": "",
             "syncedLyrics": "[00:12.34]Pierwszy wers testowego tekstu\\n[00:16.02]Drugi wers testowego tekstu"}
            """)));
        LrcLibClient client = lrcLibClient(wireMock);

        // when
        Optional<LrcLibLyrics> lyrics = client.find("Wykonawca", "Utwór", null, null);

        // then
        assertThat(lyrics).hasValueSatisfying(found -> assertThat(found.plainLyrics())
            .isEqualTo("Pierwszy wers testowego tekstu\nDrugi wers testowego tekstu"));
    }

    @Test
    void find_whenNothingFoundAnywhere_returnsEmpty(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(get(urlPathEqualTo("/api/get")).willReturn(aResponse().withStatus(404)));
        stubFor(get(urlPathEqualTo("/api/search")).willReturn(okJson("[]")));
        LrcLibClient client = lrcLibClient(wireMock);

        // when
        Optional<LrcLibLyrics> lyrics = client.find("Nieznany", "Nieistniejący utwór", null, null);

        // then
        assertThat(lyrics).isEmpty();
    }

    private LrcLibClient lrcLibClient(WireMockRuntimeInfo wireMock) {

        return new LrcLibClient(WireMockRestClients.builder(),
            new LrcLibProperties(wireMock.getHttpBaseUrl(), 100, "music-view-test"));
    }
}
