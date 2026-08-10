package com.pgoogol.enrichment.lyrics;

import com.pgoogol.catalog.LyricsStatus;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogFixtures;
import com.pgoogol.catalog.TrackLyrics;
import com.pgoogol.catalog.TrackLyricsRepository;
import com.pgoogol.enrichment.llm.LlmProperties;
import com.pgoogol.enrichment.llm.LlmRequestRejectedException;
import com.pgoogol.enrichment.llm.LlmResponseInvalidException;
import com.pgoogol.enrichment.llm.LyricsTranslation;
import com.pgoogol.enrichment.llm.LyricsTranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Grupa pól LYRICS (M6.1/D32): co się dzieje z tekstem, którego nie ma, którego
 * nie da się przetłumaczyć i który już mamy. Teksty w testach są wymyślone.
 */
class LyricsEnricherTest {

    private static final String LYRICS = "Pierwszy wers testowego tekstu\nDrugi wers testowego tekstu";

    private final LrcLibClient lrcLibClient = mock(LrcLibClient.class);
    private final LyricsTranslationService translationService = mock(LyricsTranslationService.class);
    private final TrackLyricsRepository repository = mock(TrackLyricsRepository.class);

    private LyricsEnricher enricher;

    @BeforeEach
    void setUp() {

        LlmProperties properties = new LlmProperties("openai", null, "klucz", "test-model",
            "v1", 5, 2, 2048, 0.2, 500, null, new LlmProperties.Lyrics("v2", 6000, 3000));
        enricher = new LyricsEnricher(lrcLibClient, translationService, repository, properties);
        given(repository.findById(anyString())).willReturn(Optional.empty());
    }

    @Test
    void enrich_whenLyricsFound_savesTranslationWithAudit() {

        // given
        given(lrcLibClient.find(anyString(), anyString(), any(), any()))
            .willReturn(Optional.of(new LrcLibLyrics(42L, false, LYRICS)));
        given(translationService.translate(any(), any())).willReturn(new LyricsTranslation(
            "hiszpański", "Tłumaczenie testowe", "Interpretacja testowa.", 1200, 1500));

        // when
        enricher.enrich(track(), false);

        // then
        TrackLyrics saved = saved();
        assertThat(saved.getStatus()).isEqualTo(LyricsStatus.TRANSLATED);
        assertThat(saved.getLrclibId()).isEqualTo(42L);
        assertThat(saved.getOriginalLyrics()).isEqualTo(LYRICS);
        assertThat(saved.getTranslationPl()).isEqualTo("Tłumaczenie testowe");
        assertThat(saved.getInterpretationPl()).isEqualTo("Interpretacja testowa.");
        assertThat(saved.getSourceLanguage()).isEqualTo("hiszpański");
        assertThat(saved.getModelUsed()).isEqualTo("test-model");
        assertThat(saved.getPromptVersion()).isEqualTo(2);
        assertThat(saved.getFetchedAt()).isNotNull();
        assertThat(saved.getTranslatedAt()).isNotNull();
    }

    @Test
    void enrich_whenLrcLibKnowsNothing_savesConfirmedGapWithoutPayingForModel() {

        // given
        given(lrcLibClient.find(anyString(), anyString(), any(), any())).willReturn(Optional.empty());

        // when
        enricher.enrich(track(), false);

        // then
        assertThat(saved().getStatus()).isEqualTo(LyricsStatus.NOT_FOUND);
        verifyNoInteractions(translationService);
    }

    @Test
    void enrich_whenRecordingIsInstrumental_savesInstrumentalWithoutTranslation() {

        // given
        given(lrcLibClient.find(anyString(), anyString(), any(), any()))
            .willReturn(Optional.of(LrcLibLyrics.instrumental(7L)));

        // when
        enricher.enrich(track(), false);

        // then
        TrackLyrics saved = saved();
        assertThat(saved.getStatus()).isEqualTo(LyricsStatus.INSTRUMENTAL);
        assertThat(saved.getLrclibId()).isEqualTo(7L);
        verifyNoInteractions(translationService);
    }

    @Test
    void enrich_whenAlreadyResolvedAndNotForced_doesNothing() {

        // given — negatywny cache: pytanie LRCLIB drugi raz niczego nie zmieni
        given(repository.findById("sp-vivir"))
            .willReturn(Optional.of(new TrackLyrics("sp-vivir", LyricsStatus.NOT_FOUND)));

        // when
        enricher.enrich(track(), false);

        // then
        verifyNoInteractions(lrcLibClient, translationService);
        verify(repository, never()).save(any());
    }

    @Test
    void enrich_whenForced_refetchesEvenResolvedTrack() {

        // given — zlecenie na wskazany utwór to świadome polecenie DJ-a
        given(repository.findById("sp-vivir"))
            .willReturn(Optional.of(new TrackLyrics("sp-vivir", LyricsStatus.NOT_FOUND)));
        given(lrcLibClient.find(anyString(), anyString(), any(), any()))
            .willReturn(Optional.of(new LrcLibLyrics(42L, false, LYRICS)));
        given(translationService.translate(any(), any())).willReturn(new LyricsTranslation(
            "hiszpański", "Tłumaczenie testowe", "Interpretacja testowa.", 10, 10));

        // when
        enricher.enrich(track(), true);

        // then
        assertThat(saved().getStatus()).isEqualTo(LyricsStatus.TRANSLATED);
    }

