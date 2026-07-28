package com.pgoogol.enrichment.spotify;

import java.time.Instant;

/**
 * Stan połączenia z kontem Spotify — bez tokenów, świadomie
 * (docs/rules/security.md: tokeny nie opuszczają serwera).
 */
public record SpotifyAccountStatus(
    boolean connected,
    String spotifyUserId,
    String displayName,
    String scopes,
    Instant expiresAt,
    Instant connectedAt) {

    public static SpotifyAccountStatus disconnected() {
        return new SpotifyAccountStatus(false, null, null, null, null, null);
    }

    public static SpotifyAccountStatus of(SpotifyAccount account) {

        return new SpotifyAccountStatus(true, account.getSpotifyUserId(), account.getDisplayName(),
            account.getScopes(), account.getExpiresAt(), account.getConnectedAt());
    }
}
