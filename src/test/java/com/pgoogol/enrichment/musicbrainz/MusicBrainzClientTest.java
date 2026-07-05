package com.pgoogol.enrichment.musicbrainz;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.pgoogol.WireMockRestClients;

import java.time.Duration;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@WireMockTest
@ExtendWith(MockitoExtension.class)
class MusicBrainzClientTest {

    private static final String ISRC_JSON = """
        {
          "isrc": "USSD11300483",
          "recordings": [
            {"id": "9d444787-3f25-4c16-9261-597b9ab021cc", "title": "Vivir Mi Vida"},
            {"id": "inne-nagranie"}
          ]
        }
        """;

    @Mock
    private MusicBrainzIsrcCacheRepository cacheRepository;

    @Test
    void lookupMbid_whenIsrcKnown_returnsFirstRecordingAndCachesResult(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(get(urlPathMatching("/ws/2/isrc/.*")).willReturn(okJson(ISRC_JSON)));
        given(cacheRepository.findById("USSD11300483")).willReturn(Optional.empty());
        MusicBrainzClient client = musicBrainzClient(wireMock);

        // when
        Optional<String> mbid = client.lookupMbid("USSD11300483");

        // then
        assertThat(mbid).contains("9d444787-3f25-4c16-9261-597b9ab021cc");
        ArgumentCaptor<MusicBrainzIsrcCache> saved = ArgumentCaptor.forClass(MusicBrainzIsrcCache.class);
        then(cacheRepository).should().save(saved.capture());
        assertThat(saved.getValue().getIsrc()).isEqualTo("USSD11300483");
        assertThat(saved.getValue().getMbid()).isEqualTo("9d444787-3f25-4c16-9261-597b9ab021cc");
        verify(getRequestedFor(urlPathMatching("/ws/2/isrc/USSD11300483"))
            .withHeader("User-Agent", equalTo("music-view-test/0.1 (test@example.com)"))
            .withQueryParam("fmt", equalTo("json")));
    }

    @Test
    void lookupMbid_whenCached_doesNotCallMusicBrainz(WireMockRuntimeInfo wireMock) {

        // given
        given(cacheRepository.findById("USSD11300483"))
            .willReturn(Optional.of(new MusicBrainzIsrcCache("USSD11300483", "cached-mbid")));
        MusicBrainzClient client = musicBrainzClient(wireMock);

        // when
        Optional<String> mbid = client.lookupMbid("USSD11300483");

        // then
        assertThat(mbid).contains("cached-mbid");
        verify(0, getRequestedFor(urlPathMatching("/ws/2/isrc/.*")));
    }

    @Test
    void lookupMbid_whenIsrcUnknown_returnsEmptyAndCachesMiss(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(get(urlPathMatching("/ws/2/isrc/.*")).willReturn(notFound()));
        given(cacheRepository.findById(anyString())).willReturn(Optional.empty());
        MusicBrainzClient client = musicBrainzClient(wireMock);

        // when
        Optional<String> mbid = client.lookupMbid("PLXXX0000000");

        // then
        assertThat(mbid).isEmpty();
        ArgumentCaptor<MusicBrainzIsrcCache> saved = ArgumentCaptor.forClass(MusicBrainzIsrcCache.class);
        then(cacheRepository).should().save(saved.capture());
        assertThat(saved.getValue().getMbid()).isNull();
    }

    @Test
    void lookupMbid_whenThreeUncachedLookups_throttlesToOneRequestPerSecond(WireMockRuntimeInfo wireMock) {

        // given — twardy limit MB: 1 req/s, więc 3 zapytania ≥ ~2 s
        stubFor(get(urlPathMatching("/ws/2/isrc/.*")).willReturn(okJson(ISRC_JSON)));
        given(cacheRepository.findById(anyString())).willReturn(Optional.empty());
        MusicBrainzClient client = musicBrainzClient(wireMock);
        long start = System.nanoTime();

        // when
        client.lookupMbid("ISRC0000000A");
        client.lookupMbid("ISRC0000000B");
        client.lookupMbid("ISRC0000000C");
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        // then
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(1800);
        verify(3, getRequestedFor(urlPathMatching("/ws/2/isrc/.*")));
        then(cacheRepository).should(times(3)).save(any());
    }

    private MusicBrainzClient musicBrainzClient(WireMockRuntimeInfo wireMock) {

        MusicBrainzProperties properties = new MusicBrainzProperties(
            wireMock.getHttpBaseUrl(), "music-view-test/0.1 (test@example.com)");
        return new MusicBrainzClient(WireMockRestClients.builder(), properties, cacheRepository);
    }
}
