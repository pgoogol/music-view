package com.pgoogol.ingestion;

import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogRepository;
import com.pgoogol.enrichment.spotify.SpotifyPlaylist;
import com.pgoogol.enrichment.spotify.SpotifyPlaylistClient;
import com.pgoogol.enrichment.spotify.SpotifyPlaylistItem;
import com.pgoogol.enrichment.spotify.SpotifyTrackMetadata;
import com.pgoogol.library.LibraryEntry;
import com.pgoogol.library.LibraryEntryRepository;
import com.pgoogol.library.LibrarySource;
import com.pgoogol.playlist.Playlist;
import com.pgoogol.playlist.PlaylistRepository;
import com.pgoogol.playlist.PlaylistTrack;
import com.pgoogol.playlist.PlaylistTrackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Import playlisty ze Spotify (M2.1, tryby B/D): utwory trafiają do katalogu
 * i biblioteki (dedup po spotify_id), a sama playlista odtwarzana jest lokalnie
 * wraz z kolejnością utworów. Ponowny import tej samej playlisty aktualizuje
 * nazwę i kolejność, nie tworzy duplikatów.
 */
@Service
public class PlaylistIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PlaylistIngestionService.class);

    private final SpotifyPlaylistUrlParser urlParser;
    private final SpotifyPlaylistClient playlistClient;
    private final TrackCatalogRepository trackCatalogRepository;
    private final LibraryEntryRepository libraryEntryRepository;
    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;

    public PlaylistIngestionService(SpotifyPlaylistUrlParser urlParser,
                                    SpotifyPlaylistClient playlistClient,
                                    TrackCatalogRepository trackCatalogRepository,
                                    LibraryEntryRepository libraryEntryRepository,
                                    PlaylistRepository playlistRepository,
                                    PlaylistTrackRepository playlistTrackRepository) {

        this.urlParser = urlParser;
        this.playlistClient = playlistClient;
        this.trackCatalogRepository = trackCatalogRepository;
        this.libraryEntryRepository = libraryEntryRepository;
        this.playlistRepository = playlistRepository;
        this.playlistTrackRepository = playlistTrackRepository;
    }

    @Transactional
    public PlaylistIngestReport ingest(String urlOrId, LibrarySource source) {

        Objects.requireNonNull(source, "source");
        String playlistId = urlParser.parsePlaylistId(urlOrId);
        SpotifyPlaylist spotifyPlaylist = playlistClient.getPlaylist(playlistId);
        return ingest(spotifyPlaylist, source);
    }

    /** Import playlisty o znanym już nagłówku — używane przy imporcie hurtem (tryb C). */
    @Transactional
    public PlaylistIngestReport ingest(SpotifyPlaylist spotifyPlaylist, LibrarySource source) {

        Objects.requireNonNull(spotifyPlaylist, "spotifyPlaylist");
        List<SpotifyPlaylistItem> items =
            playlistClient.getPlaylistItems(spotifyPlaylist.spotifyPlaylistId());
        Map<String, SpotifyTrackMetadata> uniqueTracks = uniqueTracks(items);

        upsertCatalog(uniqueTracks);
        int imported = saveLibraryEntries(uniqueTracks.keySet(), source);
        Playlist playlist = upsertPlaylist(spotifyPlaylist);
        replacePlaylistTracks(playlist, List.copyOf(uniqueTracks.keySet()));

        PlaylistIngestReport report = new PlaylistIngestReport(playlist.getId(),
            spotifyPlaylist.spotifyPlaylistId(), spotifyPlaylist.name(), uniqueTracks.size(),
            imported, uniqueTracks.size() - imported, skippedItems(items));
        log.info("Import playlisty '{}' ({}) zakończony: utwory={}, nowe={}, znane={}, pominięte={}",
            report.name(), report.spotifyPlaylistId(), report.tracks(), report.imported(),
            report.alreadyExisted(), report.skipped().size());
        return report;
    }

    private Map<String, SpotifyTrackMetadata> uniqueTracks(List<SpotifyPlaylistItem> items) {

        return items.stream()
            .filter(SpotifyPlaylistItem.Track.class::isInstance)
            .map(item -> ((SpotifyPlaylistItem.Track) item).metadata())
            .collect(Collectors.toMap(SpotifyTrackMetadata::spotifyId, Function.identity(),
                (first, second) -> first, LinkedHashMap::new));
    }

    private List<SkippedItem> skippedItems(List<SpotifyPlaylistItem> items) {

        return items.stream()
            .filter(SpotifyPlaylistItem.Unavailable.class::isInstance)
            .map(SpotifyPlaylistItem.Unavailable.class::cast)
            .map(item -> new SkippedItem(item.position(), item.reason()))
            .toList();
    }

    /**
     * Playlista niesie komplet metadanych grupy METADATA — nowe utwory dostają je
     * od razu, istniejącym uzupełniamy tylko puste pola (wzbogacanie jest właścicielem
     * już ustalonych wartości).
     */
    private void upsertCatalog(Map<String, SpotifyTrackMetadata> uniqueTracks) {

        Set<String> existingIds = uniqueTracks.isEmpty()
            ? Set.of()
            : trackCatalogRepository.findExistingIds(uniqueTracks.keySet());
        List<TrackCatalog> created = uniqueTracks.entrySet().stream()
            .filter(entry -> !existingIds.contains(entry.getKey()))
            .map(entry -> applyMetadata(
                new TrackCatalog(entry.getKey(), entry.getValue().title(), entry.getValue().artist()),
                entry.getValue()))
            .toList();
        trackCatalogRepository.saveAll(created);
        trackCatalogRepository.findAllById(existingIds)
            .forEach(track -> fillMissingMetadata(track, uniqueTracks.get(track.getSpotifyId())));
    }

    private TrackCatalog applyMetadata(TrackCatalog track, SpotifyTrackMetadata metadata) {

        track.setAlbum(metadata.album());
        track.setIsrc(metadata.isrc());
        track.setYear(metadata.year());
        track.setDurationMs(metadata.durationMs());
        track.setPopularity(metadata.popularity());
        track.setExplicit(metadata.explicit());
        track.setAlbumImageUrl(metadata.albumImageUrl());
        return track;
    }

    private void fillMissingMetadata(TrackCatalog track, SpotifyTrackMetadata metadata) {

        setIfMissing(track.getTitle(), metadata.title(), track::setTitle);
        setIfMissing(track.getArtist(), metadata.artist(), track::setArtist);
        setIfMissing(track.getAlbum(), metadata.album(), track::setAlbum);
        setIfMissing(track.getIsrc(), metadata.isrc(), track::setIsrc);
        setIfMissing(track.getYear(), metadata.year(), track::setYear);
        setIfMissing(track.getDurationMs(), metadata.durationMs(), track::setDurationMs);
        setIfMissing(track.getPopularity(), metadata.popularity(), track::setPopularity);
        setIfMissing(track.getExplicit(), metadata.explicit(), track::setExplicit);
        setIfMissing(track.getAlbumImageUrl(), metadata.albumImageUrl(), track::setAlbumImageUrl);
    }

    private <T> void setIfMissing(T current, T value, Consumer<T> setter) {

        if (Objects.isNull(current) && Objects.nonNull(value)) {
            setter.accept(value);
        }
    }

    private int saveLibraryEntries(Set<String> spotifyIds, LibrarySource source) {

        Set<String> alreadyInLibrary = spotifyIds.isEmpty()
            ? Set.of()
            : libraryEntryRepository.findExistingTrackIds(spotifyIds);
        List<LibraryEntry> entries = spotifyIds.stream()
            .filter(spotifyId -> !alreadyInLibrary.contains(spotifyId))
            .map(spotifyId -> new LibraryEntry(
                trackCatalogRepository.getReferenceById(spotifyId), source))
            .toList();
        libraryEntryRepository.saveAll(entries);
        return entries.size();
    }

    private Playlist upsertPlaylist(SpotifyPlaylist spotifyPlaylist) {

        Playlist playlist = playlistRepository
            .findBySpotifyPlaylistId(spotifyPlaylist.spotifyPlaylistId())
            .orElseGet(() -> {
                Playlist created = new Playlist(spotifyPlaylist.name());
                created.setSpotifyPlaylistId(spotifyPlaylist.spotifyPlaylistId());
                return created;
            });
        playlist.setName(spotifyPlaylist.name());
        return playlistRepository.save(playlist);
    }

    /** Kolejność na Spotify jest źródłem prawdy przy imporcie — wpisy odtwarzamy od zera. */
    private void replacePlaylistTracks(Playlist playlist, List<String> spotifyIds) {

        playlistTrackRepository.deleteByPlaylistId(playlist.getId());
        List<PlaylistTrack> tracks = IntStream.range(0, spotifyIds.size())
            .mapToObj(position -> new PlaylistTrack(playlist,
                trackCatalogRepository.getReferenceById(spotifyIds.get(position)), position))
            .toList();
        playlistTrackRepository.saveAll(tracks);
    }
}
