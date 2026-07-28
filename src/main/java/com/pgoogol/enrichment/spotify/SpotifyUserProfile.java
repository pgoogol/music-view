package com.pgoogol.enrichment.spotify;

/**
 * Profil właściciela konta ({@code /v1/me}) — {@code id} rozstrzyga, które
 * playlisty są jego własne (tryb C), a nie tylko obserwowane.
 */
public record SpotifyUserProfile(String spotifyUserId, String displayName) {

}
