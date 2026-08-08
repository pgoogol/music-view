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

    private static final BigDecimal PER_MILLION = new BigDecimal("1000000");

    private final LlmProperties llmProperties;

    public EnrichmentCostEstimator(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    /** Pusto, gdy nie znamy cennika providera — wtedy UI mówi to wprost. */
    public Optional<BigDecimal> estimate(long aiTracks) {

        LlmProperties.Cost cost = llmProperties.cost();
        if (!cost.isKnown() || aiTracks <= 0) {
            return cost.isKnown() ? Optional.of(BigDecimal.ZERO.setScale(4)) : Optional.empty();
        }
        BigDecimal tracks = BigDecimal.valueOf(aiTracks);
        BigDecimal input = tracks
            .multiply(BigDecimal.valueOf(INPUT_TOKENS_PER_TRACK))
            .multiply(cost.inputPer1m())
            .divide(PER_MILLION, 6, RoundingMode.HALF_UP);
        BigDecimal output = tracks
            .multiply(BigDecimal.valueOf(OUTPUT_TOKENS_PER_TRACK))
            .multiply(cost.outputPer1m())
            .divide(PER_MILLION, 6, RoundingMode.HALF_UP);
        return Optional.of(input.add(output).setScale(4, RoundingMode.HALF_UP));
    }
}
