package com.pgoogol.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Cechy audio z dumpa AcousticBrainz (D7) — fakty kluczowane MBID-em,
 * 0..1 rekord na utwór katalogu (D17).
 */
@Entity
@Table(name = "audio_features")
public class AudioFeatures {

    @Id
    @Column(length = 36)
    private String mbid;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spotify_id", nullable = false)
    private TrackCatalog track;

    @Column(precision = 6, scale = 2)
    private BigDecimal bpm;

    @Column(name = "musical_key", length = 16)
    private String musicalKey;

    @Column(precision = 6, scale = 3)
    private BigDecimal danceability;

    protected AudioFeatures() {

    }

    public AudioFeatures(String mbid, TrackCatalog track) {

        this.mbid = Objects.requireNonNull(mbid, "mbid");
        this.track = Objects.requireNonNull(track, "track");
    }

    public String getMbid() {
        return mbid;
    }

    public TrackCatalog getTrack() {
        return track;
    }

    public BigDecimal getBpm() {
        return bpm;
    }

    public void setBpm(BigDecimal bpm) {
        this.bpm = bpm;
    }

    public String getMusicalKey() {
        return musicalKey;
    }

    public void setMusicalKey(String musicalKey) {
        this.musicalKey = musicalKey;
    }

    public BigDecimal getDanceability() {
        return danceability;
    }

    public void setDanceability(BigDecimal danceability) {
        this.danceability = danceability;
    }
}
