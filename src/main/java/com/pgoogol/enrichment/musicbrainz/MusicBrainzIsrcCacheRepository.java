package com.pgoogol.enrichment.musicbrainz;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MusicBrainzIsrcCacheRepository extends JpaRepository<MusicBrainzIsrcCache, String> {

}
