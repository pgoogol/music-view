package com.pgoogol.library;

import com.pgoogol.catalog.TrackCatalog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Wpis biblioteki DJ-a (D3) — dane prywatne: uwagi, custom tagi, rating,
 * override slotu. Jeden wpis na utwór katalogu (D17).
 */
@Entity
@Table(name = "library_entry")
public class LibraryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Blokada optymistyczna (D29) — dwie karty przeglądarki nie nadpiszą sobie notatek. */
    @Version
    @Column(nullable = false)
    private int version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spotify_id", nullable = false)
    private TrackCatalog track;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private LibrarySource source;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "dj_notes")
    private String djNotes;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "custom_tags", columnDefinition = "text[]")
    private List<String> customTags;

    @Column(name = "dj_slot_override", length = 32)
    private String djSlotOverride;

    private Integer rating;

    protected LibraryEntry() {

    }

    public LibraryEntry(TrackCatalog track, LibrarySource source) {

        this.track = Objects.requireNonNull(track, "track");
        this.source = Objects.requireNonNull(source, "source");
        this.addedAt = Instant.now();
    }

    public int getVersion() {
        return version;
    }

    public Long getId() {
        return id;
    }

    public TrackCatalog getTrack() {
        return track;
    }

    public LibrarySource getSource() {
        return source;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public String getDjNotes() {
        return djNotes;
    }

    public void setDjNotes(String djNotes) {
        this.djNotes = djNotes;
    }

    public List<String> getCustomTags() {
        return customTags;
    }

    public void setCustomTags(List<String> customTags) {
        this.customTags = customTags;
    }

    public String getDjSlotOverride() {
        return djSlotOverride;
    }

    public void setDjSlotOverride(String djSlotOverride) {
        this.djSlotOverride = djSlotOverride;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }
}
