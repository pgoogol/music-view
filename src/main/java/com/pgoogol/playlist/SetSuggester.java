package com.pgoogol.playlist;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Dobiera utwór na wskazane miejsce w gotowym secie (M4.4, D32) — „co zagrać
 * po tym, co już mam".
 *
 * <p>W odróżnieniu od generatora <b>nie losuje</b>: DJ dostaje listę
 * uszeregowaną od najlepiej pasującego i wybiera sam, więc ziarno nie miałoby
 * czego powtarzać, a ta sama luka pytana dwa razy musi dać tę samą odpowiedź.</p>
 *
 * <p>Kandydat ma <b>dwóch sąsiadów</b>, nie jednego: karę za przejście liczymy
 * i od utworu przed luką, i do utworu za nią. Bez tego dokładanie w środek setu
 * psułoby przejście, które DJ przed chwilą ułożył.</p>
 */
@Component
public class SetSuggester {

    public static final int DEFAULT_LIMIT = 5;
    public static final int MAX_LIMIT = 20;

    private final SetRules rules;

    public SetSuggester(SetRules rules) {
        this.rules = rules;
    }

    /**
     * @param set      obecny skład setu w kolejności grania
     * @param position miejsce wstawienia (0 = przed pierwszym utworem,
     *                 {@code set.size()} = na koniec); pozycję waliduje wołający
     */
    public List<SetSuggestion> suggest(List<SetCandidate> candidates, List<SetCandidate> set,
                                       int position, int limit) {

        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(set, "set");
        SetCandidate previous = position > 0 ? set.get(position - 1) : null;
        SetCandidate next = position < set.size() ? set.get(position) : null;
        // sąsiad odniesienia; przy pustym secie nie ma żadnego i powody zostają puste
        SetCandidate anchor = Optional.ofNullable(previous).orElse(next);
        DjSlot phase = rules.anchorPhase(previous, next);
        long[] startsMs = startsMs(set);
        long insertAt = position < startsMs.length ? startsMs[position] : totalMs(set);

        return candidates.stream()
            .filter(candidate -> isAllowed(candidate, set, startsMs, insertAt))
            .sorted(Comparator
                .comparingInt((SetCandidate candidate) -> -rank(candidate, previous, next, phase))
                .thenComparing(SetCandidate::spotifyId))
            .limit(Math.clamp(limit, 1, MAX_LIMIT))
            .map(candidate -> toSuggestion(candidate, anchor))
            .toList();
    }

    /** Ocena z {@link SetRules} plus kara za przejście do utworu za luką. */
    private int rank(SetCandidate candidate, @Nullable SetCandidate previous,
                     @Nullable SetCandidate next, @Nullable DjSlot phase) {

        int score = rules.score(candidate, previous, phase);
        return Objects.isNull(next) ? score : score - rules.transitionPenalty(candidate, next);
    }

    private SetSuggestion toSuggestion(SetCandidate candidate, @Nullable SetCandidate anchor) {

        if (Objects.isNull(anchor)) {
            return new SetSuggestion(candidate.track(), candidate.slot(), null, null);
        }
        Integer bpmDelta = anchor.bpm()
            .flatMap(before -> candidate.bpm().map(after -> after - before))
            .orElse(null);
        Boolean harmonic = anchor.key().isEmpty() || candidate.key().isEmpty()
            ? null
            : rules.isHarmonic(anchor, candidate);
        return new SetSuggestion(candidate.track(), candidate.slot(), bpmDelta, harmonic);
    }

    /**
     * Ograniczenia twarde na dobieranym utworze: nie ma go jeszcze w secie
     * i nie postawi tego samego wykonawcy bliżej niż {@value SetRules#ARTIST_GAP_MINUTES}
     * minut od jego innego utworu. Odstęp liczymy od momentu wstawienia w obie
     * strony — utwór dołożony w środek i tak przesuwa całą resztę wieczoru,
     * więc dokładniejsza arytmetyka niczego by tu nie kupiła.
     */
    private boolean isAllowed(SetCandidate candidate, List<SetCandidate> set, long[] startsMs,
                              long insertAt) {

        String artist = candidate.artistKey();
        return IntStream.range(0, set.size()).noneMatch(index -> {
            SetCandidate onSet = set.get(index);
            return Objects.equals(onSet.spotifyId(), candidate.spotifyId())
                || (!artist.isEmpty() && Objects.equals(onSet.artistKey(), artist)
                    && Math.abs(startsMs[index] - insertAt) < SetRules.ARTIST_GAP_MS);
        });
    }

    /**
     * Moment startu każdego utworu setu na osi wieczoru — liczony raz na wywołanie,
     * bo inaczej sprawdzenie odstępu wykonawcy sumowałoby czasy dla każdego
     * kandydata z osobna. Suma prefiksowa to jedno z tych miejsc, gdzie zwykła
     * pętla jest tańsza od strumienia.
     */
    private long[] startsMs(List<SetCandidate> set) {

        long[] starts = new long[set.size()];
        long elapsed = 0;
        for (int index = 0; index < set.size(); index++) {
            starts[index] = elapsed;
            elapsed += rules.durationMs(set.get(index));
        }
        return starts;
    }

    private long totalMs(List<SetCandidate> set) {
        return set.stream().mapToLong(rules::durationMs).sum();
    }
}
