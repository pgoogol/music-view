package com.pgoogol.enrichment.spotify;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code redirectUri} i {@code scopes} dotyczą wyłącznie połączenia konta
 * właściciela (OAuth PKCE — D4); {@code redirectUri} musi być identyczny
 * z adresem wpisanym w dashboardzie aplikacji Spotify.
 */
@ConfigurationProperties(prefix = "clients.spotify")
public record SpotifyProperties(
    String baseUrl,
    String authUrl,
    String clientId,
    String clientSecret,
    int requestsPerSecond,
    String redirectUri,
    String scopes) {

}
