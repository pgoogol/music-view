package com.pgoogol.enrichment;

import org.springframework.lang.Nullable;

import java.math.BigDecimal;

/**
 * Ile utworów obejmie zlecenie i ile to będzie kosztowało (M5.1/D28) — koszt
 * pokazujemy <b>przed</b> startem joba, nie po.
 *
 * @param aiTracks       utwory, za które realnie zapłacimy; grupy METADATA i AUDIO
 *                       jadą z darmowych źródeł (D6), więc nie wchodzą do kosztu
 * @param estimatedCost  {@code null}, gdy stawki providera nie są skonfigurowane —
 *                       lepiej powiedzieć „nie wiem" niż zgadywać cennik
 * @param limit          twardy sufit {@code llm.max-tracks-per-job}
 * @param withinLimit    czy zlecenie w ogóle wystartuje
 */
public record EnrichmentEstimate(
    long trackCount,
    long aiTracks,
    @Nullable BigDecimal estimatedCost,
    int limit,
    boolean withinLimit) {

}
