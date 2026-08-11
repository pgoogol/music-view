package com.pgoogol.api;

import java.time.Instant;

/**
 * Stan automatycznego odświeżania playlist (M4.7) — kontrakt
 * {@code GET /api/ingest/my-playlists/refresh-status}. Bez tego odświeżanie
 * w tle jest niewidoczne: DJ nie wie, czy patrzy na świeże dane, czy na takie,
 * których nikt nie ruszył od godziny.
 *
 * @param outcome         {@code NEVER_RUN}, {@code DISABLED},
 *                        {@code SKIPPED_NOT_CONNECTED}, {@code REFRESHED} albo {@code FAILED}
 * @param intervalSeconds co ile aplikacja sięga po zmiany (liczone od zakończenia
 *                        poprzedniego przebiegu)
 */
public record PlaylistRefreshStatusResponse(
    String outcome,
    Instant lastRunAt,
    int refreshedPlaylists,
    int failedPlaylists,
    String message,
    long intervalSeconds) {

}
