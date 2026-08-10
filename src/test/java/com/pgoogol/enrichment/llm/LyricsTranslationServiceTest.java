package com.pgoogol.enrichment.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tłumaczenie i interpretacja tekstu (M6.1/D32) — teksty w testach są wymyślone
 * na potrzeby testu, a odpowiedzi modelu podstawione.
 */
class LyricsTranslationServiceTest {

    private static final String LYRICS = "Pierwszy wers testowego tekstu\nDrugi wers testowego tekstu";

    private final LlmClient llmClient = mock(LlmClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void translate_whenModelReturnsObject_mapsAllFields() {

        // given
        given(llmClient.complete(any())).willReturn(new LlmCompletion("""
            JEZYK: hiszpański
            ### TLUMACZENIE ###
            Pierwszy wers po polsku
            Drugi wers po polsku
            ### INTERPRETACJA ###
            Utwór o świętowaniu mimo trudności.
            """, 1200, 1500));
        LyricsTranslationService service = service();

        // when
        LyricsTranslation translation = service.translate(track(), LYRICS);

        // then
        assertThat(translation.sourceLanguage()).isEqualTo("hiszpański");
        assertThat(translation.translationPl()).isEqualTo("Pierwszy wers po polsku\nDrugi wers po polsku");
        assertThat(translation.interpretationPl()).isEqualTo("Utwór o świętowaniu mimo trudności.");
        assertThat(translation.inputTokens()).isEqualTo(1200);
        assertThat(translation.outputTokens()).isEqualTo(1500);
    }

    @Test
    void translate_whenModelAnswersInJsonInstead_stillParses() {

        // given — starszy prompt (v1) i modele, które trzymają JSON bez problemu
        given(llmClient.complete(any())).willReturn(new LlmCompletion("""
            ```json
            {"source_language": "angielski", "translation_pl": "Tłumaczenie", "interpretation_pl": "Opis."}
            ```
            """, 10, 10));

        // when
        LyricsTranslation translation = service().translate(track(), LYRICS);

        // then
        assertThat(translation.translationPl()).isEqualTo("Tłumaczenie");
    }

    @Test
    void translate_whenPromptBuilt_carriesTrackContextAndLyrics() {

        // given — sam tytuł bywa u różnych wykonawców innym utworem
        given(llmClient.complete(any())).willReturn(new LlmCompletion(
            "### TLUMACZENIE ###\nTłumaczenie", 10, 10));
        ArgumentCaptor<LlmPrompt> prompt = ArgumentCaptor.forClass(LlmPrompt.class);

        // when
        service().translate(track(), LYRICS);

        // then
        verify(llmClient).complete(prompt.capture());
        assertThat(prompt.getValue().user())
            .contains("Marc Anthony")
            .contains("Vivir Mi Vida")
            .contains(LYRICS);
    }

    @Test
    void translate_whenLyricsExceedLimit_truncatesWithMarker() {

        // given — przycięcie jest tańsze niż wywołanie odrzucone przez limit modelu
        given(llmClient.complete(any())).willReturn(new LlmCompletion(
            "### TLUMACZENIE ###\nTłumaczenie", 10, 10));
        String tooLong = "wers testowego tekstu\n".repeat(500);
        ArgumentCaptor<LlmPrompt> prompt = ArgumentCaptor.forClass(LlmPrompt.class);

        // when
        service(50).translate(track(), tooLong);

        // then
        verify(llmClient).complete(prompt.capture());
        assertThat(prompt.getValue().user())
            .contains(LyricsTranslationService.TRUNCATION_MARKER)
            .doesNotContain(tooLong);
    }

    @Test
    void translate_whenCalled_sendsLyricsOwnTokenCeiling() {

        // given — sufit odpowiedzi dla tłumaczenia jest inny niż dla opisu utworu (D32)
        given(llmClient.complete(any())).willReturn(new LlmCompletion(
            "### TLUMACZENIE ###\nTłumaczenie", 10, 10));
        ArgumentCaptor<LlmPrompt> prompt = ArgumentCaptor.forClass(LlmPrompt.class);

        // when
        service().translate(track(), LYRICS);

        // then
        verify(llmClient).complete(prompt.capture());
        assertThat(prompt.getValue().maxTokens()).isEqualTo(1500);
    }

    @Test
    void translate_whenResponseHasNoTranslation_failsWithFormatHint() {

        // given — model odpowiedział po swojemu, zamiast trzymać się promptu
        given(llmClient.complete(any())).willReturn(
            new LlmCompletion("Nie mogę pomóc w tym zadaniu.", 10, 10));
        LyricsTranslationService service = service();
        TrackCatalog track = track();

        // when + then
        assertThatThrownBy(() -> service.translate(track, LYRICS))
            .isInstanceOf(LlmResponseInvalidException.class)
            .hasMessageContaining("prompt-version");
    }

    @Test
    void translate_whenProviderCutResponseOnTokenLimit_refusesHalfTranslation() {

        // given — sufit tokenów przerwał generowanie; połowa tłumaczenia zapisana
        // jako gotowa byłaby gorsza niż jego brak (utwór ma wrócić do kolejki)
        given(llmClient.complete(any())).willReturn(new LlmCompletion(
            "### TLUMACZENIE ###\nPierwszy wers po pol", 10, 1500, true));
        LyricsTranslationService service = service();
        TrackCatalog track = track();

        // when + then
        assertThatThrownBy(() -> service.translate(track, LYRICS))
            .isInstanceOf(LlmResponseInvalidException.class)
            .hasMessageContaining("llm.lyrics.max-tokens");
    }

    private LyricsTranslationService service() {
        return service(6000);
    }

    private LyricsTranslationService service(int maxChars) {

        LlmProperties properties = new LlmProperties("openai", null, "klucz", "test-model",
            "v1", 5, 2, 2048, 0.2, 500, null, new LlmProperties.Lyrics("v1", maxChars, 1500));
        return new LyricsTranslationService(
            llmClient, new LyricsTranslationPrompt(properties),
            new LyricsResponseParser(objectMapper), properties);
    }

    private TrackCatalog track() {
        return TrackCatalogFixtures.enrichedTrack("sp-vivir");
    }
}
