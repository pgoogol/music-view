package com.pgoogol.enrichment;

import org.springframework.lang.Nullable;

import java.math.BigDecimal;

/**
 * Ile utworów obejmie zlecenie i ile to będzie kosztowało (M5.1/D28) — koszt
 * pokazujemy <b>przed</b> startem joba, nie po.
 *
 * @param aiTracks       utwory z opisem AI (D11) — grupy METADATA i AUDIO jadą
 *                       z darmowych źródeł (D6), więc nie wchodzą do kosztu
 * @param lyricsTracks   utwory z tłumaczeniem tekstu (D32); też płatne, ale
 *                       liczone osobno — wejściem jest cały tekst, nie metadane,
 *                       więc utwór kosztuje tam wielokrotnie więcej niż w AI
 * @param estimatedCost  {@code null}, gdy stawki providera nie są skonfigurowane —
 *                       lepiej powiedzieć „nie wiem" niż zgadywać cennik
 * @param limit          twardy sufit {@code llm.max-tracks-per-job}
 * @param withinLimit    czy zlecenie w ogóle wystartuje
 */
public record EnrichmentEstimate(
    long trackCount,
    long aiTracks,
    long lyricsTracks,
    @Nullable BigDecimal estimatedCost,
    int limit,
    boolean withinLimit) {

    /** Utwory, za które realnie zapłacimy providerowi. */
    public long paidTracks() {
        return aiTracks + lyricsTracks;
    }
}
