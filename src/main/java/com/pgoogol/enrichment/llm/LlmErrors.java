package com.pgoogol.enrichment.llm;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Set;

/**
 * Tłumaczenie odpowiedzi 4xx providera LLM na typowany wyjątek
 * (docs/rules/errorhandling.md). Treść odpowiedzi wchodzi do komunikatu, bo
 * to w niej siedzi realna przyczyna — lokalne silniki (llama.cpp, LM Studio,
 * Ollama) zwracają pod statusem 400 komunikaty w rodzaju „context length
 * exceeded" albo „ErrorOutOfDeviceMemory", a bez nich w logu zostaje samo
 * „400 Bad Request".
 */
final class LlmErrors {

    /** Ile znaków odpowiedzi providera trafia do komunikatu — reszta to szum. */
    private static final int MAX_BODY_CHARS = 500;

    /** Statusy znaczące „to konkretne żądanie", a nie „zła konfiguracja". */
    private static final Set<HttpStatus> REQUEST_SPECIFIC = Set.of(
        HttpStatus.BAD_REQUEST,
        HttpStatus.PAYLOAD_TOO_LARGE,
        HttpStatus.UNPROCESSABLE_ENTITY);

    private LlmErrors() {

    }

    static LlmRequestRejectedException rejected(HttpClientErrorException ex) {

        boolean requestSpecific = REQUEST_SPECIFIC.contains(HttpStatus.valueOf(ex.getStatusCode().value()));
        String errorCode = requestSpecific ? "LLM_REQUEST_REJECTED" : "LLM_NOT_AUTHORIZED";
        String hint = requestSpecific
            ? "sprawdź llm.lyrics.max-chars, llm.lyrics.max-tokens i okno kontekstu modelu"
            : "sprawdź LLM_API_KEY, LLM_BASE_URL i LLM_MODEL";
        return new LlmRequestRejectedException(errorCode,
            "Provider LLM odrzucił żądanie (%s) — %s. Odpowiedź: %s"
                .formatted(ex.getStatusCode(), hint, body(ex)),
            requestSpecific);
    }

    private static String body(HttpClientErrorException ex) {

        String body = ex.getResponseBodyAsString().strip();
        if (body.isEmpty()) {
            return "(pusta)";
        }
        return body.length() <= MAX_BODY_CHARS ? body : body.substring(0, MAX_BODY_CHARS) + "…";
    }
}
