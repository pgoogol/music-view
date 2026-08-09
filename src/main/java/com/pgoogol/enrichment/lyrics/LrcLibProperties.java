package com.pgoogol.enrichment.lyrics;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * LRCLIB — darmowe, otwarte źródło tekstów utworów (D32); bez klucza API.
 * {@code userAgent} nie jest sekretem, tylko wymogiem grzeczności wobec
 * publicznego API (jak MB_USER_AGENT w D6) — serwis prosi o identyfikację
 * klienta z adresem kontaktowym.
 */
@ConfigurationProperties(prefix = "clients.lrclib")
public record LrcLibProperties(
    @DefaultValue("https://lrclib.net") String baseUrl,
    @DefaultValue("2") int requestsPerSecond,
    @DefaultValue("music-view/0.1 (https://github.com/pgoogol/music-view)") String userAgent) {

}
