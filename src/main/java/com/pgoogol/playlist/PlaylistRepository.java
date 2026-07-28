package com.pgoogol.playlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    Optional<Playlist> findBySpotifyPlaylistId(String spotifyPlaylistId);
}
