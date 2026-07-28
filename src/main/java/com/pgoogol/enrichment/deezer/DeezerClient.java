package com.pgoogol.enrichment.deezer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pgoogol.common.ExternalServiceException;
import com.pgoogol.common.RateLimitedException;
import com.pgoogol.common.ratelimit.ApiCallGuard;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Deezer jako fallback BPM w kaskadzie D6: lookup po ISRC
 * ({@code /track/isrc:…}), a gdy brak — wyszukiwanie artist+title i dociągnięcie
 * pełnego utworu (wyniki wyszukiwania nie zawierają bpm). {@code bpm = 0}
 * oznacza brak danych, nie wartość. Bez auth; braki sygnalizowane w JSON-ie
 * polem {@code error} przy statusie 200.
 */
@Component
public class DeezerClient {

    private final RestClient restClient;
    private final ApiCallGuard guard;

    public DeezerClient(RestClient.Builder restClientBuilder, DeezerProperties properties) {

        this.restClient = restClientBuilder.clone().baseUrl(properties.baseUrl()).build();
        this.guard = ApiCallGuard.of("deezer", properties.requestsPerSecond());
    }

    public Optional<BigDecimal> findBpmByIsrc(String isrc) {

        Objects.requireNonNull(isrc, "isrc");
        TrackNode track = execute(() -> restClient.get()
            .uri("/track/isrc:{isrc}", isrc)
            .retrieve()
            .body(TrackNode.class));
        return bpmOf(track);
    }

    public Optional<BigDecimal> findBpmByArtistTitle(String artist, String title) {

        Objects.requireNonNull(artist, "artist");
        Objects.requireNonNull(title, "title");
        SearchResponse searchResponse = execute(() -> restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/search")
                .queryParam("q", "artist:\"%s\" track:\"%s\"".formatted(artist, title))
                .build())
            .retrieve()
            .body(SearchResponse.class));
        return Optional.ofNullable(searchResponse)
            .map(SearchResponse::data)
            .orElse(List.of())
            .stream()
            .findFirst()
            .flatMap(found -> findBpmByTrackId(found.id()));
    }

    private Optional<BigDecimal> findBpmByTrackId(Long trackId) {

        if (Objects.isNull(trackId)) {
            return Optional.empty();
        }
        TrackNode track = execute(() -> restClient.get()
            .uri("/track/{id}", trackId)
            .retrieve()
            .body(TrackNode.class));
        return bpmOf(track);
    }

    private Optional<BigDecimal> bpmOf(TrackNode track) {

        if (Objects.isNull(track) || Objects.nonNull(track.error()) || Objects.isNull(track.bpm())) {
            return Optional.empty();
        }
        return BigDecimal.ZERO.compareTo(track.bpm()) == 0
            ? Optional.empty()
            : Optional.of(track.bpm());
    }

    private <T> T execute(Supplier<T> call) {

        return guard.execute(() -> {
            try {
                return call.get();
            } catch (HttpClientErrorException.TooManyRequests ex) {
                throw new RateLimitedException("DEEZER_RATE_LIMITED",
                    "Deezer ograniczył liczbę zapytań", null);
            } catch (HttpServerErrorException | ResourceAccessException ex) {
                throw new ExternalServiceException("DEEZER_UNAVAILABLE",
                    "Deezer API niedostępne", ex);
            }
        });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TrackNode(Long id, BigDecimal bpm, ErrorNode error) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorNode(String type, String message) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(List<TrackNode> data) {

    }
}
