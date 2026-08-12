package com.pgoogol.enrichment;

import java.time.Instant;

/**
 * Jeden utwór, który wypadł z przebiegu wzbogacania (D37) — razem z powodem,
 * bo „pominięto 37" bez powodów nie mówi, czy padł jeden serwis, czy 37 razy
 * to samo.
 */
public record EnrichmentFailure(String spotifyId, String reason, Instant failedAt) { }
