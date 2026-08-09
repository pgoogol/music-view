package com.pgoogol.enrichment.lyrics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pgoogol.common.ExternalServiceException;
import com.pgoogol.common.RateLimitedException;
import com.pgoogol.common.ratelimit.ApiCallGuard;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Klient LRCLIB (D32) — jedyne źródło tekstów utworów w projekcie; bez klucza
 * i bez kosztu, w duchu darmowych źródeł faktów z D6.
 *
 * <p>Dwa kroki, jak w kaskadzie Deezera (D6): najpierw dokładne dopasowanie
 * {@code /api/get} (wykonawca + tytuł + album + czas trwania), a gdy serwis
 * odpowie 404 — luźniejsze {@code /api/search} po wykonawcy i tytule. Czas
 * trwania podajemy tylko w pierwszym kroku: LRCLIB traktuje go jako warunek
 * (tolerancja kilku sekund), więc inne wydanie tego samego nagrania odpadłoby
 * bez fallbacku.</p>
 *
 * <p>Gdy nagranie ma wyłącznie tekst zsynchronizowany, znaczniki czasu
 * {@code [mm:ss.xx]} są usuwane tutaj — reszta aplikacji dostaje czysty tekst
 * i nie musi wiedzieć o formacie LRC.</p>
 */
@Component
public class LrcLibClient {

    private static final Pattern LRC_TIMESTAMP = Pattern.compile("^(\\[\\d+:\\d+(\\.\\d+)?])+");

    private final RestClient restClient;
    private final ApiCallGuard guard;

    public LrcLibClient(RestClient.Builder restClientBuilder, LrcLibProperties properties) {

        this.restClient = restClientBuilder.clone()
            .baseUrl(properties.baseUrl())
            .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
            .build();
        this.guard = ApiCallGuard.of("lrclib", properties.requestsPerSecond());
    }

    /**
     * Pusto oznacza „LRCLIB nie zna tego nagrania" — wywołujący zapisuje to
     * jako potwierdzony brak, żeby nie pytać drugi raz (D32).
     */
    public Optional<LrcLibLyrics> find(String artist, String title, String album, Integer durationMs) {

        Objects.requireNonNull(artist, "artist");
        Objects.requireNonNull(title, "title");
        return exactMatch(artist, title, album, durationMs)
            .or(() -> searchMatch(artist, title));
    }

    private Optional<LrcLibLyrics> exactMatch(String artist, String title, String album,
                                              Integer durationMs) {

        LyricsNode node = execute(() -> restClient.get()
            .uri(uriBuilder -> {
                UriBuilder uri = uriBuilder.path("/api/get")
                    .queryParam("artist_name", artist)
                    .queryParam("track_name", title);
                if (Objects.nonNull(album) && !album.isBlank()) {
                    uri = uri.queryParam("album_name", album);
                }
                if (Objects.nonNull(durationMs)) {
                    uri = uri.queryParam("duration", Math.round(durationMs / 1000.0));
                }
                return uri.build();
            })
            .retrieve()
            .body(LyricsNode.class));
        return toLyrics(node);
    }

    private Optional<LrcLibLyrics> searchMatch(String artist, String title) {

        LyricsNode[] found = execute(() -> restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/api/search")
                .queryParam("artist_name", artist)
                .queryParam("track_name", title)
                .build())
            .retrieve()
            .body(LyricsNode[].class));
        return Optional.ofNullable(found)
            .map(List::of)
            .orElse(List.of())
            .stream()
            .map(this::toLyrics)
            .flatMap(Optional::stream)
            .findFirst();
    }

    private Optional<LrcLibLyrics> toLyrics(LyricsNode node) {

        if (Objects.isNull(node)) {
            return Optional.empty();
        }
        if (node.instrumental()) {
            return Optional.of(LrcLibLyrics.instrumental(node.id()));
        }
        return plainText(node).map(text -> new LrcLibLyrics(node.id(), false, text));
    }

    private Optional<String> plainText(LyricsNode node) {

        if (Objects.nonNull(node.plainLyrics()) && !node.plainLyrics().isBlank()) {
            return Optional.of(node.plainLyrics().strip());
        }
        return Optional.ofNullable(node.syncedLyrics())
            .filter(synced -> !synced.isBlank())
            .map(this::stripTimestamps)
            .filter(text -> !text.isBlank());
    }

    private String stripTimestamps(String syncedLyrics) {

        return syncedLyrics.lines()
            .map(line -> LRC_TIMESTAMP.matcher(line).replaceFirst("").strip())
            .collect(Collectors.joining("\n"))
            .strip();
    }

    /** 404 to normalna odpowiedź „nie znam tego nagrania", nie awaria. */
    private <T> T execute(Supplier<T> call) {

        return guard.execute(() -> {
            try {
                return call.get();
            } catch (HttpClientErrorException.NotFound ex) {
                return null;
            } catch (HttpClientErrorException.TooManyRequests ex) {
                throw new RateLimitedException("LRCLIB_RATE_LIMITED",
                    "LRCLIB ograniczył liczbę zapytań", null);
            } catch (HttpServerErrorException | ResourceAccessException ex) {
                throw new ExternalServiceException("LRCLIB_UNAVAILABLE",
                    "LRCLIB niedostępne", ex);
            }
        });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LyricsNode(Long id,
                              boolean instrumental,
                              @JsonProperty("plainLyrics") String plainLyrics,
                              @JsonProperty("syncedLyrics") String syncedLyrics) {

    }
}