    @Test
    void enrich_whenLyricsStoredButNotTranslated_translatesWithoutCallingLrcLib() {

        // given — poprzedni przebieg pobrał tekst i padł na tłumaczeniu
        TrackLyrics stored = new TrackLyrics("sp-vivir", LyricsStatus.FETCHED);
        stored.setOriginalLyrics(LYRICS);
        given(repository.findById("sp-vivir")).willReturn(Optional.of(stored));
        given(translationService.translate(any(), any())).willReturn(new LyricsTranslation(
            "hiszpański", "Tłumaczenie testowe", "Interpretacja testowa.", 10, 10));

        // when
        enricher.enrich(track(), false);

        // then
        verifyNoInteractions(lrcLibClient);
        assertThat(saved().getStatus()).isEqualTo(LyricsStatus.TRANSLATED);
    }

    @Test
    void enrich_whenModelReturnsNoTranslation_leavesTrackInQueue() {

        // given
        given(lrcLibClient.find(anyString(), anyString(), any(), any()))
            .willReturn(Optional.of(new LrcLibLyrics(42L, false, LYRICS)));
        given(translationService.translate(any(), any()))
            .willReturn(new LyricsTranslation("hiszpański", null, null, 10, 10));

        // when
        enricher.enrich(track(), false);

        // then — FETCHED nie jest rozstrzygnięty, więc utwór wróci przy kolejnym przebiegu
        assertThat(saved().getStatus()).isEqualTo(LyricsStatus.FETCHED);
    }

    @Test
    void enrich_whenModelRejectsThisRequest_keepsTextAndLetsJobGoOn() {

        // given — lokalny silnik bez wolnej pamięci karty odrzuca to jedno żądanie;
        // przebieg po całej bibliotece nie może się przez to wywrócić (D31/D32)
        given(lrcLibClient.find(anyString(), anyString(), any(), any()))
            .willReturn(Optional.of(new LrcLibLyrics(42L, false, LYRICS)));
        given(translationService.translate(any(), any())).willThrow(
            new LlmRequestRejectedException("LLM_REQUEST_REJECTED", "brak pamięci karty", true));

        // when
        enricher.enrich(track(), false);

        // then — tekst zostaje, tłumaczenie wraca do kolejki przy kolejnym przebiegu
        TrackLyrics saved = saved();
        assertThat(saved.getStatus()).isEqualTo(LyricsStatus.FETCHED);
        assertThat(saved.getOriginalLyrics()).isEqualTo(LYRICS);
        assertThat(saved.isResolved()).isFalse();
    }

    @Test
    void enrich_whenResponseIsUnusable_keepsTextAndLetsJobGoOn() {

        // given — model odpowiedział, ale nie da się z tego wyczytać tłumaczenia
        // (ucięcie na limicie tokenów albo własny format zamiast tego z promptu)
        given(lrcLibClient.find(anyString(), anyString(), any(), any()))
            .willReturn(Optional.of(new LrcLibLyrics(42L, false, LYRICS)));
        given(translationService.translate(any(), any())).willThrow(
            new LlmResponseInvalidException("model uciął odpowiedź", true));

        // when
        enricher.enrich(track(), false);

        // then
        TrackLyrics saved = saved();
        assertThat(saved.getStatus()).isEqualTo(LyricsStatus.FETCHED);
        assertThat(saved.getOriginalLyrics()).isEqualTo(LYRICS);
    }

    @Test
    void enrich_whenProviderConfigurationIsWrong_failsLoudly() {

        // given — zły klucz albo zły model to nie jest problem tego utworu; pomijanie
        // po cichu przerobiłoby bibliotekę na serię nieudanych wywołań
        given(lrcLibClient.find(anyString(), anyString(), any(), any()))
            .willReturn(Optional.of(new LrcLibLyrics(42L, false, LYRICS)));
        given(translationService.translate(any(), any())).willThrow(
            new LlmRequestRejectedException("LLM_NOT_AUTHORIZED", "zły klucz", false));
        TrackCatalog track = track();

        // when + then
        assertThatThrownBy(() -> enricher.enrich(track, false))
            .isInstanceOf(LlmRequestRejectedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void enrich_whenTrackHasNoArtist_skipsWithoutCallingSources() {

        // given — bez wykonawcy i tytułu nie ma o co zapytać LRCLIB
        TrackCatalog incomplete = new TrackCatalog("sp-pusty", null, null);

        // when
        enricher.enrich(incomplete, false);

        // then
        verifyNoInteractions(lrcLibClient, translationService);
        verify(repository, never()).save(any());
    }

    private TrackLyrics saved() {

        ArgumentCaptor<TrackLyrics> captor = ArgumentCaptor.forClass(TrackLyrics.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private TrackCatalog track() {
        return TrackCatalogFixtures.enrichedTrack("sp-vivir");
    }
}
