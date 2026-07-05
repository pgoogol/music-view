package com.pgoogol.enrichment.spotify;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clients.spotify")
public record SpotifyProperties(
    String baseUrl,
    String authUrl,
    String clientId,
    String clientSecret,
    int requestsPerSecond) {

}
