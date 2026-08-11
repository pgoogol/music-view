package com.pgoogol.playlist;

import com.pgoogol.catalog.CatalogSearchCriteria;
import com.pgoogol.catalog.CatalogService;
import com.pgoogol.catalog.CatalogSort;
import com.pgoogol.catalog.CatalogSortOrder;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.common.ValidationException;
import com.pgoogol.library.LibraryEntryRepository;
import com.pgoogol.library.TrackDjData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Buduje pulę kandydatów i oddaje ją generatorowi (M4.2) albo dobieraniu do
 * gotowego setu (M4.4). Nic nie zapisuje — propozycja to materiał do obejrzenia,
 * a skład setu zmienia DJ istniejącą drogą (D26/D32).
 */
@Service
public class SetProposalService {

    /** Sufit puli: biblioteka jednego DJ-a ma rząd 2500 utworów (D2). */
    static final int MAX_CANDIDATES = 2000;
    static final int MIN_TARGET_MINUTES = 15;
    static final int MAX_TARGET_MINUTES = 12 * 60;

    private static final Logger log = LoggerFactory.getLogger(SetProposalService.class);

    private final CatalogService catalogService;
    private final LibraryEntryRepository libraryEntryRepository;
    private final DjSlotCalculator djSlotCalculator;
    private final SetGenerator setGenerator;
    private final SetSuggester setSuggester;
    private final SetRules setRules;
    private final PlaylistService playlistService;

    public SetProposalService(CatalogService catalogService,
                              LibraryEntryRepository libraryEntryRepository,
                              DjSlotCalculator djSlotCalculator,
                              SetGenerator setGenerator,
                              SetSuggester setSuggester,
                              SetRules setRules,
                              PlaylistService playlistService) {

        this.catalogService = catalogService;
        this.libraryEntryRepository = libraryEntryRepository;
        this.djSlotCalculator = djSlotCalculator;
        this.setGenerator = setGenerator;
        this.setSuggester = setSuggester;
        this.setRules = setRules;
        this.playlistService = playlistService;
    }

    @Transactional(readOnly = true)
    public SetProposal propose(CatalogSearchCriteria criteria, int targetMinutes, SetCurve curve,
                               @Nullable Long seed) {

        Objects.requireNonNull(criteria, "criteria");
        validateTarget(targetMinutes);
        List<SetCandidate> candidates = requireCandidates(criteria);
        SetProposal proposal =
            setGenerator.generate(candidates, Duration.ofMinutes(targetMinutes), curve, seed);
        log.info("Propozycja setu ({}): {} utworów na {} min z puli {} (seed {})",
            curve, proposal.tracks().size(), targetMinutes, candidates.size(), proposal.seed());
        return proposal;
    }

    /**
     * Uzupełnia gotowy set do zadanego czasu (M4.4). Utwory, które już w nim są,
     * zajmują początek wieczoru i nie wracają w propozycji — wynik to sam dalszy
     * ciąg, dopisywany na koniec.
     */
    @Transactional(readOnly = true)
    public SetFill fill(Long playlistId, CatalogSearchCriteria criteria, int targetMinutes,
                        SetCurve curve, @Nullable Long seed) {

        Objects.requireNonNull(criteria, "criteria");
        validateTarget(targetMinutes);
        List<SetCandidate> current = currentSet(playlistId);
        List<SetCandidate> candidates = requireCandidates(criteria);
        SetProposal proposal = setGenerator.extend(
            candidates, current, Duration.ofMinutes(targetMinutes), curve, seed);
        log.info("Uzupełnienie setu {} ({}): +{} utworów do {} min z puli {} (seed {})",
            playlistId, curve, proposal.tracks().size(), targetMinutes, candidates.size(),
            proposal.seed());
        return new SetFill(current.size(), durationMs(current), proposal);
    }

