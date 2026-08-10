package com.pgoogol.enrichment.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Czyta odpowiedź modelu na prompt tłumaczenia (D32) — <b>tolerancyjnie</b>,
 * bo po drugiej stronie bywa mały model lokalny.
 *
 * <p>Podstawowy format (prompt v2) to sekcje z nagłówkami, nie JSON: kilkanaście
 * zwrotek z podziałem na wersy wewnątrz łańcucha JSON to dokładnie to zadanie,
 * na którym modele 7–13B się wykładają (nieucieknięte cudzysłowy, gubione
 * {@code \n}, ucięty nawias na końcu). Sekcje przeżywają wszystkie te potknięcia.</p>
 *
 * <p>Format JSON (prompt v1) zostaje obsłużony jako alternatywa — dla modeli,
 * które trzymają go bez problemu, i dla utworów opisanych wcześniejszą wersją
 * promptu.</p>
 */
@Component
public class LyricsResponseParser {

    private static final Pattern LANGUAGE =
        Pattern.compile("^\\s*(?:JEZYK|JĘZYK)\\s*:\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern TRANSLATION_HEAD =
        Pattern.compile("^\\s*#*\\s*(?:TLUMACZENIE|TŁUMACZENIE)\\s*#*\\s*$", Pattern.MULTILINE);
    private static final Pattern INTERPRETATION_HEAD =
        Pattern.compile("^\\s*#*\\s*INTERPRETACJA\\s*#*\\s*$", Pattern.MULTILINE);

    private final ObjectMapper objectMapper;

    public LyricsResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @throws LlmResponseInvalidException gdy w odpowiedzi nie da się znaleźć
     *                                     tłumaczenia w żadnym z formatów
     */
    public ParsedTranslation parse(String content, boolean truncated) {

        String text = Objects.requireNonNullElse(content, "").strip();
        ParsedTranslation sections = fromSections(text);
        if (Objects.nonNull(sections)) {
            return sections;
        }
        ParsedTranslation json = fromJson(text);
        if (Objects.nonNull(json)) {
            return json;
        }
        throw new LlmResponseInvalidException(truncated
            ? "Model uciął odpowiedź na limicie tokenów — podnieś llm.lyrics.max-tokens "
                + "albo zmniejsz llm.lyrics.max-chars"
            : "Odpowiedź modelu nie zawiera sekcji tłumaczenia ani poprawnego JSON-a — "
                + "sprawdź, czy model trzyma się formatu z promptu (llm.lyrics.prompt-version)",
            truncated);
    }

    /** {@code null} = odpowiedź nie jest w formacie sekcji (prompt v2). */
    private ParsedTranslation fromSections(String text) {

        Matcher translationHead = TRANSLATION_HEAD.matcher(text);
        if (!translationHead.find()) {
            return null;
        }
        Matcher interpretationHead = INTERPRETATION_HEAD.matcher(text);
        boolean hasInterpretation = interpretationHead.find(translationHead.end());
        String translation = text.substring(translationHead.end(),
            hasInterpretation ? interpretationHead.start() : text.length());
        String interpretation = hasInterpretation ? text.substring(interpretationHead.end()) : null;
        return new ParsedTranslation(
            language(text.substring(0, translationHead.start())),
            blankToNull(translation),
            blankToNull(interpretation));
    }

    /** {@code null} = odpowiedź nie jest obiektem JSON (prompt v1). */
    private ParsedTranslation fromJson(String text) {

        return LlmResponses.findJsonObject(objectMapper, text)
            .map(node -> new ParsedTranslation(
                LlmResponses.textOrNull(node, "source_language"),
                LlmResponses.textOrNull(node, "translation_pl"),
                LlmResponses.textOrNull(node, "interpretation_pl")))
            .filter(parsed -> Objects.nonNull(parsed.translationPl()))
            .orElse(null);
    }

    private String language(String head) {

        Matcher matcher = LANGUAGE.matcher(head);
        return matcher.find() ? blankToNull(matcher.group(1)) : null;
    }

    private String blankToNull(String value) {

        return Optional.ofNullable(value)
            .map(String::strip)
            .filter(stripped -> !stripped.isEmpty())
            .orElse(null);
    }

    /** Trzy pola, które niesie odpowiedź — niezależnie od formatu, w jakim przyszły. */
    public record ParsedTranslation(String sourceLanguage, String translationPl,
                                    String interpretationPl) {

    }
}
