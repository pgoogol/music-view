package com.pgoogol.playlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;

/**
 * Playlista / plan setu; {@code spotify_playlist_id} ustawiane po eksporcie
 * na Spotify (M2.4).
 */
@Entity
@Table(name = "playlist")
public class Playlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Blokada optymistyczna na agregacie (D29): zmiana składu albo kolejności
     * setu podbija tę wersję, choć zmieniają się wiersze {@code playlist_track}.
     */
    @Version
    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String name;

    @Column(name = "spotify_playlist_id", length = 64)
    private String spotifyPlaylistId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Playlist() {

    }

    public Playlist(String name) {

        this.name = Objects.requireNonNull(name, "name");
        this.createdAt = Instant.now();
    }

    public int getVersion() {
        return version;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public String getSpotifyPlaylistId() {
        return spotifyPlaylistId;
    }

    public void setSpotifyPlaylistId(String spotifyPlaylistId) {
        this.spotifyPlaylistId = spotifyPlaylistId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