    /**
     * Dobiera kandydatów na wskazane miejsce w secie (M4.4); {@code position}
     * bez wartości znaczy „na koniec".
     */
    @Transactional(readOnly = true)
    public SetSuggestions suggest(Long playlistId, CatalogSearchCriteria criteria,
                                  @Nullable Integer position, @Nullable Integer limit) {

        Objects.requireNonNull(criteria, "criteria");
        List<SetCandidate> current = currentSet(playlistId);
        int gap = validatePosition(position, current.size());
        List<SetSuggestion> suggestions = setSuggester.suggest(
            requireCandidates(criteria), current, gap,
            Optional.ofNullable(limit).orElse(SetSuggester.DEFAULT_LIMIT));
        log.info("Dobieranie do setu {} na pozycję {}: {} propozycji",
            playlistId, gap, suggestions.size());
        return new SetSuggestions(gap, suggestions);
    }

    /**
     * Obecny skład setu jako pula „już zajęta". Ocena DJ-a zostaje pusta celowo:
     * z utworów, które są w secie, liczy się tylko czas, wykonawca, tempo, tonacja
     * i slot — oceniamy kandydatów, nie to, co DJ już wybrał.
     */
    private List<SetCandidate> currentSet(Long playlistId) {

        return playlistService.get(playlistId).tracks().stream()
            .map(planned -> new SetCandidate(planned.track(), planned.djSlot(), null))
            .toList();
    }

    private long durationMs(List<SetCandidate> tracks) {
        return tracks.stream().mapToLong(setRules::durationMs).sum();
    }

    private List<SetCandidate> requireCandidates(CatalogSearchCriteria criteria) {

        List<SetCandidate> candidates = candidates(criteria);
        if (candidates.isEmpty()) {
            throw new ValidationException("SET_NO_CANDIDATES",
                "Żaden utwór nie przeszedł filtrów — poluzuj kryteria puli");
        }
        return candidates;
    }

    private int validatePosition(@Nullable Integer position, int setSize) {

        int gap = Optional.ofNullable(position).orElse(setSize);
        if (gap < 0 || gap > setSize) {
            throw new ValidationException("SET_POSITION_OUT_OF_RANGE",
                "Pozycja %d jest poza setem (dozwolone 0–%d)".formatted(gap, setSize));
        }
        return gap;
    }

    private List<SetCandidate> candidates(CatalogSearchCriteria criteria) {

        List<TrackCatalog> tracks = catalogService.search(
                criteria,
                CatalogSortOrder.of(CatalogSort.RELEVANCE, Sort.Direction.ASC),
                PageRequest.of(0, MAX_CANDIDATES))
            .getContent();
        Map<String, TrackDjData> djData = djData(tracks);
        return tracks.stream()
            .map(track -> toCandidate(track, djData.get(track.getSpotifyId())))
            .toList();
    }

    private SetCandidate toCandidate(TrackCatalog track, @Nullable TrackDjData djData) {

        String override = Optional.ofNullable(djData).map(TrackDjData::djSlotOverride).orElse(null);
        DjSlot slot = DjSlot.parse(override)
            .or(() -> djSlotCalculator.calculate(track))
            .orElse(null);
        return new SetCandidate(track, slot,
            Optional.ofNullable(djData).map(TrackDjData::rating).orElse(null));
    }

    private Map<String, TrackDjData> djData(List<TrackCatalog> tracks) {

        if (tracks.isEmpty()) {
            return Map.of();
        }
        List<String> spotifyIds = tracks.stream().map(TrackCatalog::getSpotifyId).toList();
        return libraryEntryRepository.findDjData(spotifyIds).stream()
            .collect(Collectors.toMap(TrackDjData::spotifyId, Function.identity()));
    }

    private void validateTarget(int targetMinutes) {

        if (targetMinutes < MIN_TARGET_MINUTES || targetMinutes > MAX_TARGET_MINUTES) {
            throw new ValidationException("SET_TARGET_OUT_OF_RANGE",
                "Długość setu musi mieścić się w %d–%d minutach"
                    .formatted(MIN_TARGET_MINUTES, MAX_TARGET_MINUTES));
        }
    }
}
