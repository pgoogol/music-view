package com.pgoogol.enrichment.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.common.ExternalServiceException;
import com.pgoogol.enrichment.bpm.HalfTimeCorrector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Analiza AI utworów (M1.5): batchowanie po {@code llm.batch-size} (domyślnie 5),
 * budowa promptu z metadanych (+bpm jeśli znany), parsowanie i walidacja
 * strukturalnego JSON-a. {@code bpm_estimate} żądany wyłącznie dla utworów
 * bez BPM, gdy wywołujący o to poprosi (kaskada M1.4 pusta); estymaty
 * przechodzą korektę half-time. Zapis do katalogu należy do joba (M1.6).
 */
@Service
public class TrackAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(TrackAnalysisService.class);

    private final LlmClient llmClient;
    private final TrackAnalysisPrompt prompt;
    private final ObjectMapper objectMapper;
    private final HalfTimeCorrector halfTimeCorrector;
    private final LlmProperties properties;

    public TrackAnalysisService(LlmClient llmClient, TrackAnalysisPrompt prompt,
                                ObjectMapper objectMapper, HalfTimeCorrector halfTimeCorrector,
                                LlmProperties properties) {

        this.llmClient = llmClient;
        this.prompt = prompt;
        this.objectMapper = objectMapper;
        this.halfTimeCorrector = halfTimeCorrector;
        this.properties = properties;
    }

    public TrackAnalysisResult analyze(List<TrackCatalog> tracks, boolean estimateMissingBpm) {

        Objects.requireNonNull(tracks, "tracks");
        List<TrackAnalysis> analyses = new ArrayList<>();
        long inputTokens = 0;
        long outputTokens = 0;
        for (List<TrackCatalog> batch : partition(tracks, properties.batchSize())) {
            LlmCompletion completion = llmClient.complete(
                new LlmPrompt(prompt.system(), prompt.user(toInputJson(batch, estimateMissingBpm))));
            analyses.addAll(parseBatch(completion.content(), batch));
            inputTokens += completion.inputTokens();
            outputTokens += completion.outputTokens();
        }
        log.info("Analiza AI zakończona: utwory={}, analizy={}, tokeny={}wej/{}wyj (prompt {})",
            tracks.size(), analyses.size(), inputTokens, outputTokens, prompt.version());
        return new TrackAnalysisResult(List.copyOf(analyses), inputTokens, outputTokens);
    }

    // pętla po batchach powyżej i klasyczna budowa partycji: sekwencyjne wywołania
    // zdalne z akumulacją tokenów są tu czytelniejsze niż wariant strumieniowy
    private List<List<TrackCatalog>> partition(List<TrackCatalog> tracks, int batchSize) {

        return IntStream.range(0, (tracks.size() + batchSize - 1) / batchSize)
            .mapToObj(batch -> tracks.subList(
                batch * batchSize, Math.min((batch + 1) * batchSize, tracks.size())))
            .toList();
    }

    private String toInputJson(List<TrackCatalog> batch, boolean estimateMissingBpm) {

        List<InputTrack> input = batch.stream()
            .map(track -> new InputTrack(
                track.getSpotifyId(), track.getTitle(), track.getArtist(), track.getAlbum(),
                track.getYear(), track.getBpm(),
                estimateMissingBpm && Objects.isNull(track.getBpm())))
            .toList();
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Nie udało się zserializować wejścia dla LLM", ex);
        }
    }

    private List<TrackAnalysis> parseBatch(String content, List<TrackCatalog> batch) {

        Map<String, TrackCatalog> byId = batch.stream()
            .collect(Collectors.toMap(TrackCatalog::getSpotifyId, Function.identity()));
        JsonNode root = readJson(content);
        if (!root.isArray()) {
            throw new ExternalServiceException("LLM_RESPONSE_INVALID",
                "Odpowiedź LLM nie jest tablicą JSON");
        }
        List<TrackAnalysis> analyses = new ArrayList<>();
        root.forEach(node -> toAnalysis(node, byId).ifPresent(analyses::add));
        if (analyses.size() < batch.size()) {
            log.warn("LLM zwrócił {} z {} analiz w batchu — brakujące utwory zostaną ponowione "
                + "przy kolejnym wzbogacaniu", analyses.size(), batch.size());
        }
        return analyses;
    }

    private JsonNode readJson(String content) {

        try {
            return objectMapper.readTree(stripMarkdownFences(content));
        } catch (JsonProcessingException ex) {
            throw new ExternalServiceException("LLM_RESPONSE_INVALID",
                "Odpowiedź LLM nie jest poprawnym JSON-em", ex);
        }
    }

    private String stripMarkdownFences(String content) {

        String trimmed = content.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int closingFence = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || closingFence <= firstLineEnd) {
            return trimmed;
        }
        return trimmed.substring(firstLineEnd + 1, closingFence).strip();
    }

    private Optional<TrackAnalysis> toAnalysis(JsonNode node, Map<String, TrackCatalog> byId) {

        String spotifyId = node.path("spotify_id").asText(null);
        if (Objects.isNull(spotifyId) || !byId.containsKey(spotifyId)) {
            log.warn("LLM zwrócił analizę dla utworu spoza batcha: {}", spotifyId);
            return Optional.empty();
        }
        GenreFamily genreFamily = parseGenreFamily(node.path("genre_family").asText(null), spotifyId);
        return Optional.of(new TrackAnalysis(
            spotifyId,
            textOrNull(node, "style"),
            genreFamily,
            textOrNull(node, "lyrics_theme"),
            textOrNull(node, "description_pl"),
            textOrNull(node, "energy"),
            textOrNull(node, "confidence"),
            bpmEstimate(node, genreFamily)));
    }

    private Integer bpmEstimate(JsonNode node, GenreFamily genreFamily) {

        JsonNode estimate = node.path("bpm_estimate");
        if (!estimate.isNumber()) {
            return null;
        }
        return halfTimeCorrector.correct(genreFamily, estimate.asInt());
    }

    private GenreFamily parseGenreFamily(String raw, String spotifyId) {

        if (Objects.isNull(raw) || raw.isBlank()) {
            log.warn("LLM nie zwrócił genre_family dla {} — przyjmuję OTHER", spotifyId);
            return GenreFamily.OTHER;
        }
        try {
            return GenreFamily.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("LLM zwrócił nieznane genre_family '{}' dla {} — przyjmuję OTHER", raw, spotifyId);
            return GenreFamily.OTHER;
        }
    }

    private String textOrNull(JsonNode node, String field) {

        String value = node.path(field).asText(null);
        return Objects.isNull(value) || value.isBlank() ? null : value.strip();
    }

    private record InputTrack(@JsonProperty("spotify_id") String spotifyId,
                              String title,
                              String artist,
                              String album,
                              Integer year,
                              Integer bpm,
                              @JsonProperty("oszacuj_bpm") boolean estimateBpm) {

    }
}
