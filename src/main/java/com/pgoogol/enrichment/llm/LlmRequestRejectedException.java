package com.pgoogol.enrichment.llm;

import com.pgoogol.common.AppException;

/**
 * Provider LLM odrzucił żądanie odpowiedzią 4xx. To <b>nie</b> jest
 * {@link com.pgoogol.common.ExternalServiceException}: retry z
 * {@code common/ratelimit} ponawia wyłącznie awarie przejściowe (5xx, timeout,
 * 429), a odrzucone żądanie po ponowieniu zostanie odrzucone tak samo
 * (docs/rules/errorhandling.md).
 *
 * <p>{@code requestSpecific} rozdziela dwie sytuacje, które wymagają różnej
 * reakcji:</p>
 * <ul>
 *   <li><b>true</b> (400/413/422) — to konkretne żądanie jest nie do przyjęcia:
 *       tekst za długi na okno kontekstu albo lokalny silnik bez wolnej pamięci
 *       karty. Inny utwór ma szansę przejść, więc job idzie dalej (D32).</li>
 *   <li><b>false</b> (401/403/404) — zła konfiguracja providera. Pomijanie
 *       utworów po cichu przerobiłoby całą bibliotekę na serię nieudanych
 *       wywołań, więc job ma paść głośno.</li>
 * </ul>
 */
public class LlmRequestRejectedException extends AppException {

    private final boolean requestSpecific;

    public LlmRequestRejectedException(String errorCode, String message, boolean requestSpecific) {

        super(errorCode, message);
        this.requestSpecific = requestSpecific;
    }

    public boolean isRequestSpecific() {
        return requestSpecific;
    }
}
