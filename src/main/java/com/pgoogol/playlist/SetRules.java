package com.pgoogol.playlist;

import com.pgoogol.catalog.CamelotKey;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reguły układania setu (D26) wspólne dla generatora od zera
 * ({@link SetGenerator}) i dobierania do gotowego setu ({@link SetSuggester},
 * M4.4/D32). Obie ścieżki muszą oceniać kandydata tak samo — inaczej „dobierz
 * następny" podpowiadałoby utwór, którego generator by nie wybrał, i DJ
 * dostawałby dwie różne opinie o tej samej bibliotece.
 *
 * <p><b>Ograniczenia twarde</b> (zawężają pulę): utwór wchodzi do setu raz,
 * a ten sam wykonawca nie częściej niż raz na {@value #ARTIST_GAP_MINUTES} minut.
 * <b>Miękkie</b> to kary w ocenie: skok BPM ponad próg, brak zgodności
 * harmonicznej, niska ocena, brak BPM. Gdyby miękkie zrobić twardymi, przy
 * wąskiej bibliotece wynikiem byłaby pustka zamiast setu z ostrzeżeniami.</p>
 */
@Component
public class SetRules {

    static final int ARTIST_GAP_MINUTES = 30;
    static final int BPM_JUMP_TOLERANCE = 15;

    /** Utwór bez znanego czasu liczymy jako typowy singiel — inaczej zawiesiłby pętlę. */
    static final long DEFAULT_TRACK_MS = Duration.ofMinutes(3).plusSeconds(30).toMillis();

    static final long ARTIST_GAP_MS = Duration.ofMinutes(ARTIST_GAP_MINUTES).toMillis();

    /** Kolejność faz wieczoru (D9) — po niej liczymy odległość slotu od fazy. */
    static final List<DjSlot> PHASE_ORDER =
        List.of(DjSlot.WARMUP, DjSlot.MIDDLE, DjSlot.PEAK, DjSlot.CLOSING);

    /**
     * Ocena kandydata na daną fazę — im wyżej, tym lepiej pasuje. Wartości są
     * względne i mają znaczenie tylko wobec siebie nawzajem; ich zadaniem jest
     * ustawić kolejność, a nie zmierzyć „jakość" utworu. Dlatego nie wychodzą
     * na zewnątrz w API — DJ dostaje powody (skok BPM, tonacja), nie punkty.
     *
     * @param phase faza, którą właśnie wypełniamy; {@code null} znaczy „bez
     *              preferencji" i zdarza się przy dobieraniu obok utworów,
     *              które nie mają jeszcze slotu
     */
    public int score(SetCandidate candidate, @Nullable SetCandidate previous,
                     @Nullable DjSlot phase) {

        Objects.requireNonNull(candidate, "candidate");
        int score = Objects.isNull(phase) ? 100 : 100 - 40 * phaseDistance(candidate.slot(), phase);
        score += Optional.ofNullable(candidate.rating()).orElse(0) * 6;
        score += Optional.ofNullable(candidate.track().getPopularity()).orElse(0) / 20;
        if (candidate.bpm().isEmpty()) {
            score -= 30;
        }
        return Objects.isNull(previous) ? score : score - transitionPenalty(previous, candidate);
    }

    /**
     * Kara za samo przejście między dwoma utworami — liczona w obie strony,
     * bo utwór dokładany w środek setu ma sąsiada z obu stron (M4.4).
     */
    public int transitionPenalty(SetCandidate from, SetCandidate to) {

        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        int penalty = isHarmonic(from, to) ? 0 : 25;
        Optional<Integer> before = from.bpm();
        Optional<Integer> after = to.bpm();
        if (before.isEmpty() || after.isEmpty()) {
            return penalty;
        }
        int jump = Math.abs(after.orElseThrow() - before.orElseThrow());
        return penalty + 2 * Math.max(0, jump - BPM_JUMP_TOLERANCE);
    }

    /** Nieznana tonacja po którejkolwiek stronie to brak danych, nie zderzenie (D25). */
    public boolean isHarmonic(SetCandidate previous, SetCandidate candidate) {

        Optional<CamelotKey> before = previous.key();
        Optional<CamelotKey> next = candidate.key();
        return before.isEmpty() || next.isEmpty()
            || before.orElseThrow().isCompatibleWith(next.orElseThrow());
    }

    /** Czas trwania utworu na osi wieczoru — z domyślną długością singla. */
    public long durationMs(SetCandidate candidate) {

        return Optional.ofNullable(candidate.track().getDurationMs())
            .filter(duration -> duration > 0)
            .map(Integer::longValue)
            .orElse(DEFAULT_TRACK_MS);
    }

    /**
     * Faza, do której dobieramy obok wskazanych sąsiadów. Bierzemy slot utworu
     * <b>przed</b> luką, a gdy go nie ma — utworu po niej: dobieranie jest
     * lokalne i ma trzymać się sąsiada, podczas gdy krzywą całego wieczoru
     * planuje generator. {@code BREAK} i brak slotu nie leżą na krzywej, więc
     * dają „bez preferencji" zamiast fałszywego punktu odniesienia.
     */
    @Nullable
    public DjSlot anchorPhase(@Nullable SetCandidate previous, @Nullable SetCandidate next) {

        return Optional.ofNullable(previous)
            .map(SetCandidate::slot)
            .filter(PHASE_ORDER::contains)
            .or(() -> Optional.ofNullable(next)
                .map(SetCandidate::slot)
                .filter(PHASE_ORDER::contains))
            .orElse(null);
    }

    /**
     * Odległość slotu kandydata od fazy, którą właśnie wypełniamy. {@code BREAK}
     * i brak slotu nie mają miejsca na osi wieczoru — dostają stałą karę zamiast
     * odległości, żeby wchodziły tam, gdzie nie ma nic lepszego.
     */
    private int phaseDistance(@Nullable DjSlot candidateSlot, DjSlot phase) {

        if (Objects.isNull(candidateSlot) || candidateSlot == DjSlot.BREAK) {
            return 2;
        }
        return Math.abs(PHASE_ORDER.indexOf(candidateSlot) - PHASE_ORDER.indexOf(phase));
    }
}
