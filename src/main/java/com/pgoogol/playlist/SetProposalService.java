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
 * Buduje pulę kandydatów i oddaje ją generatorowi (M4.2). Nic nie zapisuje —
 * propozycja to materiał do obejrzenia, a playlistę zakłada DJ istniejącą
 * drogą (D26).
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

    public SetProposalService(CatalogService catalogService,
                              LibraryEntryRepository libraryEntryRepository,
                              DjSlotCalculator djSlotCalculator,
                              SetGenerator setGenerator) {

        this.catalogService = catalogService;
        this.libraryEntryRepository = libraryEntryRepository;
        this.djSlotCalculator = djSlotCalculator;
        this.setGenerator = setGenerator;
    }

    @Transactional(readOnly = true)
    public SetProposal propose(CatalogSearchCriteria criteria, int targetMinutes,
                               @Nullable Long seed) {

        Objects.requireNonNull(criteria, "criteria");
        validateTarget(targetMinutes);
        List<SetCandidate> candidates = candidates(criteria);
        if (candidates.isEmpty()) {
            throw new ValidationException("SET_NO_CANDIDATES",
                "Żaden utwór nie przeszedł filtrów — poluzuj kryteria puli");
        }
        SetProposal proposal =
            setGenerator.generate(candidates, Duration.ofMinutes(targetMinutes), seed);
        log.info("Propozycja setu: {} utworów na {} min z puli {} (seed {})",
            proposal.tracks().size(), targetMinutes, candidates.size(), proposal.seed());
        return proposal;
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
