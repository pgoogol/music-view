package com.pgoogol.enrichment.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgoogol.common.ExternalServiceException;

import java.util.Objects;

/**
 * Wspólne czytanie strukturalnych odpowiedzi LLM-a. Modele lubią opakować JSON
 * w blok markdown mimo jasnej instrukcji w prompcie — zdejmujemy ogrodzenie
 * zamiast wywracać całą partię na parsowaniu.
 */
final class LlmResponses {

    private LlmResponses() {

    }

    static JsonNode readJson(ObjectMapper objectMapper, String content) {

        try {
            return objectMapper.readTree(stripMarkdownFences(content));
        } catch (JsonProcessingException ex) {
            throw new ExternalServiceException("LLM_RESPONSE_INVALID",
                "Odpowiedź LLM nie jest poprawnym JSON-em", ex);
        }
    }

    /** Pusty tekst i pusty łańcuch traktujemy jak brak wartości, nie jak wartość. */
    static String textOrNull(JsonNode node, String field) {

        String value = node.path(field).asText(null);
        return Objects.isNull(value) || value.isBlank() ? null : value.strip();
    }

    private static String stripMarkdownFences(String content) {

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
}
