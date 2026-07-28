package com.pgoogol.enrichment.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Playlisty Spotify (M2.1): nagłówek playlisty i jej utwory ze stronicowaniem
 * (100 pozycji na stronę — limit endpointu). Czyta dane publiczne na tokenie
 * aplikacyjnym; pozycje bez odpowiednika w katalogu Spotify (pliki lokalne,
 * odcinki podcastów, utwory usunięte) wracają jako
 * {@link SpotifyPlaylistItem.Unavailable} — decyzję o nich podejmuje ingestion.
 */
@Component
public class SpotifyPlaylistClient {

    static final int PAGE_SIZE = 100;
    static final int MY_PLAYLISTS_PAGE_SIZE = 50;
    static final int URIS_BATCH_SIZE = 100;

    private static final String TRACK_TYPE = "track";
    private static final String TRACK_URI_PREFIX = "spotify:track:";

    private final RestClient apiClient;
    private final SpotifyAppTokenProvider tokenProvider;
    private final SpotifyAccountService accountService;
    private final SpotifyTrackMapper trackMapper;
    private final SpotifyApiExecutor executor;

    public SpotifyPlaylistClient(RestClient.Builder restClientBuilder, SpotifyProperties properties,
                                 SpotifyAppTokenProvider tokenProvider,
                                 SpotifyAccountService accountService,
                                 SpotifyTrackMapper trackMapper, SpotifyApiExecutor executor) {

        this.apiClient = restClientBuilder.clone().baseUrl(properties.baseUrl()).build();
        this.tokenProvider = tokenProvider;
        this.accountService = accountService;
        this.trackMapper = trackMapper;
        this.executor = executor;
    }

    /**
     * Playlisty widoczne dla właściciela (tryb C) — także obserwowane cudze,
     * dlatego filtr po właścicielu należy do ingestion. Wymaga połączonego
     * konta (D4): prywatne playlisty nie są widoczne na tokenie aplikacyjnym.
     */
    public List<SpotifyPlaylist> getMyPlaylists() {

        MyPlaylistsResponse firstPage = fetchMyPlaylistsPage(0);
        int total = Objects.requireNonNullElse(firstPage.total(), firstPage.items().size());
        List<SpotifyPlaylist> playlists = new ArrayList<>(toPlaylists(firstPage));
        IntStream.iterate(MY_PLAYLISTS_PAGE_SIZE, offset -> offset < total,
                offset -> offset + MY_PLAYLISTS_PAGE_SIZE)
            .forEach(offset -> playlists.addAll(toPlaylists(fetchMyPlaylistsPage(offset))));
        return List.copyOf(playlists);
    }

    private MyPlaylistsResponse fetchMyPlaylistsPage(int offset) {

        return executor.call("playlisty właściciela", () -> apiClient.get()
            .uri(uriBuilder -> uriBuilder.path("/v1/me/playlists")
                .queryParam("limit", MY_PLAYLISTS_PAGE_SIZE)
                .queryParam("offset", offset)
                .build())
            .headers(headers -> headers.setBearerAuth(accountService.userAccessToken()))
            .retrieve()
            .body(MyPlaylistsResponse.class));
    }

    private List<SpotifyPlaylist> toPlaylists(MyPlaylistsResponse page) {

        return Objects.requireNonNullElse(page.items(), List.<PlaylistResponse>of()).stream()
            .filter(Objects::nonNull)
            .map(this::toPlaylist)
            .toList();
    }

    public SpotifyPlaylist getPlaylist(String playlistId) {

        Objects.requireNonNull(playlistId, "playlistId");
        PlaylistResponse response = executor.call("playlista " + playlistId, () -> apiClient.get()
            .uri("/v1/playlists/{id}", playlistId)
            .headers(headers -> headers.setBearerAuth(tokenProvider.bearerToken()))
            .retrieve()
            .body(PlaylistResponse.class));
        return toPlaylist(response);
    }

    /** Wszystkie pozycje playlisty w kolejności, z pozycją liczoną w skali całej playlisty. */
    public List<SpotifyPlaylistItem> getPlaylistItems(String playlistId) {

        Objects.requireNonNull(playlistId, "playlistId");
        PlaylistItemsResponse firstPage = fetchItemsPage(playlistId, 0);
        int total = Objects.requireNonNullElse(firstPage.total(), firstPage.items().size());
        List<SpotifyPlaylistItem> items = new ArrayList<>(toItems(firstPage, 0));
        IntStream.iterate(PAGE_SIZE, offset -> offset < total, offset -> offset + PAGE_SIZE)
            .forEach(offset -> items.addAll(toItems(fetchItemsPage(playlistId, offset), offset)));
        return List.copyOf(items);
    }

