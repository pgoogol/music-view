package com.pgoogol.enrichment.deezer;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class DeezerClientTest {

    @Test
    void findBpmByIsrc_whenTrackHasBpm_returnsBpm(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(get(urlPathEqualTo("/track/isrc:USSD11300483"))
            .willReturn(okJson("{\"id\": 68095459, \"title\": \"Vivir Mi Vida\", \"bpm\": 92.4}")));
        DeezerClient client = deezerClient(wireMock);

        // when
        Optional<BigDecimal> bpm = client.findBpmByIsrc("USSD11300483");

        // then
        assertThat(bpm).contains(new BigDecimal("92.4"));
    }

    @Test
    void findBpmByIsrc_whenBpmZero_returnsEmpty(WireMockRuntimeInfo wireMock) {

        // given — Deezer zwraca bpm=0 gdy nie zna wartości (D6)
        stubFor(get(urlPathEqualTo("/track/isrc:PLXXX0000001"))
            .willReturn(okJson("{\"id\": 123, \"title\": \"Utwór bez BPM\", \"bpm\": 0}")));
        DeezerClient client = deezerClient(wireMock);

        // when
        Optional<BigDecimal> bpm = client.findBpmByIsrc("PLXXX0000001");

        // then
        assertThat(bpm).isEmpty();
    }

    @Test
    void findBpmByIsrc_whenTrackNotFound_returnsEmpty(WireMockRuntimeInfo wireMock) {

        // given — Deezer sygnalizuje brak utworu obiektem error przy statusie 200
        stubFor(get(urlPathEqualTo("/track/isrc:PLXXX0000002"))
            .willReturn(okJson("{\"error\": {\"type\": \"DataException\", \"message\": \"no data\"}}")));
        DeezerClient client = deezerClient(wireMock);

        // when
        Optional<BigDecimal> bpm = client.findBpmByIsrc("PLXXX0000002");

        // then
        assertThat(bpm).isEmpty();
    }

    @Test
    void findBpmByArtistTitle_whenSearchFindsTrack_looksUpFullTrackForBpm(WireMockRuntimeInfo wireMock) {

        // given — wyniki wyszukiwania nie zawierają bpm, potrzebny drugi request
        stubFor(get(urlPathEqualTo("/search"))
            .willReturn(okJson("{\"data\": [{\"id\": 68095459, \"title\": \"Vivir Mi Vida\"}]}")));
        stubFor(get(urlPathEqualTo("/track/68095459"))
            .willReturn(okJson("{\"id\": 68095459, \"bpm\": 92.4}")));
        DeezerClient client = deezerClient(wireMock);

        // when
        Optional<BigDecimal> bpm = client.findBpmByArtistTitle("Marc Anthony", "Vivir Mi Vida");

        // then
        assertThat(bpm).contains(new BigDecimal("92.4"));
        verify(getRequestedFor(urlPathEqualTo("/search"))
            .withQueryParam("q", containing("Marc Anthony")));
    }

    @Test
    void findBpmByArtistTitle_whenNoSearchResults_returnsEmpty(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(get(urlPathEqualTo("/search")).willReturn(okJson("{\"data\": []}")));
        DeezerClient client = deezerClient(wireMock);

        // when
        Optional<BigDecimal> bpm = client.findBpmByArtistTitle("Nieznany", "Nieistniejący utwór");

        // then
        assertThat(bpm).isEmpty();
        verify(0, getRequestedFor(urlPathEqualTo("/track/0")));
    }

    private DeezerClient deezerClient(WireMockRuntimeInfo wireMock) {
        return new DeezerClient(RestClient.builder(),
            new DeezerProperties(wireMock.getHttpBaseUrl(), 100));
    }
}
