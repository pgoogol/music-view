package com.pgoogol.api;

import java.time.Instant;

/**
 * Stan połączenia z kontem Spotify — kontrakt {@code /api/auth/spotify/*}.
 * Świadomie bez tokenów: te nie opuszczają serwera (docs/rules/security.md).
 */
public record SpotifyAccountResponse(
    boolean connected,
    String spotifyUserId,
    String displayName,
    String scopes,
    Instant expiresAt,
    Instant connectedAt) {

}
