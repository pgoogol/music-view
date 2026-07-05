package com.pgoogol.enrichment.musicbrainz;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bez konfigurowalnego limitu zapytań — 1 req/s jest twardo w kliencie (D6).
 */
@ConfigurationProperties(prefix = "clients.musicbrainz")
public record MusicBrainzProperties(String baseUrl, String userAgent) {

}
