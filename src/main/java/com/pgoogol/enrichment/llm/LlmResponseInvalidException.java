package com.pgoogol.enrichment.llm;

import com.pgoogol.common.ExternalServiceException;

/**
 * Model odpowiedział, ale odpowiedzi nie da się użyć — nie trzyma się formatu
 * z promptu albo urwała się na limicie tokenów.
 *
 * <p>Rozszerza {@link ExternalServiceException} (kod {@code LLM_RESPONSE_INVALID}
 * zostaje bez zmian), więc mapowanie na 502 i dotychczasowa obsługa działają jak
 * dotąd; osobny typ jest po to, żeby wzbogacanie tekstów mogło pominąć jeden
 * utwór zamiast wywracać przebieg po całej bibliotece (D32).</p>
 */
public class LlmResponseInvalidException extends ExternalServiceException {

    private final boolean truncated;

    public LlmResponseInvalidException(String message, boolean truncated) {

        super("LLM_RESPONSE_INVALID", message);
        this.truncated = truncated;
    }

    /** Odpowiedź urwała się na limicie tokenów — regulacja jest w konfiguracji, nie w modelu. */
    public boolean isTruncated() {
        return truncated;
    }
}
