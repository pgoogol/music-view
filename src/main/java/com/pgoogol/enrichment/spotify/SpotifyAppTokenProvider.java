package com.pgoogol.enrichment.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pgoogol.common.ExternalServiceException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Token aplikacyjny Spotify (Client Credentials — D6) z cache'em w pamięci;
 * współdzielony przez wszystkich klientów Spotify sięgających po dane publiczne
 * ({@link SpotifyClient}, {@link SpotifyPlaylistClient}). Operacje na koncie
 * właściciela wymagają tokenu użytkownika (OAuth PKCE — D4), nie tego.
 */
@Component
public class SpotifyAppTokenProvider {

    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

    private final RestClient authClient;
    private final SpotifyProperties properties;
    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    public SpotifyAppTokenProvider(RestClient.Builder restClientBuilder,
                                   SpotifyProperties properties) {

        this.authClient = restClientBuilder.clone().baseUrl(properties.authUrl()).build();
        this.properties = properties;
    }

    public String bearerToken() {

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
                Instant.now().plusSeconds(response.expiresIn()).minus(EXPIRY_MARGIN));
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

    private record CachedToken(String value, Instant expiresAt) {

        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("expires_in") long expiresIn) {

    }
}
