package com.pgoogol.enrichment.musicbrainz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Trwały cache lookupów ISRC→MBID (D18); {@code mbid = null} to potwierdzony
 * brak wyniku — MusicBrainz nie jest odpytywany ponownie.
 */
@Entity
@Table(name = "musicbrainz_isrc_cache")
public class MusicBrainzIsrcCache {

    @Id
    @Column(length = 16)
    private String isrc;

    @Column(length = 36)
    private String mbid;

    @Column(name = "resolved_at", nullable = false)
    private Instant resolvedAt;

    protected MusicBrainzIsrcCache() {

    }

    public MusicBrainzIsrcCache(String isrc, @Nullable String mbid) {

        this.isrc = Objects.requireNonNull(isrc, "isrc");
        this.mbid = mbid;
        this.resolvedAt = Instant.now();
    }

    public String getIsrc() {
        return isrc;
    }

    @Nullable
    public String getMbid() {
        return mbid;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
