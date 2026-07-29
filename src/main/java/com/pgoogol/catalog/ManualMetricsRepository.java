package com.pgoogol.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ManualMetricsRepository extends JpaRepository<ManualMetrics, String> {

    List<ManualMetrics> findBySpotifyIdIn(Collection<String> spotifyIds);
}
