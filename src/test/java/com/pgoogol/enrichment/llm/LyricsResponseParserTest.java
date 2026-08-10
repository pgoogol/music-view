package com.pgoogol.enrichment.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Czytanie odpowiedzi modelu na prompt tłumaczenia (D32). Po drugiej stronie
 * bywa mały model lokalny, więc parser ma być tolerancyjny na formę i twardy
 * dopiero wtedy, gdy tłumaczenia naprawdę nie ma. Teksty wymyślone na potrzeby testu.
 */
class LyricsResponseParserTest {

    private final LyricsResponseParser parser = new LyricsResponseParser(new ObjectMapper());

    @Test
    void parse_whenSectionsFormat_readsAllThreeFields() {

        // given — format promptu v2
        String content = """
            JEZYK: hiszpański
            ### TLUMACZENIE ###
            Pierwszy wers po polsku
            Drugi wers po polsku
            ### INTERPRETACJA ###
            Utwór o świętowaniu mimo trudności.
            """;

        // when
        LyricsResponseParser.ParsedTranslation parsed = parser.parse(content, false);

        // then
        assertThat(parsed.sourceLanguage()).isEqualTo("hiszpański");
        assertThat(parsed.translationPl()).isEqualTo("Pierwszy wers po polsku\nDrugi wers po polsku");
        assertThat(parsed.interpretationPl()).isEqualTo("Utwór o świętowaniu mimo trudności.");
    }

    @Test
    void parse_whenSectionsWithoutInterpretation_keepsTranslation() {

        // given — model urwał się przed ostatnią sekcją, ale tłumaczenie jest całe
        String content = """
            JEZYK: angielski
            ### TLUMACZENIE ###
            Pierwszy wers po polsku
            """;

        // when
        LyricsResponseParser.ParsedTranslation parsed = parser.parse(content, false);

        // then
        assertThat(parsed.translationPl()).isEqualTo("Pierwszy wers po polsku");
        assertThat(parsed.interpretationPl()).isNull();
    }

    @Test
    void parse_whenJsonFormat_stillWorks() {

        // given — prompt v1 i modele, które JSON-a trzymają bez problemu
        String content = """
            {"source_language": "hiszpański", "translation_pl": "Pierwszy wers po polsku",
             "interpretation_pl": "Opis testowy."}
            """;

        // when
        LyricsResponseParser.ParsedTranslation parsed = parser.parse(content, false);

        // then
        assertThat(parsed.sourceLanguage()).isEqualTo("hiszpański");
        assertThat(parsed.translationPl()).isEqualTo("Pierwszy wers po polsku");
    }

    @Test
    void parse_whenJsonWrappedInProse_extractsObject() {

        // given — mały model lubi poprzedzić odpowiedź zdaniem od siebie
        String content = """
            Jasne, oto tłumaczenie tego utworu:
            {"translation_pl": "Pierwszy wers po polsku", "interpretation_pl": "Opis testowy."}
            Mam nadzieję, że pomogłem!
            """;

        // when
        LyricsResponseParser.ParsedTranslation parsed = parser.parse(content, false);

        // then
        assertThat(parsed.translationPl()).isEqualTo("Pierwszy wers po polsku");
    }

    @Test
    void parse_whenNothingUsable_failsWithFormatHint() {

        // when + then
        assertThatThrownBy(() -> parser.parse("Nie mogę pomóc w tym zadaniu.", false))
            .isInstanceOf(LlmResponseInvalidException.class)
            .hasMessageContaining("prompt-version");
    }

    @Test
    void parse_whenTruncatedAndUnusable_pointsAtTokenLimit() {

        // given — odpowiedź urwana na limicie: JSON bez zamknięcia, bez sekcji
        String content = "{\"translation_pl\": \"Pierwszy wers po pol";

        // when + then
        assertThatThrownBy(() -> parser.parse(content, true))
            .isInstanceOf(LlmResponseInvalidException.class)
            .hasMessageContaining("llm.lyrics.max-tokens")
            .matches(thrown -> ((LlmResponseInvalidException) thrown).isTruncated());
    }
}
