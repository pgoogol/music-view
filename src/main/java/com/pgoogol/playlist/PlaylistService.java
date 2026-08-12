package com.pgoogol.playlist;

import com.pgoogol.catalog.ManualMetrics;
import com.pgoogol.catalog.ManualMetricsRepository;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogRepository;
import com.pgoogol.common.ConflictException;
import com.pgoogol.common.NotFoundException;
import com.pgoogol.common.ValidationException;
import com.pgoogol.library.LibraryEntryRepository;
import com.pgoogol.library.TrackSlotOverride;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Planowanie setów (M2.3): CRUD playlist, skład i kolejność utworów oraz slot
 * wieczoru per utwór (D9 — liczony, nie zapisywany; override DJ-a wygrywa).
 * Pozycje trzymamy zwarte (0..n-1), żeby kolejność z frontu i eksport na Spotify
 * (M2.4) czytały to samo.
 */
@Service
public class PlaylistService {

    private static final Logger log = LoggerFactory.getLogger(PlaylistService.class);

    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;
    private final TrackCatalogRepository trackCatalogRepository;
    private final LibraryEntryRepository libraryEntryRepository;
    private final ManualMetricsRepository manualMetricsRepository;
    private final DjSlotCalculator djSlotCalculator;
    private final EntityManager entityManager;

    public PlaylistService(PlaylistRepository playlistRepository,
                           PlaylistTrackRepository playlistTrackRepository,
                           TrackCatalogRepository trackCatalogRepository,
                           LibraryEntryRepository libraryEntryRepository,
                           ManualMetricsRepository manualMetricsRepository,
                           DjSlotCalculator djSlotCalculator,
                           EntityManager entityManager) {

        this.playlistRepository = playlistRepository;
        this.playlistTrackRepository = playlistTrackRepository;
        this.trackCatalogRepository = trackCatalogRepository;
        this.libraryEntryRepository = libraryEntryRepository;
        this.manualMetricsRepository = manualMetricsRepository;
        this.djSlotCalculator = djSlotCalculator;
        this.entityManager = entityManager;
    }

    @Transactional
    public PlaylistSummary create(String name) {

        Playlist playlist = playlistRepository.save(new Playlist(requireName(name)));
        log.info("Utworzono playlistę '{}' ({})", playlist.getName(), playlist.getId());
        return new PlaylistSummary(playlist.getId(), playlist.getName(),
            playlist.getSpotifyPlaylistId(), playlist.getCreatedAt(), 0, playlist.getVersion());
    }

    @Transactional(readOnly = true)
    public List<PlaylistSummary> list() {
        return playlistRepository.findAllSummaries();
    }

    @Transactional(readOnly = true)
    public PlaylistPlan get(Long playlistId) {
        return plan(requirePlaylist(playlistId));
    }

    @Transactional
    public PlaylistSummary rename(Long playlistId, String name, Integer expectedVersion) {

        Playlist playlist = requirePlaylist(playlistId);
        requireCurrentVersion(playlist, expectedVersion);
        playlist.setName(requireName(name));
        return new PlaylistSummary(playlist.getId(), playlist.getName(),
            playlist.getSpotifyPlaylistId(), playlist.getCreatedAt(),
            playlistTrackRepository.countByPlaylistId(playlistId), playlist.getVersion());
    }

    @Transactional
    public void delete(Long playlistId) {

        playlistRepository.delete(requirePlaylist(playlistId));
        log.info("Usunięto playlistę {} (katalog i biblioteka bez zmian)", playlistId);
    }

    /** Dokłada utwór na koniec setu; ten sam utwór może być na playliście raz (D17). */
    @Transactional
    public PlaylistPlan addTrack(Long playlistId, String spotifyId) {

        Playlist playlist = requirePlaylist(playlistId);
        TrackCatalog track = trackCatalogRepository.findById(Objects.requireNonNull(spotifyId))
            .orElseThrow(() -> new NotFoundException("TRACK_NOT_FOUND",
                "Utworu '%s' nie ma w katalogu".formatted(spotifyId)));
        if (playlistTrackRepository.findByPlaylistIdAndTrackSpotifyId(playlistId, spotifyId)
                .isPresent()) {
            throw new ConflictException("PLAYLIST_TRACK_EXISTS",
                "Utwór '%s' jest już na tej playliście".formatted(spotifyId));
        }
        int position = (int) playlistTrackRepository.countByPlaylistId(playlistId);
        playlistTrackRepository.save(new PlaylistTrack(playlist, track, position));
        bumpVersion(playlist);
        return plan(playlist);
    }

    @Transactional
    public PlaylistPlan removeTrack(Long playlistId, String spotifyId) {

        Playlist playlist = requirePlaylist(playlistId);
        PlaylistTrack entry = playlistTrackRepository
            .findByPlaylistIdAndTrackSpotifyId(playlistId, spotifyId)
            .orElseThrow(() -> new NotFoundException("PLAYLIST_TRACK_NOT_FOUND",
                "Utworu '%s' nie ma na playliście %d".formatted(spotifyId, playlistId)));
        playlistTrackRepository.delete(entry);
        playlistTrackRepository.flush();
        renumber(playlistId);
        bumpVersion(playlist);
        return plan(playlist);
    }

