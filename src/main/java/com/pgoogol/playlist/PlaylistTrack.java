package com.pgoogol.playlist;

import com.pgoogol.catalog.TrackCatalog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Utwór na playliście z pozycją; utwór występuje na playliście najwyżej raz (D17).
 */
@Entity
@Table(name = "playlist_track")
public class PlaylistTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spotify_id", nullable = false)
    private TrackCatalog track;

    @Column(name = "position", nullable = false)
    private Integer position;

    protected PlaylistTrack() {

    }

    public PlaylistTrack(Playlist playlist, TrackCatalog track, Integer position) {

        this.playlist = Objects.requireNonNull(playlist, "playlist");
        this.track = Objects.requireNonNull(track, "track");
        this.position = Objects.requireNonNull(position, "position");
    }

    public Long getId() {
        return id;
    }

    public Playlist getPlaylist() {
        return playlist;
    }

    public TrackCatalog getTrack() {
        return track;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = Objects.requireNonNull(position, "position");
    }
}
