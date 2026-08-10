package com.pgoogol.enrichment.lyrics;

import com.pgoogol.catalog.LyricsStatus;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackLyrics;
import com.pgoogol.catalog.TrackLyricsRepository;
import com.pgoogol.enrichment.llm.LlmProperties;
import com.pgoogol.enrichment.llm.LlmRequestRejectedException;
import com.pgoogol.enrichment.llm.LlmResponseInvalidException;
import com.pgoogol.enrichment.llm.LyricsTranslation;
import com.pgoogol.enrichment.llm.LyricsTranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Grupa pól LYRICS (M6.1/D32): pobranie tekstu z LRCLIB, tłumaczenie na polski
 * i interpretacja. Dwa różne źródła kosztu — LRCLIB jest darmowe, model płatny —
 * więc tekst pobieramy raz i przechowujemy, a tłumaczenie liczy się tylko dla
 * utworów, które go jeszcze nie mają.
 *
 * <p>{@code force} rozstrzyga, co znaczy „wzbogać ten utwór": zlecenie na
 * konkretne utwory (SINGLE/SELECTED) jest świadomym poleceniem DJ-a i pobiera
 * tekst od nowa, a przebieg masowy (MISSING) omija utwory rozstrzygnięte —
 * inaczej każdy przebieg pytałby LRCLIB o utwory, o których wiadomo, że tekstu
 * nie mają.</p>
 */
@Component
public class LyricsEnricher {

    private static final Logger log = LoggerFactory.getLogger(LyricsEnricher.class);

    private final LrcLibClient lrcLibClient;
    private final LyricsTranslationService translationService;
    private final TrackLyricsRepository repository;
    private final LlmProperties llmProperties;

    public LyricsEnricher(LrcLibClient lrcLibClient, LyricsTranslationService translationService,
                          TrackLyricsRepository repository, LlmProperties llmProperties) {

        this.lrcLibClient = lrcLibClient;
        this.translationService = translationService;
        this.repository = repository;
        this.llmProperties = llmProperties;
    }

    public void enrich(TrackCatalog track, boolean force) {

        Objects.requireNonNull(track, "track");
        if (isBlank(track.getArtist()) || isBlank(track.getTitle())) {
            log.warn("Utwór {} nie ma wykonawcy albo tytułu — pomijam tekst", track.getSpotifyId());
            return;
        }
        Optional<TrackLyrics> stored = repository.findById(track.getSpotifyId());
        if (!force && stored.filter(TrackLyrics::isResolved).isPresent()) {
            return;
        }
        TrackLyrics lyrics = stored.orElseGet(
            () -> new TrackLyrics(track.getSpotifyId(), LyricsStatus.FETCHED));
        String original = originalLyrics(track, lyrics, force);
        if (Objects.isNull(original)) {
            repository.save(lyrics);
            return;
        }
        translateOrKeepText(track, lyrics, original);
        repository.save(lyrics);
    }

    /**
     * Jeden utwór nie do przetłumaczenia nie może wywrócić przebiegu po całej
     * bibliotece (ta sama zasada co przy imporcie hurtem — D31). Tekst zostaje
     * zapisany ze statusem {@code FETCHED}, więc kolejny przebieg spróbuje samego
     * tłumaczenia, bez ponownego pytania LRCLIB.
     *
     * <p>Dotyczy żądań odrzuconych „co do sztuki" (za długi tekst, brak pamięci
     * na karcie) oraz odpowiedzi, z których nie da się wyczytać tłumaczenia
     * (ucięcie na limicie tokenów, własny format zamiast tego z promptu). Zła
     * konfiguracja providera leci dalej i wywraca job — inaczej przebieg po 2500
     * utworach zakończyłby się sukcesem, nie tłumacząc niczego.</p>
     */
    private void translateOrKeepText(TrackCatalog track, TrackLyrics lyrics, String original) {

        try {
            translate(track, lyrics, original);
        } catch (LlmRequestRejectedException ex) {
            if (!ex.isRequestSpecific()) {
                throw ex;
            }
            keepTextForRetry(track, lyrics, original, ex.getMessage());
        } catch (LlmResponseInvalidException ex) {
            // model odpowiedział, ale nie da się z tego zrobić tłumaczenia:
            // ucięta odpowiedź albo własny format zamiast tego z promptu
            keepTextForRetry(track, lyrics, original, ex.getMessage());
        }
    }

