package com.pgoogol.api;

import com.pgoogol.catalog.CamelotKey;
import com.pgoogol.catalog.CatalogSearchCriteria;
import com.pgoogol.catalog.CatalogSearchCriteria.HarmonicFilter;
import com.pgoogol.catalog.CatalogSearchCriteria.MetricFilter;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.TempoClass;
import com.pgoogol.common.ValidationException;
import com.pgoogol.playlist.SetCurve;
import com.pgoogol.playlist.SetFill;
import com.pgoogol.playlist.SetProposal;
import com.pgoogol.playlist.SetProposalService;
import com.pgoogol.playlist.SetSuggestions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Objects;

@RestController
@RequestMapping("/api/sets")
@Tag(name = "Sets", description = "Generator setu i domykanie gotowego setu (D26, D32)")
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
            + "kształt wieczoru wg profilu curve (STANDARD 25/30/30/15, WEDDING 30/30/25/15, "
            + "CLUB 15/25/45/15, EVEN 25/25/25/25 — udziały faz D9), "
            + "utwór raz w secie, ten sam wykonawca nie częściej niż raz na 30 minut, "
            + "kary za skok BPM, zderzenie tonacji i brak oceny. "
            + "NICZEGO NIE ZAPISUJE (D26) — playlistę zakłada DJ przez /api/playlists. "
            + "Ten sam seed daje tę samą propozycję.")
    public SetProposalResponse propose(@Valid @RequestBody SetProposalRequest request) {

        SetProposal proposal = setProposalService.propose(
            criteria(request), request.targetMinutes(), curve(request.curve()), request.seed());
        return new SetProposalResponse(
            proposal.tracks().size(),
            proposal.totalDurationMs(),
            proposal.targetDurationMs(),
            proposal.seed(),
            proposal.notes(),
            proposal.tracks().stream().map(this::toResponse).toList());
    }

    @PostMapping("/{playlistId}/fill")
    @Operation(summary = "Uzupełnij gotowy set do zadanego czasu",
        description = "Dokłada dalszy ciąg do setu, który już stoi: utwory z setu zajmują "
            + "początek wieczoru (liczą się do czasu, blokują powtórkę utworu i odstęp "
            + "wykonawcy), a wynikiem jest sama końcówka. targetMinutes to długość CAŁEGO "
            + "wieczoru, nie tego, co dochodzi. NICZEGO NIE ZAPISUJE (D32) — utwory dopisuje "
            + "DJ przez /api/playlists/{id}/tracks. Ten sam seed daje ten sam dalszy ciąg.")
    public SetFillResponse fill(@PathVariable Long playlistId,
                                @Valid @RequestBody SetFillRequest request) {

        SetFill fill = setProposalService.fill(
            playlistId, criteria(request), request.targetMinutes(), curve(request.curve()),
            request.seed());
        SetProposal proposal = fill.proposal();
        return new SetFillResponse(
            fill.currentTrackCount(),
            fill.currentDurationMs(),
            proposal.tracks().size(),
            proposal.totalDurationMs(),
            proposal.targetDurationMs(),
            proposal.seed(),
            proposal.notes(),
            proposal.tracks().stream().map(this::toResponse).toList());
    }

    @PostMapping("/{playlistId}/suggest")
    @Operation(summary = "Dobierz utwór na wskazane miejsce w secie",
        description = "Kandydaci na jedną lukę w gotowym secie, uszeregowani od najlepiej "
            + "pasującego: kara za przejście liczona od utworu przed luką i do utworu za nią, "
            + "utwór już w secie odpada, wykonawca nie wraca przed upływem 30 minut. "
            + "Bez losowania — ta sama luka daje tę samą odpowiedź. "
            + "position: 0 przed pierwszym utworem, brak wartości = na koniec.")
    public SetSuggestionResponse suggest(@PathVariable Long playlistId,
                                         @Valid @RequestBody SetSuggestionRequest request) {

        SetSuggestions suggestions = setProposalService.suggest(
            playlistId, criteria(request), request.position(), request.limit());
        return new SetSuggestionResponse(
            suggestions.position(),
            suggestions.suggestions().stream()
                .map(suggestion -> new SetSuggestionResponse.SuggestedTrackResponse(
                    Objects.toString(suggestion.djSlot(), null),
                    suggestion.bpmDelta(),
                    suggestion.harmonic(),
                    catalogApiMapper.toResponse(suggestion.track())))
                .toList());
    }

    /** Brak profilu w żądaniu to najczęstszy przypadek — domyślny przebieg z D26. */
    private SetCurve curve(String raw) {

        if (Objects.isNull(raw) || raw.isBlank()) {
            return SetCurve.STANDARD;
        }
        return SetCurve.parse(raw).orElseThrow(() -> new ValidationException("INVALID_SET_CURVE",
            "Nieprawidłowy profil wieczoru: '%s' (oczekiwano STANDARD, WEDDING, CLUB albo EVEN)"
                .formatted(raw)));
    }

    private SetProposalResponse.ProposedTrackResponse toResponse(SetProposal.ProposedTrack track) {

        return new SetProposalResponse.ProposedTrackResponse(
            track.position(),
            Objects.toString(track.djSlot(), null),
            catalogApiMapper.toResponse(track.track()));
    }

    private CatalogSearchCriteria criteria(SetFilters request) {

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

    private HarmonicFilter harmonicFilter(SetFilters request) {

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
