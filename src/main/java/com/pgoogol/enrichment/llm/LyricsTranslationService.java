package com.pgoogol.enrichment.llm;

import com.pgoogol.catalog.TrackCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Tłumaczenie i interpretacja tekstu utworu (M6.1/D32) — nowy cel warstwy AI
 * obok dotychczasowej analizy DJ-skiej (M1.5), która zostaje bez zmian.
 *
 * <p><b>Jeden utwór na wywołanie</b>, w odróżnieniu od analizy batchowanej po
 * pięć: tekst to kilka tysięcy znaków, więc partia rozsadziłaby okno kontekstu
 * i pierwszy błąd parsowania kosztowałby całą piątkę zamiast jednego utworu.</p>
 *
 * <p>Odpowiedź czyta {@link LyricsResponseParser} — tolerancyjnie, bo prompt
 * tłumaczenia bywa obsługiwany przez mały model lokalny (D32).</p>
 *
 * <p>Tekst dłuższy niż {@code llm.lyrics.max-chars} jest przycinany z jawnym
 * znacznikiem — lepiej przetłumaczyć zwrotki, które się zmieściły, i powiedzieć
 * to wprost, niż zapłacić za wywołanie odrzucone przez limit modelu.</p>
 */
@Service
public class LyricsTranslationService {

    static final String TRUNCATION_MARKER = "\n[…tekst przycięty przed wysłaniem do modelu]";

    private static final Logger log = LoggerFactory.getLogger(LyricsTranslationService.class);

    private final LlmClient llmClient;
    private final LyricsTranslationPrompt prompt;
    private final LyricsResponseParser parser;
    private final LlmProperties properties;

    public LyricsTranslationService(LlmClient llmClient, LyricsTranslationPrompt prompt,
                                    LyricsResponseParser parser, LlmProperties properties) {

        this.llmClient = llmClient;
        this.prompt = prompt;
        this.parser = parser;
        this.properties = properties;
    }

    public LyricsTranslation translate(TrackCatalog track, String lyrics) {

        Objects.requireNonNull(track, "track");
        Objects.requireNonNull(lyrics, "lyrics");
        LlmCompletion completion = llmClient.complete(new LlmPrompt(
            prompt.system(),
            prompt.user(describe(track), truncate(lyrics)),
            properties.lyrics().maxTokens()));
        LyricsResponseParser.ParsedTranslation parsed =
            parser.parse(completion.content(), completion.truncated());
        if (completion.truncated()) {
            // tłumaczenie urwane w połowie utworu jest gorsze niż jego brak:
            // utwór ma wrócić do kolejki po podniesieniu limitu, a nie udawać gotowy
            throw new LlmResponseInvalidException(
                "Model uciął tłumaczenie na limicie tokenów — podnieś llm.lyrics.max-tokens "
                    + "albo zmniejsz llm.lyrics.max-chars", true);
        }
        log.debug("Przetłumaczono tekst utworu {}: tokeny={}wej/{}wyj (prompt {})",
            track.getSpotifyId(), completion.inputTokens(), completion.outputTokens(),
            prompt.version());
        return new LyricsTranslation(
            parsed.sourceLanguage(),
            parsed.translationPl(),
            parsed.interpretationPl(),
            completion.inputTokens(),
            completion.outputTokens());
    }

    public String promptVersion() {
        return prompt.version();
    }

    private String truncate(String lyrics) {

        int maxChars = properties.lyrics().maxChars();
        if (lyrics.length() <= maxChars) {
            return lyrics;
        }
        log.info("Tekst utworu ma {} znaków przy limicie {} — przycinam przed wysłaniem",
            lyrics.length(), maxChars);
        return lyrics.substring(0, maxChars) + TRUNCATION_MARKER;
    }

    /** Kontekst dla modelu: ten sam tytuł bywa u różnych wykonawców innym utworem. */
    private String describe(TrackCatalog track) {

        return Stream.of(track.getArtist(), track.getTitle(), track.getAlbum(),
                Objects.toString(track.getYear(), null))
            .filter(Objects::nonNull)
            .filter(value -> !value.isBlank())
            .reduce((first, second) -> first + " — " + second)
            .orElse(track.getSpotifyId());
    }
}
