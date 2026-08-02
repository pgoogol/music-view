package com.pgoogol.api;

import java.math.BigDecimal;

/**
 * Szacunek zlecenia wzbogacania (M5.1/D28) — kontrakt
 * {@code POST /api/enrich/estimate}. Nic nie uruchamia.
 *
 * @param aiTracks      utwory, za które realnie zapłacimy (tylko grupa AI)
 * @param estimatedCost {@code null} = brak stawek w konfiguracji, nie zero
 * @param withinLimit   {@code false} oznacza, że {@code POST /api/enrich}
 *                      odrzuci to zlecenie
 */
public record EnrichmentEstimateResponse(
    long trackCount,
    long aiTracks,
    BigDecimal estimatedCost,
    int limit,
    boolean withinLimit) {

}