    private void keepTextForRetry(TrackCatalog track, TrackLyrics lyrics, String original,
                                  String reason) {

        log.warn("Nie udało się przetłumaczyć utworu {} ({} znaków tekstu) — tekst zostaje "
            + "do ponowienia. {}", track.getSpotifyId(), original.length(), reason);
        lyrics.setStatus(LyricsStatus.FETCHED);
    }

    /**
     * Zwraca tekst do przetłumaczenia albo {@code null}, gdy nie ma czego
     * tłumaczyć (brak w LRCLIB, nagranie instrumentalne) — stan zapisany
     * w encji jest wtedy odpowiedzią ostateczną i wywołujący ją utrwala.
     */
    private String originalLyrics(TrackCatalog track, TrackLyrics lyrics, boolean force) {

        if (!force && Objects.nonNull(lyrics.getOriginalLyrics())) {
            // tekst mamy z poprzedniego przebiegu, padło dopiero tłumaczenie
            return lyrics.getOriginalLyrics();
        }
        Optional<LrcLibLyrics> found = lrcLibClient.find(
            track.getArtist(), track.getTitle(), track.getAlbum(), track.getDurationMs());
        lyrics.setFetchedAt(Instant.now());
        if (found.isEmpty()) {
            log.debug("LRCLIB nie zna tekstu utworu {} — zapisuję potwierdzony brak",
                track.getSpotifyId());
            resetTranslation(lyrics, LyricsStatus.NOT_FOUND);
            lyrics.setOriginalLyrics(null);
            lyrics.setLrclibId(null);
            return null;
        }
        LrcLibLyrics fetched = found.get();
        lyrics.setLrclibId(fetched.lrclibId());
        if (fetched.instrumental()) {
            resetTranslation(lyrics, LyricsStatus.INSTRUMENTAL);
            lyrics.setOriginalLyrics(null);
            return null;
        }
        lyrics.setStatus(LyricsStatus.FETCHED);
        lyrics.setOriginalLyrics(fetched.plainLyrics());
        return fetched.plainLyrics();
    }

    private void translate(TrackCatalog track, TrackLyrics lyrics, String original) {

        LyricsTranslation translation = translationService.translate(track, original);
        lyrics.setSourceLanguage(translation.sourceLanguage());
        lyrics.setTranslationPl(translation.translationPl());
        lyrics.setInterpretationPl(translation.interpretationPl());
        lyrics.setTranslatedAt(Instant.now());
        lyrics.setModelUsed(llmProperties.model());
        lyrics.setPromptVersion(llmProperties.lyrics().promptVersionNumber().orElse(null));
        // model bywa gadatliwy, ale bez tłumaczenia utwór nie jest rozstrzygnięty
        // i ma wrócić do kolejki przy kolejnym przebiegu
        lyrics.setStatus(Objects.isNull(translation.translationPl())
            ? LyricsStatus.FETCHED
            : LyricsStatus.TRANSLATED);
    }

    /** Ponowne pobranie kasuje poprzednie tłumaczenie — dotyczyło innego tekstu. */
    private void resetTranslation(TrackLyrics lyrics, LyricsStatus status) {

        lyrics.setStatus(status);
        lyrics.setSourceLanguage(null);
        lyrics.setTranslationPl(null);
        lyrics.setInterpretationPl(null);
        lyrics.setTranslatedAt(null);
        lyrics.setModelUsed(null);
        lyrics.setPromptVersion(null);
    }

    private boolean isBlank(String value) {
        return Objects.isNull(value) || value.isBlank();
    }
}
