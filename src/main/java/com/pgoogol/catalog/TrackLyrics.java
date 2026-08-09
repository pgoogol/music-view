package com.pgoogol.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Tekst utworu z LRCLIB wraz z tłumaczeniem i interpretacją (D32) — dane
 * deterministyczne, więc strona katalogu (D3), a nie biblioteki DJ-a.
 *
 * <p>Osobna tabela zamiast kolumn w {@code track_catalog}: tekst i tłumaczenie
 * to kilka kilobajtów na utwór, a katalog czyta wyszukiwarka przy każdym
 * przewinięciu listy.</p>
 *
 * <p>Pola audytu ({@code modelUsed}, {@code promptVersion}) są tu własne —
 * tłumaczenie może powstać innym modelem i inną wersją promptu niż opis
 * z grupy AI (D15/D11).</p>
 */
@Entity
@Table(name = "track_lyrics")
public class TrackLyrics {

    @Id
    @Column(name = "spotify_id", length = 64)
    private String spotifyId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private LyricsStatus status;

    @Column(name = "lrclib_id")
    private Long lrclibId;

    @Column(name = "source_language", length = 32)
    private String sourceLanguage;

    @Column(name = "original_lyrics")
    private String originalLyrics;

    @Column(name = "translation_pl")
    private String translationPl;

    @Column(name = "interpretation_pl")
    private String interpretationPl;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @Column(name = "translated_at")
    private Instant translatedAt;

    @Column(name = "model_used", length = 128)
    private String modelUsed;

    @Column(name = "prompt_version")
    private Integer promptVersion;

    protected TrackLyrics() {

    }

    public TrackLyrics(String spotifyId, LyricsStatus status) {

        this.spotifyId = Objects.requireNonNull(spotifyId, "spotifyId");
        this.status = Objects.requireNonNull(status, "status");
    }

    /** Czy utwór jest już rozstrzygnięty — patrz {@link LyricsStatus}. */
    public boolean isResolved() {
        return status.isResolved();
    }

    public String getSpotifyId() {
        return spotifyId;
    }

    public LyricsStatus getStatus() {
        return status;
    }

    public void setStatus(LyricsStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public Long getLrclibId() {
        return lrclibId;
    }

    public void setLrclibId(Long lrclibId) {
        this.lrclibId = lrclibId;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public void setSourceLanguage(String sourceLanguage) {
        this.sourceLanguage = sourceLanguage;
    }

    public String getOriginalLyrics() {
        return originalLyrics;
    }

    public void setOriginalLyrics(String originalLyrics) {
        this.originalLyrics = originalLyrics;
    }

    public String getTranslationPl() {
        return translationPl;
    }

    public void setTranslationPl(String translationPl) {
        this.translationPl = translationPl;
    }

    public String getInterpretationPl() {
        return interpretationPl;
    }

    public void setInterpretationPl(String interpretationPl) {
        this.interpretationPl = interpretationPl;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public Instant getTranslatedAt() {
        return translatedAt;
    }

    public void setTranslatedAt(Instant translatedAt) {
        this.translatedAt = translatedAt;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public Integer getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(Integer promptVersion) {
        this.promptVersion = promptVersion;
    }
}
