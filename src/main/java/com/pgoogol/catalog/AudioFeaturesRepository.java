package com.pgoogol.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AudioFeaturesRepository extends JpaRepository<AudioFeatures, String> {

    Optional<AudioFeatures> findByTrackSpotifyId(String spotifyId);
}
