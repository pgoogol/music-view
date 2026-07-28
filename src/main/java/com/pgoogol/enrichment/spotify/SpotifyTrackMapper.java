package com.pgoogol.enrichment.spotify;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Mapowanie surowego utworu Spotify na {@link SpotifyTrackMetadata} (D5):
 * wykonawcy sklejani przecinkami, okładka = pierwszy (największy) obrazek albumu,
 * rok wycinany z {@code release_date} (może mieć precyzję roku, miesiąca lub dnia).
 */
@Component
public class SpotifyTrackMapper {

    private static final int YEAR_LENGTH = 4;

    public SpotifyTrackMetadata toMetadata(SpotifyTrackNode track) {

        Objects.requireNonNull(track, "track");
        String artist = Optional.ofNullable(track.artists()).orElse(List.of()).stream()
            .map(SpotifyTrackNode.ArtistNode::name)
            .collect(Collectors.joining(", "));
        SpotifyTrackNode.AlbumNode album = Objects.requireNonNullElse(
            track.album(), new SpotifyTrackNode.AlbumNode(null, null, List.of()));
        String albumImageUrl = Optional.ofNullable(album.images()).orElse(List.of()).stream()
            .findFirst()
            .map(SpotifyTrackNode.ImageNode::url)
            .orElse(null);
        String isrc = Optional.ofNullable(track.externalIds())
            .map(ids -> ids.get("isrc"))
            .orElse(null);
        return new SpotifyTrackMetadata(track.id(), track.name(), artist, album.name(),
            releaseYear(album.releaseDate()), track.durationMs(), track.popularity(),
            track.explicit(), albumImageUrl, isrc);
    }

    private Integer releaseYear(String releaseDate) {

        if (Objects.isNull(releaseDate) || releaseDate.length() < YEAR_LENGTH) {
            return null;
        }
        try {
            return Integer.valueOf(releaseDate.substring(0, YEAR_LENGTH));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