    /** Zakłada playlistę na koncie właściciela (M2.4); domyślnie prywatną. */
    public String createPlaylist(String userId, String name, String description) {

        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(name, "name");
        PlaylistResponse response = executor.call("konto " + userId, () -> apiClient.post()
            .uri("/v1/users/{userId}/playlists", userId)
            .headers(headers -> headers.setBearerAuth(accountService.userAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreatePlaylistRequest(name, false, description))
            .retrieve()
            .body(PlaylistResponse.class));
        return response.id();
    }

    /**
     * Ustawia zawartość playlisty na dokładnie podane utwory, w tej kolejności.
     * Pierwsza partia idzie przez PUT (zastępuje całość), kolejne przez POST —
     * bo endpoint przyjmuje najwyżej 100 URI naraz.
     */
    public void replaceTracks(String playlistId, List<String> spotifyIds) {

        Objects.requireNonNull(playlistId, "playlistId");
        Objects.requireNonNull(spotifyIds, "spotifyIds");
        List<String> uris = spotifyIds.stream().map(TRACK_URI_PREFIX::concat).toList();
        List<List<String>> batches = IntStream
            .iterate(0, offset -> offset < uris.size(), offset -> offset + URIS_BATCH_SIZE)
            .mapToObj(offset -> uris.subList(offset, Math.min(offset + URIS_BATCH_SIZE, uris.size())))
            .toList();
        sendUris(playlistId, batches.isEmpty() ? List.of() : batches.getFirst(), true);
        batches.stream().skip(1).forEach(batch -> sendUris(playlistId, batch, false));
    }

    private void sendUris(String playlistId, List<String> uris, boolean replace) {

        executor.call("playlista " + playlistId, () -> {
            RestClient.RequestBodySpec request = replace
                ? apiClient.put().uri("/v1/playlists/{id}/tracks", playlistId)
                : apiClient.post().uri("/v1/playlists/{id}/tracks", playlistId);
            return request
                .headers(headers -> headers.setBearerAuth(accountService.userAccessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UrisRequest(uris))
                .retrieve()
                .toBodilessEntity();
        });
    }

    private PlaylistItemsResponse fetchItemsPage(String playlistId, int offset) {

        return executor.call("playlista " + playlistId, () -> apiClient.get()
            .uri(uriBuilder -> uriBuilder.path("/v1/playlists/{id}/tracks")
                .queryParam("limit", PAGE_SIZE)
                .queryParam("offset", offset)
                .build(playlistId))
            .headers(headers -> headers.setBearerAuth(tokenProvider.bearerToken()))
            .retrieve()
            .body(PlaylistItemsResponse.class));
    }

    private List<SpotifyPlaylistItem> toItems(PlaylistItemsResponse page, int offset) {

        List<ItemNode> items = Objects.requireNonNullElse(page.items(), List.<ItemNode>of());
        return IntStream.range(0, items.size())
            .mapToObj(index -> toItem(items.get(index), offset + index))
            .toList();
    }

    private SpotifyPlaylistItem toItem(ItemNode item, int position) {

        SpotifyTrackNode track = Objects.isNull(item) ? null : item.track();
        if (Objects.isNull(track)) {
            return new SpotifyPlaylistItem.Unavailable(position,
                "pozycja bez utworu — usunięty ze Spotify lub niedostępny w regionie");
        }
        if (Boolean.TRUE.equals(track.isLocal()) || Objects.isNull(track.id())) {
            return new SpotifyPlaylistItem.Unavailable(position,
                "plik lokalny — brak odpowiednika w katalogu Spotify");
        }
        if (Objects.nonNull(track.type()) && !TRACK_TYPE.equals(track.type())) {
            return new SpotifyPlaylistItem.Unavailable(position,
                "pozycja typu '%s' — nie jest utworem".formatted(track.type()));
        }
        return new SpotifyPlaylistItem.Track(position, trackMapper.toMetadata(track));
    }

    private SpotifyPlaylist toPlaylist(PlaylistResponse response) {

        OwnerNode owner = Objects.requireNonNullElse(response.owner(), new OwnerNode(null, null));
        int trackCount = Objects.isNull(response.tracks())
            ? 0
            : Objects.requireNonNullElse(response.tracks().total(), 0);
        return new SpotifyPlaylist(response.id(), response.name(),
            owner.id(), owner.displayName(), trackCount);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PlaylistResponse(String id, String name, OwnerNode owner, TracksNode tracks) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OwnerNode(String id, @JsonProperty("display_name") String displayName) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TracksNode(Integer total) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MyPlaylistsResponse(List<PlaylistResponse> items, Integer total) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PlaylistItemsResponse(List<ItemNode> items, Integer total) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ItemNode(SpotifyTrackNode track) {

    }

    private record CreatePlaylistRequest(String name,
                                         @JsonProperty("public") boolean publicPlaylist,
                                         String description) {

    }

    private record UrisRequest(List<String> uris) {

    }
}
