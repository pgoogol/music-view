package com.pgoogol.enrichment.spotify;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Połączone konto Spotify właściciela (D4/D20). Narzędzie jest jednoosobowe (D2),
 * więc w tabeli żyje dokładnie jeden wiersz o stałym {@link #SINGLE_ROW_ID} —
 * ponowne połączenie nadpisuje ten sam rekord. Tokeny nie opuszczają serwera:
 * nie trafiają do odpowiedzi API ani do logów (docs/rules/security.md).
 */
@Entity
@Table(name = "spotify_account")
public class SpotifyAccount {

    public static final short SINGLE_ROW_ID = 1;

    @Id
    private Short id;

    @Column(name = "spotify_user_id", length = 64, nullable = false)
    private String spotifyUserId;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Column(name = "refresh_token", nullable = false)
    private String refreshToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(length = 500)
    private String scopes;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    protected SpotifyAccount() {

    }

    public SpotifyAccount(String spotifyUserId, String displayName, String accessToken,
                          String refreshToken, Instant expiresAt, String scopes) {

        this.id = SINGLE_ROW_ID;
        this.spotifyUserId = Objects.requireNonNull(spotifyUserId, "spotifyUserId");
        this.displayName = displayName;
        this.accessToken = Objects.requireNonNull(accessToken, "accessToken");
        this.refreshToken = Objects.requireNonNull(refreshToken, "refreshToken");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.scopes = scopes;
        this.connectedAt = Instant.now();
    }

    public void refreshed(String accessToken, String refreshToken, Instant expiresAt) {

        this.accessToken = Objects.requireNonNull(accessToken, "accessToken");
        this.refreshToken = Objects.requireNonNullElse(refreshToken, this.refreshToken);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean isExpiredAt(Instant moment) {
        return !moment.isBefore(expiresAt);
    }

    public Short getId() {
        return id;
    }

    public String getSpotifyUserId() {
        return spotifyUserId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getScopes() {
        return scopes;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }
}
