package com.pgoogol.api;

import com.pgoogol.catalog.CamelotKey;
import com.pgoogol.catalog.CatalogSearchCriteria;
import com.pgoogol.catalog.CatalogSearchCriteria.HarmonicFilter;
import com.pgoogol.catalog.CatalogSearchCriteria.MetricFilter;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.TempoClass;
import com.pgoogol.common.ValidationException;
import com.pgoogol.playlist.SetProposal;
import com.pgoogol.playlist.SetProposalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Objects;

@RestController
@RequestMapping("/api/sets")
@Tag(name = "Sets", description = "Generator propozycji setu (D26)")
public class SetController {

    private final SetProposalService setProposalService;
    private final CatalogApiMapper catalogApiMapper;

    public SetController(SetProposalService setProposalService, CatalogApiMapper catalogApiMapper) {

        this.setProposalService = setProposalService;
        this.catalogApiMapper = catalogApiMapper;
    }

    @PostMapping("/propose")
    @Operation(summary = "Propozycja setu na zadany czas",
        description = "Układa set z utworów spełniających te same filtry co wyszukiwarka: "
            + "krzywa wieczoru (rozgrzewka 25% / środek 30% / szczyt 30% / zamknięcie 15%), "
            + "utwór raz w secie, ten sam wykonawca nie częściej niż raz na 30 minut, "
            + "kary za skok BPM, zderzenie tonacji i brak oceny. "
            + "NICZEGO NIE ZAPISUJE (D26) — playlistę zakłada DJ przez /api/playlists. "
            + "Ten sam seed daje tę samą propozycję.")
    public SetProposalResponse propose(@Valid @RequestBody SetProposalRequest request) {

        SetProposal proposal = setProposalService.propose(
            criteria(request), request.targetMinutes(), request.seed());
        return new SetProposalResponse(
            proposal.tracks().size(),
            proposal.totalDurationMs(),
            proposal.targetDurationMs(),
            proposal.seed(),
            proposal.notes(),
            proposal.tracks().stream()
                .map(track -> new SetProposalResponse.ProposedTrackResponse(
                    track.position(),
                    Objects.toString(track.djSlot(), null),
                    catalogApiMapper.toResponse(track.track())))
                .toList());
    }

    private CatalogSearchCriteria criteria(SetProposalRequest request) {

        return new CatalogSearchCriteria(
            request.search(),
            parseEnum(GenreFamily.class, request.genreFamily(), "genreFamily"),
            request.bpmMin(),
            request.bpmMax(),
            parseEnum(TempoClass.class, request.tempoClass(), "tempoClass"),
            request.energy(),
            request.inLibrary(),
            request.ratingMin(),
            request.tag(),
            harmonicFilter(request),
            new MetricFilter(request.valenceMin(), request.valenceMax(),
                request.instrumentalMin(), request.livenessMax()));
    }

    private HarmonicFilter harmonicFilter(SetProposalRequest request) {

        if (Objects.isNull(request.camelot()) || request.camelot().isBlank()) {
            return null;
        }
        return CamelotKey.ofLabel(request.camelot())
            .map(key -> new HarmonicFilter(key,
                !Boolean.FALSE.equals(request.camelotCompatible())))
            .orElseThrow(() -> new ValidationException("INVALID_CAMELOT",
                "Nieprawidłowa pozycja koła Camelot: '%s' (oczekiwano 1A–12B)"
                    .formatted(request.camelot())));
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {

        if (Objects.isNull(raw) || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("INVALID_PARAMETER",
                "Nieprawidłowa wartość '%s' dla pola %s".formatted(raw, field));
        }
    }
}
