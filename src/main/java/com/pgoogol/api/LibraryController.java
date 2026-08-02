package com.pgoogol.api;

import com.pgoogol.library.LibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/library")
@Tag(name = "Library", description = "Biblioteka DJ-a — dane prywatne (D3)")
public class LibraryController {

    private final LibraryService libraryService;
    private final LibraryApiMapper mapper;

    public LibraryController(LibraryService libraryService, LibraryApiMapper mapper) {

        this.libraryService = libraryService;
        this.mapper = mapper;
    }

    @GetMapping("/tracks")
    @Operation(summary = "Lista biblioteki z pełnym rekordem katalogu (join)")
    public PageResponse<LibraryEntryResponse> listTracks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + CatalogController.DEFAULT_PAGE_SIZE) int size) {

        PageRequest pageRequest = PageRequest.of(Math.max(0, page),
            CatalogController.cappedSize(size), Sort.by("addedAt").descending());
        return PageResponse.of(libraryService.list(pageRequest), mapper::toResponse);
    }

    @GetMapping("/overview")
    @Operation(summary = "Przegląd biblioteki — rozkłady i pokrycie",
        description = "Rozkłady gatunków, tempa, energii, źródeł BPM i ocen, histogram BPM, "
            + "najczęstsi wykonawcy oraz przyrost biblioteki po miesiącach. Wszystko liczone "
            + "w bazie jednym wywołaniem (D27). Udział bpm_source mówi, ile biblioteki stoi "
            + "na faktach, a ile na estymacie LLM — wskaźnik z kryterium D19.")
    public LibraryOverviewResponse getOverview() {
        return mapper.toResponse(libraryService.overview());
    }

    @GetMapping("/tags")
    @Operation(summary = "Custom tagi użyte w bibliotece",
        description = "Posortowany słownik tagów DJ-a — podpowiedzi filtra wyszukiwarki (M3.2).")
    public List<String> listTags() {
        return libraryService.listTags();
    }

    @GetMapping("/tracks/{spotifyId}")
    @Operation(summary = "Pojedynczy wpis biblioteki z rekordem katalogu")
    public LibraryEntryResponse getTrack(@PathVariable String spotifyId) {
        return mapper.toResponse(libraryService.get(spotifyId));
    }

    @PostMapping("/tracks")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ręczne dodanie utworu do biblioteki (szkielet katalogu gdy brak)")
    public LibraryEntryResponse addTrack(@Valid @RequestBody AddLibraryTrackRequest request) {

        return mapper.toResponse(libraryService.add(
            request.spotifyId(), request.title(), request.artist(), request.album()));
    }

    @PatchMapping("/tracks/{spotifyId}")
    @Operation(summary = "Aktualizacja danych prywatnych DJ-a",
        description = "null = bez zmian; pusty string / pusta lista / rating 0 = wyczyszczenie "
            + "pola. Pole version jest wymagane (D29) — niezgodna wersja kończy się 409 "
            + "RESOURCE_MODIFIED, żeby cudza notatka nie zniknęła po cichu.")
    public LibraryEntryResponse updateTrack(@PathVariable String spotifyId,
                                            @Valid @RequestBody UpdateLibraryEntryRequest request) {

        return mapper.toResponse(libraryService.update(spotifyId, mapper.toUpdate(request)));
    }

    @DeleteMapping("/tracks/{spotifyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Usunięcie wpisu z biblioteki (rekord katalogu zostaje)")
    public void deleteTrack(@PathVariable String spotifyId) {
        libraryService.delete(spotifyId);
    }
}
