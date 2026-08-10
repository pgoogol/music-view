package com.pgoogol.enrichment.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.common.ExternalServiceException;
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
    private final ObjectMapper objectMapper;
    private final LlmProperties properties;

    public LyricsTranslationService(LlmClient llmClient, LyricsTranslationPrompt prompt,
                                    ObjectMapper objectMapper, LlmProperties properties) {

        this.llmClient = llmClient;
        this.prompt = prompt;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public LyricsTranslation translate(TrackCatalog track, String lyrics) {

        Objects.requireNonNull(track, "track");
        Objects.requireNonNull(lyrics, "lyrics");
        LlmCompletion completion = llmClient.complete(new LlmPrompt(
            prompt.system(),
            prompt.user(describe(track), truncate(lyrics)),
            properties.lyrics().maxTokens()));
        JsonNode root = LlmResponses.readJson(objectMapper, completion.content());
        if (!root.isObject()) {
            throw new ExternalServiceException("LLM_RESPONSE_INVALID",
                "Odpowiedź LLM dla tekstu utworu nie jest obiektem JSON");
        }
        log.debug("Przetłumaczono tekst utworu {}: tokeny={}wej/{}wyj (prompt {})",
            track.getSpotifyId(), completion.inputTokens(), completion.outputTokens(),
            prompt.version());
        return new LyricsTranslation(
            LlmResponses.textOrNull(root, "source_language"),
            LlmResponses.textOrNull(root, "translation_pl"),
            LlmResponses.textOrNull(root, "interpretation_pl"),
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
