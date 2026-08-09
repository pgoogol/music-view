package com.pgoogol.enrichment;

import com.pgoogol.enrichment.llm.LlmProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Szacunek kosztu wzbogacania (M5.1/D28). Zużycie tokenów bierze się z próby
 * generalnej M1.9 (prompt v1, batch po 5): ~{@value #INPUT_TOKENS_PER_TRACK}
 * tokenów wejścia i ~{@value #OUTPUT_TOKENS_PER_TRACK} wyjścia na utwór.
 * Stawki providera siedzą w konfiguracji ({@code llm.cost.*}) — wcześniej były
 * wyłącznie zmiennymi środowiskowymi {@code LlmSmokeTest}, więc nie dawało się
 * ich pokazać w UI.
 *
 * <p>Płatna jest tylko grupa AI: metadane i cechy audio jadą z darmowych źródeł
 * (D6), więc liczba utworów w zleceniu nie musi równać się liczbie płatnych.</p>
 */
@Component
public class EnrichmentCostEstimator {

    static final int INPUT_TOKENS_PER_TRACK = 140;
    static final int OUTPUT_TOKENS_PER_TRACK = 120;

    /**
     * Tłumaczenie tekstu (D32) kosztuje o rząd wielkości więcej niż opis
     * z metadanych: wejściem jest cały tekst utworu (~3–4 tys. znaków), a wyjściem
     * tłumaczenie tej samej długości plus interpretacja. Wartości orientacyjne
     * do zmierzenia na realnym przebiegu — jak stawki AI po M1.9.
     */
    static final int LYRICS_INPUT_TOKENS_PER_TRACK = 1400;
    static final int LYRICS_OUTPUT_TOKENS_PER_TRACK = 1600;

    private static final BigDecimal PER_MILLION = new BigDecimal("1000000");

    private final LlmProperties llmProperties;

    public EnrichmentCostEstimator(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    /** Pusto, gdy nie znamy cennika providera — wtedy UI mówi to wprost. */
    public Optional<BigDecimal> estimate(long aiTracks, long lyricsTracks) {

        LlmProperties.Cost cost = llmProperties.cost();
        if (!cost.isKnown()) {
            return Optional.empty();
        }
        BigDecimal total = groupCost(aiTracks, INPUT_TOKENS_PER_TRACK, OUTPUT_TOKENS_PER_TRACK)
            .add(groupCost(lyricsTracks,
                LYRICS_INPUT_TOKENS_PER_TRACK, LYRICS_OUTPUT_TOKENS_PER_TRACK));
        return Optional.of(total.setScale(4, RoundingMode.HALF_UP));
    }

    private BigDecimal groupCost(long trackCount, int inputTokens, int outputTokens) {

        LlmProperties.Cost cost = llmProperties.cost();
        if (trackCount <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal tracks = BigDecimal.valueOf(trackCount);
        BigDecimal input = tracks
            .multiply(BigDecimal.valueOf(inputTokens))
            .multiply(cost.inputPer1m())
            .divide(PER_MILLION, 6, RoundingMode.HALF_UP);
        BigDecimal output = tracks
            .multiply(BigDecimal.valueOf(outputTokens))
            .multiply(cost.outputPer1m())
            .divide(PER_MILLION, 6, RoundingMode.HALF_UP);
        return input.add(output);
    }
}
