package com.pgoogol.enrichment.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Fakty ze Spotify (D6): metadane + ISRC po spotify_id, batch po 50
 * (limit endpointu /v1/tracks). Token aplikacyjny z {@link SpotifyAppTokenProvider},
 * limiter i tłumaczenie błędów w {@link SpotifyApiExecutor}.
 */
@Component
public class SpotifyClient {

    static final int TRACKS_BATCH_SIZE = 50;

    private final RestClient apiClient;
    private final SpotifyAppTokenProvider tokenProvider;
    private final SpotifyTrackMapper trackMapper;
    private final SpotifyApiExecutor executor;

    public SpotifyClient(RestClient.Builder restClientBuilder, SpotifyProperties properties,
                         SpotifyAppTokenProvider tokenProvider, SpotifyTrackMapper trackMapper,
                         SpotifyApiExecutor executor) {

        this.apiClient = restClientBuilder.clone().baseUrl(properties.baseUrl()).build();
        this.tokenProvider = tokenProvider;
        this.trackMapper = trackMapper;
        this.executor = executor;
    }

    /**
     * Pobiera metadane utworów po spotify_id; nieznane id są pomijane
     * (Spotify zwraca dla nich null w tablicy tracks).
     */
    public List<SpotifyTrackMetadata> getTracks(Collection<String> spotifyIds) {

        Objects.requireNonNull(spotifyIds, "spotifyIds");
        List<String> ids = List.copyOf(spotifyIds);
        return IntStream.range(0, (ids.size() + TRACKS_BATCH_SIZE - 1) / TRACKS_BATCH_SIZE)
            .mapToObj(batch -> ids.subList(batch * TRACKS_BATCH_SIZE,
                Math.min((batch + 1) * TRACKS_BATCH_SIZE, ids.size())))
            .map(this::fetchBatch)
            .flatMap(List::stream)
            .toList();
    }

    private List<SpotifyTrackMetadata> fetchBatch(List<String> batchIds) {

        TracksResponse response = executor.call("utwory", () -> apiClient.get()
            .uri(uriBuilder -> uriBuilder.path("/v1/tracks")
                .queryParam("ids", String.join(",", batchIds))
                .build())
            .headers(headers -> headers.setBearerAuth(tokenProvider.bearerToken()))
            .retrieve()
            .body(TracksResponse.class));
        return response.tracks().stream()
            .filter(Objects::nonNull)
            .map(trackMapper::toMetadata)
            .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TracksResponse(List<SpotifyTrackNode> tracks) {

    }
}
