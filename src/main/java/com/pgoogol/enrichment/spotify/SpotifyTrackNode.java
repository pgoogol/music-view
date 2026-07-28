package com.pgoogol.enrichment.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Surowy obiekt utworu z API Spotify — ten sam kształt w {@code /v1/tracks}
 * i w pozycjach playlisty ({@code items[].track}), dlatego mapowanie na
 * {@link SpotifyTrackMetadata} jest wspólne ({@link SpotifyTrackMapper}).
 * {@code type} odróżnia utwór od odcinka podcastu, {@code isLocal} — plik
 * lokalny DJ-a, którego nie ma w katalogu Spotify.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpotifyTrackNode(
    String id,
    String name,
    String type,
    @JsonProperty("is_local") Boolean isLocal,
    @JsonProperty("duration_ms") Integer durationMs,
    Boolean explicit,
    Integer popularity,
    List<ArtistNode> artists,
    AlbumNode album,
    @JsonProperty("external_ids") Map<String, String> externalIds) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArtistNode(String name) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlbumNode(String name,
                            @JsonProperty("release_date") String releaseDate,
                            List<ImageNode> images) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageNode(String url) {

    }
}
