package com.pgoogol.playlist;

import com.pgoogol.catalog.CamelotKey;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Układa propozycję setu na zadany czas (M4.2, rozstrzygnięcia w D26).
 *
 * <p><b>Krzywa wieczoru jest stała</b> — rozgrzewka 25%, środek 30%, szczyt 30%,
 * zamknięcie 15% czasu. Parametryzacja byłaby opcją dla jednego użytkownika,
 * który i tak poprawia wynik ręcznie; progi są punktem wyjścia, jak w D21.</p>
 *
 * <p><b>Ograniczenia twarde</b> (zawężają pulę): utwór wchodzi do setu raz,
 * a ten sam wykonawca nie częściej niż raz na {@value #ARTIST_GAP_MINUTES} minut.
 * <b>Miękkie</b> (kary w ocenie kandydata): skok BPM ponad próg, brak zgodności
 * harmonicznej, niska ocena, brak BPM. Gdyby miękkie zrobić twardymi, generator
 * przy wąskiej bibliotece zwracałby pustkę zamiast setu z ostrzeżeniami — a DJ
 * woli set do poprawienia niż komunikat.</p>
 *
 * <p><b>Powtarzalność:</b> wybór spośród {@value #SHORTLIST} najlepszych kandydatów
 * z ziarnem z żądania. Czysto zachłanny generator dawałby za każdym razem ten sam
 * set, więc po pierwszym uruchomieniu byłby bezużyteczny.</p>
 */
@Component
public class SetGenerator {

    static final int ARTIST_GAP_MINUTES = 30;
    static final int SHORTLIST = 5;
    static final int BPM_JUMP_TOLERANCE = 15;

    /** Utwór bez znanego czasu liczymy jako typowy singiel — inaczej zawiesiłby pętlę. */
    static final long DEFAULT_TRACK_MS = Duration.ofMinutes(3).plusSeconds(30).toMillis();

    private static final long ARTIST_GAP_MS = Duration.ofMinutes(ARTIST_GAP_MINUTES).toMillis();

    /** Krzywa wieczoru (D26) — udziały sumują się do 1.0. */
    private static final List<Phase> PHASES = List.of(
        new Phase(DjSlot.WARMUP, 0.25),
        new Phase(DjSlot.MIDDLE, 0.30),
        new Phase(DjSlot.PEAK, 0.30),
        new Phase(DjSlot.CLOSING, 0.15));

    private static final List<DjSlot> PHASE_ORDER =
        List.of(DjSlot.WARMUP, DjSlot.MIDDLE, DjSlot.PEAK, DjSlot.CLOSING);

    public SetProposal generate(List<SetCandidate> candidates, Duration target, @Nullable Long seed) {

        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(target, "target");
        long targetMs = target.toMillis();
        long resolvedSeed = Objects.requireNonNullElseGet(seed, () -> new Random().nextLong());
        Random random = new Random(resolvedSeed);

        State state = new State();
        List<String> notes = new ArrayList<>();
        long phaseStart = 0;
        for (Phase phase : PHASES) {
            phaseStart += Math.round(targetMs * phase.share());
            fillPhase(candidates, state, phase.slot(), phaseStart, random, notes);
        }
        if (state.elapsed < targetMs) {
            notes.add("Set jest krótszy od zamówionego (%d z %d min) — pula kandydatów się skończyła"
                .formatted(minutes(state.elapsed), minutes(targetMs)));
        }
        return new SetProposal(state.tracks, state.elapsed, targetMs, resolvedSeed, notes);
    }

    private void fillPhase(List<SetCandidate> candidates, State state, DjSlot slot,
                           long phaseEnd, Random random, List<String> notes) {

        int addedInPhase = 0;
        while (state.elapsed < phaseEnd) {
            Optional<SetCandidate> picked = pick(candidates, state, slot, random);
            if (picked.isEmpty()) {
                notes.add("Faza %s: zabrakło pasujących utworów (dodano %d)"
                    .formatted(slot, addedInPhase));
                return;
            }
            state.add(picked.orElseThrow(), slot);
            addedInPhase++;
        }
    }

    private Optional<SetCandidate> pick(List<SetCandidate> candidates, State state, DjSlot slot,
                                        Random random) {

        List<SetCandidate> shortlist = candidates.stream()
            .filter(candidate -> state.isAllowed(candidate))
            .sorted(Comparator
                .comparingInt((SetCandidate candidate) -> -score(candidate, state, slot))
                .thenComparing(SetCandidate::spotifyId))
            .limit(SHORTLIST)
            .toList();
        return shortlist.isEmpty()
            ? Optional.empty()
            : Optional.of(shortlist.get(random.nextInt(shortlist.size())));
    }

    /**
     * Ocena kandydata na daną fazę — im wyżej, tym lepiej pasuje. Wartości są
     * względne i mają znaczenie tylko wobec siebie nawzajem; ich zadaniem jest
     * ustawić kolejność, a nie zmierzyć „jakość" utworu.
     */
    private int score(SetCandidate candidate, State state, DjSlot slot) {

        int score = 100 - 40 * phaseDistance(candidate.slot(), slot);
        score += Optional.ofNullable(candidate.rating()).orElse(0) * 6;
        score += Optional.ofNullable(candidate.track().getPopularity()).orElse(0) / 20;

        Optional<Integer> bpm = candidate.bpm();
        if (bpm.isEmpty()) {
            score -= 30;
        }
        SetCandidate previous = state.last();
        if (Objects.isNull(previous)) {
            return score;
        }
        if (bpm.isPresent() && previous.bpm().isPresent()) {
            int jump = Math.abs(bpm.orElseThrow() - previous.bpm().orElseThrow());
            score -= 2 * Math.max(0, jump - BPM_JUMP_TOLERANCE);
        }
        if (!isHarmonic(previous, candidate)) {
            score -= 25;
        }
        return score;
    }

    /** Nieznana tonacja po którejkolwiek stronie to brak danych, nie zderzenie (D25). */
    private boolean isHarmonic(SetCandidate previous, SetCandidate candidate) {

        Optional<CamelotKey> before = previous.key();
        Optional<CamelotKey> next = candidate.key();
        return before.isEmpty() || next.isEmpty()
            || before.orElseThrow().isCompatibleWith(next.orElseThrow());
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

    private long minutes(long millis) {
        return Duration.ofMillis(millis).toMinutes();
    }

    private record Phase(DjSlot slot, double share) { }

    /** Stan układanego setu: co już weszło, ile to trwa i kiedy grał który wykonawca. */
    private static final class State {

        private final List<SetProposal.ProposedTrack> tracks = new ArrayList<>();
        private final Set<String> usedIds = new HashSet<>();
        private final Map<String, Long> lastPlayedByArtist = new HashMap<>();
        private SetCandidate last;
        private long elapsed;

        private boolean isAllowed(SetCandidate candidate) {

            if (usedIds.contains(candidate.spotifyId())) {
                return false;
            }
            String artist = candidate.artistKey();
            if (artist.isEmpty()) {
                return true;
            }
            Long lastPlayed = lastPlayedByArtist.get(artist);
            return Objects.isNull(lastPlayed) || elapsed - lastPlayed >= ARTIST_GAP_MS;
        }

        private void add(SetCandidate candidate, DjSlot slot) {

            tracks.add(new SetProposal.ProposedTrack(tracks.size(), candidate.track(), slot));
            usedIds.add(candidate.spotifyId());
            if (!candidate.artistKey().isEmpty()) {
                lastPlayedByArtist.put(candidate.artistKey(), elapsed);
            }
            elapsed += Optional.ofNullable(candidate.track().getDurationMs())
                .filter(duration -> duration > 0)
                .map(Integer::longValue)
                .orElse(DEFAULT_TRACK_MS);
            last = candidate;
        }

        @Nullable
        private SetCandidate last() {
            return last;
        }
    }
}
