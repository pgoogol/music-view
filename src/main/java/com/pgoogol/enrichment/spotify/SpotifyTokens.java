package com.pgoogol.enrichment.spotify;

/**
 * Odpowiedź serwera autoryzacji Spotify. {@code refreshToken} bywa pusty przy
 * odświeżaniu — wtedy obowiązuje dotychczasowy. Nie logujemy tych wartości (D14).
 */
public record SpotifyTokens(String accessToken, String refreshToken, long expiresIn, String scope) {

}
