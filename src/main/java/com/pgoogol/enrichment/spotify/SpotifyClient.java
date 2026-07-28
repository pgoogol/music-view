package com.pgoogol.enrichment.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pgoogol.common.ExternalServiceException;
import com.pgoogol.common.RateLimitedException;
import com.pgoogol.common.ratelimit.ApiCallGuard;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Fakty ze Spotify (D6): metadane + ISRC po spotify_id, batch po 50
 * (limit endpointu /v1/tracks). Client Credentials z cache'owanym tokenem;
 * limiter + retry z {@code common/ratelimit}.
 */
@Component
public class SpotifyClient {

    static final int TRACKS_BATCH_SIZE = 50;
    private static final Duration TOKEN_EXPIRY_MARGIN = Duration.ofSeconds(60);

    private final RestClient apiClient;
    private final RestClient authClient;
    private final SpotifyProperties properties;
    private final ApiCallGuard guard;
    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    public SpotifyClient(RestClient.Builder restClientBuilder, SpotifyProperties properties) {

        this.apiClient = restClientBuilder.clone().baseUrl(properties.baseUrl()).build();
        this.authClient = restClientBuilder.clone().baseUrl(properties.authUrl()).build();
        this.properties = properties;
        this.guard = ApiCallGuard.of("spotify", properties.requestsPerSecond());
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

        TracksResponse response = guard.execute(() -> {
            try {
                return apiClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/tracks")
                        .queryParam("ids", String.join(",", batchIds))
                        .build())
                    .headers(headers -> headers.setBearerAuth(bearerToken()))
                    .retrieve()
                    .body(TracksResponse.class);
            } catch (HttpClientErrorException.TooManyRequests ex) {
                throw new RateLimitedException("SPOTIFY_RATE_LIMITED",
                    "Spotify ograniczył liczbę zapytań", retryAfter(ex));
            } catch (HttpServerErrorException | ResourceAccessException ex) {
                throw new ExternalServiceException("SPOTIFY_UNAVAILABLE",
                    "Spotify API niedostępne", ex);
            }
        });
        return response.tracks().stream()
            .filter(Objects::nonNull)
            .map(this::toMetadata)
            .toList();
    }

    private String bearerToken() {

        CachedToken token = cachedToken.get();
        if (Objects.nonNull(token) && token.isValid()) {
            return token.value();
        }
        synchronized (cachedToken) {
            token = cachedToken.get();
            if (Objects.nonNull(token) && token.isValid()) {
                return token.value();
            }
            TokenResponse response = requestToken();
            CachedToken fresh = new CachedToken(response.accessToken(),
                Instant.now().plusSeconds(response.expiresIn()).minus(TOKEN_EXPIRY_MARGIN));
            cachedToken.set(fresh);
            return fresh.value();
        }
    }

    private TokenResponse requestToken() {

        try {
            return authClient.post()
                .uri("/api/token")
                .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(TokenResponse.class);
        } catch (HttpServerErrorException | ResourceAccessException ex) {
            throw new ExternalServiceException("SPOTIFY_AUTH_UNAVAILABLE",
                "Nie udało się pobrać tokenu Spotify", ex);
        }
    }

    private SpotifyTrackMetadata toMetadata(TrackNode track) {

        String artist = Optional.ofNullable(track.artists()).orElse(List.of()).stream()
            .map(ArtistNode::name)
            .collect(Collectors.joining(", "));
        AlbumNode album = Objects.requireNonNullElse(track.album(), new AlbumNode(null, null, List.of()));
        String albumImageUrl = Optional.ofNullable(album.images()).orElse(List.of()).stream()
            .findFirst()
            .map(ImageNode::url)
            .orElse(null);
        String isrc = Optional.ofNullable(track.externalIds())
            .map(ids -> ids.get("isrc"))
            .orElse(null);
        return new SpotifyTrackMetadata(track.id(), track.name(), artist, album.name(),
            releaseYear(album.releaseDate()), track.durationMs(), track.popularity(),
            track.explicit(), albumImageUrl, isrc);
    }

    private Integer releaseYear(String releaseDate) {

        if (Objects.isNull(releaseDate) || releaseDate.length() < 4) {
            return null;
        }
        try {
            return Integer.valueOf(releaseDate.substring(0, 4));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Duration retryAfter(HttpClientErrorException.TooManyRequests ex) {

        return Optional.ofNullable(ex.getResponseHeaders())
            .map(headers -> headers.getFirst("Retry-After"))
            .map(Long::parseLong)
            .map(Duration::ofSeconds)
            .orElse(null);
    }

    private record CachedToken(String value, Instant expiresAt) {

        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("expires_in") long expiresIn) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TracksResponse(List<TrackNode> tracks) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TrackNode(String id,
                             String name,
                             @JsonProperty("duration_ms") Integer durationMs,
                             Boolean explicit,
                             Integer popularity,
                             List<ArtistNode> artists,
                             AlbumNode album,
                             @JsonProperty("external_ids") Map<String, String> externalIds) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ArtistNode(String name) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AlbumNode(String name,
                             @JsonProperty("release_date") String releaseDate,
                             List<ImageNode> images) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ImageNode(String url) {

    }
}
