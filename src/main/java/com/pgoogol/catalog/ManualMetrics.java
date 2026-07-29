package com.pgoogol.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Metryki utworu wgrane ręcznie z CSV (D24) — obejście po tym, jak Spotify
 * wyłączył {@code audio-features}. Surowy zapis pliku: wartości 0..1 (nie 0..100),
 * BPM bez korekty half-time. Projekcję na {@code track_catalog} robi
 * {@code ManualMetricsApplier}, żeby dało się ją odtworzyć po ponownym imporcie.
 */
@Entity
@Table(name = "manual_metrics")
public class ManualMetrics {

    @Id
    @Column(name = "spotify_id", length = 64)
    private String spotifyId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spotify_id")
    private TrackCatalog track;

    @Column(precision = 6, scale = 2)
    private BigDecimal bpm;

    @Column(name = "musical_key", length = 16)
    private String musicalKey;

    @Column(length = 8)
    private String camelot;

    @Column(precision = 6, scale = 3)
    private BigDecimal danceability;

    @Column(precision = 6, scale = 3)
    private BigDecimal energy;

    @Column(precision = 6, scale = 3)
    private BigDecimal valence;

    @Column(precision = 6, scale = 3)
    private BigDecimal acousticness;

    @Column(precision = 6, scale = 3)
    private BigDecimal instrumentalness;

    @Column(precision = 6, scale = 3)
    private BigDecimal speechiness;

    @Column(precision = 6, scale = 3)
    private BigDecimal liveness;

    @Column(name = "loudness_db", precision = 5, scale = 2)
    private BigDecimal loudnessDb;

    @Column(name = "time_signature")
    private Integer timeSignature;

    @Column(length = 255)
    private String source;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    protected ManualMetrics() {

    }

    public ManualMetrics(TrackCatalog track) {

        this.track = Objects.requireNonNull(track, "track");
        this.importedAt = Instant.now();
    }

    public String getSpotifyId() {
        return spotifyId;
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

    public String getCamelot() {
        return camelot;
    }

    public void setCamelot(String camelot) {
        this.camelot = camelot;
    }

    public BigDecimal getDanceability() {
        return danceability;
    }

    public void setDanceability(BigDecimal danceability) {
        this.danceability = danceability;
    }

    public BigDecimal getEnergy() {
        return energy;
    }

    public void setEnergy(BigDecimal energy) {
        this.energy = energy;
    }

    public BigDecimal getValence() {
        return valence;
    }

    public void setValence(BigDecimal valence) {
        this.valence = valence;
    }

    public BigDecimal getAcousticness() {
        return acousticness;
    }

    public void setAcousticness(BigDecimal acousticness) {
        this.acousticness = acousticness;
    }

    public BigDecimal getInstrumentalness() {
        return instrumentalness;
    }

    public void setInstrumentalness(BigDecimal instrumentalness) {
        this.instrumentalness = instrumentalness;
    }

    public BigDecimal getSpeechiness() {
        return speechiness;
    }

    public void setSpeechiness(BigDecimal speechiness) {
        this.speechiness = speechiness;
    }

    public BigDecimal getLiveness() {
        return liveness;
    }

    public void setLiveness(BigDecimal liveness) {
        this.liveness = liveness;
    }

    public BigDecimal getLoudnessDb() {
        return loudnessDb;
    }

    public void setLoudnessDb(BigDecimal loudnessDb) {
        this.loudnessDb = loudnessDb;
    }

    public Integer getTimeSignature() {
        return timeSignature;
    }

    public void setTimeSignature(Integer timeSignature) {
        this.timeSignature = timeSignature;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Instant importedAt) {
        this.importedAt = importedAt;
    }
}
