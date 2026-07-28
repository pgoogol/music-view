package com.pgoogol.enrichment.deezer;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clients.deezer")
public record DeezerProperties(String baseUrl, int requestsPerSecond) {

}
