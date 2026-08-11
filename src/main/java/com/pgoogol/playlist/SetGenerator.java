package com.pgoogol.playlist;

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
 * Układa propozycję setu na zadany czas (M4.2, rozstrzygnięcia w D26) oraz
 * dokłada dalszy ciąg do setu, który już stoi (M4.4, D32).
 *
 * <p><b>Kształt wieczoru wybiera profil</b> {@link SetCurve} (M4.5, D33):
 * fazy D9 i ich kolejność są stałe, zmieniają się tylko proporcje — wesele
 * potrzebuje długiej rozgrzewki, klub długiego szczytu.</p>
 *
 * <p>Ograniczenia twarde i miękkie opisuje {@link SetRules} — tam też siedzi
 * ocena kandydata, wspólna z dobieraniem pojedynczego utworu.</p>
 *
 * <p><b>Powtarzalność:</b> wybór spośród {@value #SHORTLIST} najlepszych kandydatów
 * z ziarnem z żądania. Czysto zachłanny generator dawałby za każdym razem ten sam
 * set, więc po pierwszym uruchomieniu byłby bezużyteczny.</p>
 */
@Component
public class SetGenerator {

    static final int SHORTLIST = 5;

    private final SetRules rules;

    public SetGenerator(SetRules rules) {
        this.rules = rules;
    }

    public SetProposal generate(List<SetCandidate> candidates, Duration target, SetCurve curve,
                                @Nullable Long seed) {
        return extend(candidates, List.of(), target, curve, seed);
    }

    /**
     * Dokłada dalszy ciąg do setu, który już stoi (M4.4). Utwory z {@code prefix}
     * zajmują początek osi wieczoru: liczą się do upływu czasu, blokują powtórkę
     * utworu i odstęp wykonawcy, ale <b>nie wracają w wyniku</b> — propozycja to
     * wyłącznie to, co dochodzi na koniec.
     *
     * <p>Fazy liczymy nad <b>całym</b> zamówionym czasem, nie nad tym, co zostało:
     * set na 90 minut uzupełniany do czterech godzin ma dostać dalszy ciąg
     * wieczoru (środek → szczyt → zamknięcie), a nie drugą rozgrzewkę.</p>
     */
    public SetProposal extend(List<SetCandidate> candidates, List<SetCandidate> prefix,
                              Duration target, SetCurve curve, @Nullable Long seed) {

        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(curve, "curve");
        long targetMs = target.toMillis();
        long resolvedSeed = Objects.requireNonNullElseGet(seed, () -> new Random().nextLong());
        Random random = new Random(resolvedSeed);

        State state = new State(prefix, rules);
        List<String> notes = new ArrayList<>();
        if (state.elapsed >= targetMs && !prefix.isEmpty()) {
            notes.add("Set ma już %d min, czyli co najmniej tyle, ile zamówiono (%d min)"
                .formatted(minutes(state.elapsed), minutes(targetMs)));
        }
        long phaseStart = 0;
        for (SetCurve.Phase phase : curve.phases()) {
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
                .comparingInt((SetCandidate candidate) -> -rules.score(candidate, state.last(), slot))
                .thenComparing(SetCandidate::spotifyId))
            .limit(SHORTLIST)
            .toList();
        return shortlist.isEmpty()
            ? Optional.empty()
            : Optional.of(shortlist.get(random.nextInt(shortlist.size())));
    }

    private long minutes(long millis) {
        return Duration.ofMillis(millis).toMinutes();
    }

    /** Stan układanego setu: co już weszło, ile to trwa i kiedy grał który wykonawca. */
    private static final class State {

        private final List<SetProposal.ProposedTrack> tracks = new ArrayList<>();
        private final Set<String> usedIds = new HashSet<>();
        private final Map<String, Long> lastPlayedByArtist = new HashMap<>();
        private final SetRules rules;
        private final int positionOffset;
        private SetCandidate last;
        private long elapsed;

        private State(List<SetCandidate> prefix, SetRules rules) {

            this.rules = rules;
            this.positionOffset = prefix.size();
            prefix.forEach(this::consume);
        }

        private boolean isAllowed(SetCandidate candidate) {

            if (usedIds.contains(candidate.spotifyId())) {
                return false;
            }
            String artist = candidate.artistKey();
            if (artist.isEmpty()) {
                return true;
            }
            Long lastPlayed = lastPlayedByArtist.get(artist);
            return Objects.isNull(lastPlayed) || elapsed - lastPlayed >= SetRules.ARTIST_GAP_MS;
        }

        private void add(SetCandidate candidate, DjSlot slot) {

            tracks.add(new SetProposal.ProposedTrack(
                positionOffset + tracks.size(), candidate.track(), slot));
            consume(candidate);
        }

        /** Przesuwa oś wieczoru o utwór — bez zapisu w wyniku (dotyczy też prefiksu). */
        private void consume(SetCandidate candidate) {

            usedIds.add(candidate.spotifyId());
            if (!candidate.artistKey().isEmpty()) {
                lastPlayedByArtist.put(candidate.artistKey(), elapsed);
            }
            elapsed += rules.durationMs(candidate);
            last = candidate;
        }

        @Nullable
        private SetCandidate last() {
            return last;
        }
    }
}
