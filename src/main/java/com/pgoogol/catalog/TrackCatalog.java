package com.pgoogol.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Dane deterministyczne utworu (D3/D5) — metadane, cechy audio i analiza AI.
 * Dane prywatne DJ-a żyją w {@code library_entry}, nie tutaj.
 */
@Entity
@Table(name = "track_catalog")
public class TrackCatalog {

    @Id
    @Column(name = "spotify_id", length = 64)
    private String spotifyId;

    @Column(length = 16)
    private String isrc;

    @Column(length = 500)
    private String title;

    @Column(length = 500)
    private String artist;

    @Column(length = 500)
    private String album;

    @Column(name = "year")
    private Integer year;

    @Column(name = "duration_ms")
    private Integer durationMs;

    private Integer popularity;

    private Boolean explicit;

    @Column(name = "album_image_url", length = 1024)
    private String albumImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "genre_family", length = 32)
    private GenreFamily genreFamily;

    @Column(length = 255)
    private String style;

    private Integer bpm;

    @Enumerated(EnumType.STRING)
    @Column(name = "bpm_source", length = 32)
    private BpmSource bpmSource;

    @Column(precision = 6, scale = 3)
    private BigDecimal danceability;

    @Column(name = "musical_key", length = 16)
    private String musicalKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "tempo_class", length = 16)
    private TempoClass tempoClass;

    @Column(length = 32)
    private String energy;

    @Column(name = "lyrics_theme", length = 500)
    private String lyricsTheme;

    @Column(name = "description_pl")
    private String descriptionPl;

    @Column(length = 16)
    private String confidence;

    @Column(name = "enriched_at")
    private Instant enrichedAt;

    @Column(name = "model_used", length = 128)
    private String modelUsed;

    @Column(name = "enrich_version")
    private Integer enrichVersion;

    protected TrackCatalog() {

    }

    public TrackCatalog(String spotifyId, String title, String artist) {

        this.spotifyId = Objects.requireNonNull(spotifyId, "spotifyId");
        this.title = title;
        this.artist = artist;
    }

    public String getSpotifyId() {
        return spotifyId;
    }

    public String getIsrc() {
        return isrc;
    }

    public void setIsrc(String isrc) {
        this.isrc = isrc;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getPopularity() {
        return popularity;
    }

    public void setPopularity(Integer popularity) {
        this.popularity = popularity;
    }

    public Boolean getExplicit() {
        return explicit;
    }

    public void setExplicit(Boolean explicit) {
        this.explicit = explicit;
    }

    public String getAlbumImageUrl() {
        return albumImageUrl;
    }

    public void setAlbumImageUrl(String albumImageUrl) {
        this.albumImageUrl = albumImageUrl;
    }

    public GenreFamily getGenreFamily() {
        return genreFamily;
    }

    public void setGenreFamily(GenreFamily genreFamily) {
        this.genreFamily = genreFamily;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public Integer getBpm() {
        return bpm;
    }

    public void setBpm(Integer bpm) {
        this.bpm = bpm;
    }

    public BpmSource getBpmSource() {
        return bpmSource;
    }

    public void setBpmSource(BpmSource bpmSource) {
        this.bpmSource = bpmSource;
    }

    public BigDecimal getDanceability() {
        return danceability;
    }

    public void setDanceability(BigDecimal danceability) {
        this.danceability = danceability;
    }

    public String getMusicalKey() {
        return musicalKey;
    }

    public void setMusicalKey(String musicalKey) {
        this.musicalKey = musicalKey;
    }

    public TempoClass getTempoClass() {
        return tempoClass;
    }

    public void setTempoClass(TempoClass tempoClass) {
        this.tempoClass = tempoClass;
    }

    public String getEnergy() {
        return energy;
    }

    public void setEnergy(String energy) {
        this.energy = energy;
    }

    public String getLyricsTheme() {
        return lyricsTheme;
    }

    public void setLyricsTheme(String lyricsTheme) {
        this.lyricsTheme = lyricsTheme;
    }

    public String getDescriptionPl() {
        return descriptionPl;
    }

    public void setDescriptionPl(String descriptionPl) {
        this.descriptionPl = descriptionPl;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public Instant getEnrichedAt() {
        return enrichedAt;
    }

    public void setEnrichedAt(Instant enrichedAt) {
        this.enrichedAt = enrichedAt;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public Integer getEnrichVersion() {
        return enrichVersion;
    }

    public void setEnrichVersion(Integer enrichVersion) {
        this.enrichVersion = enrichVersion;
    }
}