    /**
     * Nowa kolejność setu (drag&drop we froncie) — lista musi być permutacją
     * obecnego składu, żeby przypadkowe pominięcie utworu nie skasowało go po cichu.
     */
    @Transactional
    public PlaylistPlan reorder(Long playlistId, List<String> spotifyIds, Integer expectedVersion) {

        Playlist playlist = requirePlaylist(playlistId);
        requireCurrentVersion(playlist, expectedVersion);
        Objects.requireNonNull(spotifyIds, "spotifyIds");
        Map<String, PlaylistTrack> current = playlistTrackRepository
            .findAllByPlaylistIdOrderByPositionAsc(playlistId).stream()
            .collect(Collectors.toMap(entry -> entry.getTrack().getSpotifyId(), Function.identity()));
        Set<String> requested = new LinkedHashSet<>(spotifyIds);
        if (requested.size() != spotifyIds.size() || !requested.equals(current.keySet())) {
            throw new ValidationException("PLAYLIST_ORDER_MISMATCH",
                "Nowa kolejność musi zawierać dokładnie te same utwory co playlista (%d szt.)"
                    .formatted(current.size()));
        }
        IntStream.range(0, spotifyIds.size())
            .forEach(position -> current.get(spotifyIds.get(position)).setPosition(position));
        bumpVersion(playlist);
        return plan(playlist);
    }

    /**
     * Wersja siedzi na agregacie (D29): zmiana wierszy {@code playlist_track} musi
     * podbić {@code playlist.version}, a {@code @Version} na encji nadrzędnej sama
     * tego nie zrobi — stąd jawny {@code OPTIMISTIC_FORCE_INCREMENT}.
     */
    private void bumpVersion(Playlist playlist) {
        entityManager.lock(playlist, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    /**
     * Nieświeży klient (D29): kolejność albo nazwa przyszły z widoku sprzed cudzej
     * zmiany. Brak wersji w żądaniu traktujemy jak niezgodność — kontrakt jej wymaga.
     */
    private void requireCurrentVersion(Playlist playlist, Integer expectedVersion) {

        if (Objects.isNull(expectedVersion) || playlist.getVersion() != expectedVersion) {
            throw new ConflictException("RESOURCE_MODIFIED",
                ("Set zmienił się w innym miejscu (wersja %d, przysłano %s) — "
                    + "odśwież i spróbuj ponownie")
                    .formatted(playlist.getVersion(), expectedVersion));
        }
    }

    private void renumber(Long playlistId) {

        List<PlaylistTrack> tracks =
            playlistTrackRepository.findAllByPlaylistIdOrderByPositionAsc(playlistId);
        IntStream.range(0, tracks.size())
            .forEach(position -> tracks.get(position).setPosition(position));
    }

    private PlaylistPlan plan(Playlist playlist) {

        List<PlaylistTrack> tracks =
            playlistTrackRepository.findAllWithTrackByPlaylistId(playlist.getId());
        Map<String, String> overrides = slotOverrides(tracks);
        Map<String, ManualMetrics> metrics = metrics(tracks);
        List<PlannedTrack> planned = tracks.stream()
            .map(entry -> toPlanned(entry,
                overrides.get(entry.getTrack().getSpotifyId()),
                metrics.get(entry.getTrack().getSpotifyId())))
            .toList();
        return new PlaylistPlan(playlist, planned);
    }

    private PlannedTrack toPlanned(PlaylistTrack playlistTrack, String override,
                                   @Nullable ManualMetrics metrics) {

        TrackCatalog track = playlistTrack.getTrack();
        DjSlot slot = DjSlot.parse(override)
            .or(() -> djSlotCalculator.calculate(track))
            .orElse(null);
        return new PlannedTrack(playlistTrack.getPosition(), track, slot, override, metrics);
    }

    /** Metryki z pliku (D24) — ostrzeżenia planera i falowe tryby układania (D34). */
    private Map<String, ManualMetrics> metrics(List<PlaylistTrack> tracks) {

        Set<String> spotifyIds = tracks.stream()
            .map(entry -> entry.getTrack().getSpotifyId())
            .collect(Collectors.toSet());
        return spotifyIds.isEmpty()
            ? Map.of()
            : manualMetricsRepository.findBySpotifyIdIn(spotifyIds).stream()
                .collect(Collectors.toMap(ManualMetrics::getSpotifyId, Function.identity()));
    }

    private Map<String, String> slotOverrides(List<PlaylistTrack> tracks) {

        Set<String> spotifyIds = tracks.stream()
            .map(entry -> entry.getTrack().getSpotifyId())
            .collect(Collectors.toSet());
        if (spotifyIds.isEmpty()) {
            return Map.of();
        }
        return libraryEntryRepository.findSlotOverrides(spotifyIds).stream()
            .collect(Collectors.toMap(
                TrackSlotOverride::spotifyId, TrackSlotOverride::djSlotOverride));
    }

    private Playlist requirePlaylist(Long playlistId) {

        Objects.requireNonNull(playlistId, "playlistId");
        return playlistRepository.findById(playlistId)
            .orElseThrow(() -> new NotFoundException("PLAYLIST_NOT_FOUND",
                "Playlisty %d nie ma w bazie".formatted(playlistId)));
    }

    private String requireName(String name) {

        if (Objects.isNull(name) || name.isBlank()) {
            throw new ValidationException("PLAYLIST_NAME_EMPTY", "Nazwa playlisty nie może być pusta");
        }
        return name.trim();
    }
}
