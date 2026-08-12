package com.pgoogol.playlist;

import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.TrackCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SetGeneratorTest {

    private static final int TRACK_SECONDS = 210;

    private static final List<DjSlot> PHASES_IN_ORDER =
        List.of(DjSlot.WARMUP, DjSlot.MIDDLE, DjSlot.PEAK, DjSlot.CLOSING);

    private final SetGenerator generator = new SetGenerator(new SetRules());

    @Test
    @DisplayName("układa set zbliżony do zamówionego czasu")
    void shouldFillRequestedDuration() {

        SetProposal proposal = generator.generate(bigPool(), Duration.ofMinutes(60), SetCurve.STANDARD, 1L);

        assertThat(proposal.tracks()).isNotEmpty();
        assertThat(proposal.totalDurationMs())
            .isBetween(Duration.ofMinutes(58).toMillis(), Duration.ofMinutes(66).toMillis());
        assertThat(proposal.targetDurationMs()).isEqualTo(Duration.ofMinutes(60).toMillis());
    }

    @Test
    @DisplayName("prowadzi set przez fazy wieczoru w kolejności D9")
    void shouldFollowEveningCurve() {

        SetProposal proposal = generator.generate(bigPool(), Duration.ofMinutes(120), SetCurve.STANDARD, 7L);

        List<DjSlot> slots = proposal.tracks().stream()
            .map(SetProposal.ProposedTrack::djSlot)
            .filter(Objects::nonNull)
            .toList();

        assertThat(slots).startsWith(DjSlot.WARMUP);
        assertThat(slots).endsWith(DjSlot.CLOSING);
        assertThat(indexOfFirst(slots, DjSlot.PEAK))
            .isGreaterThan(indexOfFirst(slots, DjSlot.WARMUP));
        assertThat(indexOfFirst(slots, DjSlot.CLOSING))
            .isGreaterThan(indexOfFirst(slots, DjSlot.PEAK));
    }

    @Test
    @DisplayName("twarde ograniczenie: żaden utwór nie wchodzi do setu dwa razy")
    void shouldNeverRepeatTrack() {

        SetProposal proposal = generator.generate(bigPool(), Duration.ofMinutes(180), SetCurve.STANDARD, 3L);

        List<String> ids = proposal.tracks().stream()
            .map(track -> track.track().getSpotifyId())
            .toList();

        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("twarde ograniczenie: ten sam wykonawca nie wraca przed upływem 30 minut")
    void shouldKeepArtistsApart() {

        SetProposal proposal = generator.generate(bigPool(), Duration.ofMinutes(120), SetCurve.STANDARD, 5L);

        assertThat(minutesBetweenSameArtist(proposal))
            .allSatisfy(gap -> assertThat(gap).isGreaterThanOrEqualTo(SetRules.ARTIST_GAP_MINUTES));
    }

    @Test
    @DisplayName("wykonawca bez nazwy nie blokuje puli — odstęp liczymy tylko po znanych")
    void shouldNotBlockOnMissingArtist() {

        List<SetCandidate> pool = IntStream.range(0, 40)
            .mapToObj(index -> candidate("sp-" + index, null, 120, DjSlot.MIDDLE, 3))
            .toList();

        SetProposal proposal = generator.generate(pool, Duration.ofMinutes(60), SetCurve.STANDARD, 11L);

        assertThat(proposal.tracks()).hasSizeGreaterThan(10);
    }

    @Test
    @DisplayName("ten sam seed daje ten sam set, inny — inny")
    void shouldBeReproducibleForTheSameSeed() {

        List<String> first = idsOf(generator.generate(bigPool(), Duration.ofMinutes(60), SetCurve.STANDARD, 42L));
        List<String> same = idsOf(generator.generate(bigPool(), Duration.ofMinutes(60), SetCurve.STANDARD, 42L));
        List<String> other = idsOf(generator.generate(bigPool(), Duration.ofMinutes(60), SetCurve.STANDARD, 43L));

        assertThat(same).isEqualTo(first);
        assertThat(other).isNotEqualTo(first);
    }

    @Test
    @DisplayName("bez seeda oddaje wylosowane ziarno, żeby dało się wrócić do propozycji")
    void shouldReturnGeneratedSeed() {

        SetProposal proposal = generator.generate(bigPool(), Duration.ofMinutes(60), SetCurve.STANDARD, null);

        List<String> repeated = idsOf(
            generator.generate(bigPool(), Duration.ofMinutes(60), SetCurve.STANDARD, proposal.seed()));

        assertThat(repeated).isEqualTo(idsOf(proposal));
    }

    @Test
    @DisplayName("miękkie ograniczenia: przy równych szansach woli utwór bez skoku tempa")
    void shouldPreferSmoothTempoTransitions() {

        List<SetCandidate> pool = List.of(
            candidate("sp-start", "Start", 120, DjSlot.WARMUP, 5),
            candidate("sp-blisko", "Blisko", 124, DjSlot.WARMUP, 5),
            candidate("sp-daleko", "Daleko", 175, DjSlot.WARMUP, 5));

        SetProposal proposal = generator.generate(pool, Duration.ofMinutes(15), SetCurve.STANDARD, 1L);

        List<Integer> bpms = proposal.tracks().stream()
            .map(track -> track.track().getBpm())
            .toList();
        List<Integer> jumps = IntStream.range(1, bpms.size())
            .mapToObj(index -> Math.abs(bpms.get(index) - bpms.get(index - 1)))
            .toList();

        assertThat(jumps).isNotEmpty();
        assertThat(jumps.get(0)).isLessThan(50);
    }

    @Test
    @DisplayName("miękkie ograniczenia nie blokują setu — wąska pula daje set z notatką")
    void shouldReturnShorterSetInsteadOfFailing() {

        List<SetCandidate> pool = List.of(
            candidate("sp-1", "A", 120, DjSlot.MIDDLE, 3),
            candidate("sp-2", "B", 121, DjSlot.MIDDLE, 3));

        SetProposal proposal = generator.generate(pool, Duration.ofMinutes(240), SetCurve.STANDARD, 1L);

        assertThat(proposal.tracks()).hasSize(2);
        assertThat(proposal.totalDurationMs()).isLessThan(proposal.targetDurationMs());
        assertThat(proposal.notes()).isNotEmpty();
        assertThat(proposal.notes().getLast()).contains("krótszy");
    }

    @Test
    @DisplayName("utwór bez czasu trwania liczy się jako typowy singiel, nie zawiesza pętli")
    void shouldAssumeDefaultDurationForUnknownLength() {

        List<SetCandidate> pool = IntStream.range(0, 30)
            .mapToObj(index -> {
                TrackCatalog track = new TrackCatalog("sp-" + index, "Utwór " + index, "Wyk " + index);
                track.setBpm(120);
                track.setGenreFamily(GenreFamily.LATIN);
                return new SetCandidate(track, DjSlot.MIDDLE, 3);
            })
            .toList();

        SetProposal proposal = generator.generate(pool, Duration.ofMinutes(30), SetCurve.STANDARD, 2L);

        assertThat(proposal.tracks()).isNotEmpty();
        assertThat(proposal.totalDurationMs())
            .isGreaterThanOrEqualTo(Duration.ofMinutes(30).toMillis());
    }

    @Test
    @DisplayName("pusta pula daje pusty set z notatką, nie wyjątek")
    void shouldHandleEmptyPool() {

        SetProposal proposal = generator.generate(List.of(), Duration.ofMinutes(60), SetCurve.STANDARD, 1L);

        assertThat(proposal.tracks()).isEmpty();
        assertThat(proposal.notes()).isNotEmpty();
    }

    @Test
    @DisplayName("profil wieczoru zmienia proporcje faz: klub dostaje dłuższy szczyt niż wesele")
    void shouldFollowSelectedCurveProfile() {

        SetProposal club = generator.generate(bigPool(), Duration.ofMinutes(240),
            SetCurve.CLUB, 13L);
        SetProposal wedding = generator.generate(bigPool(), Duration.ofMinutes(240),
            SetCurve.WEDDING, 13L);

        assertThat(count(club, DjSlot.PEAK)).isGreaterThan(count(wedding, DjSlot.PEAK));
        assertThat(count(wedding, DjSlot.WARMUP)).isGreaterThan(count(club, DjSlot.WARMUP));
    }

    @Test
    @DisplayName("profil bez wyraźnego szczytu rozkłada fazy po równo")
    void shouldSpreadPhasesEvenly() {

        SetProposal proposal = generator.generate(bigPool(), Duration.ofMinutes(240),
            SetCurve.EVEN, 13L);

        List<Integer> counts = PHASES_IN_ORDER.stream()
            .map(slot -> count(proposal, slot))
            .toList();

        // różnica wynika tylko z tego, że utwór nie dzieli się na pół
        assertThat(java.util.Collections.max(counts) - java.util.Collections.min(counts))
            .isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("uzupełnianie: oddaje sam dalszy ciąg, bez utworów, które już są w secie")
    void shouldExtendWithoutRepeatingTracksAlreadyOnTheSet() {

        List<SetCandidate> current = existingSet(10);

        SetProposal proposal =
            generator.extend(bigPool(), current, Duration.ofMinutes(120), SetCurve.STANDARD, 5L);

        List<String> added = idsOf(proposal);
        assertThat(added).isNotEmpty();
        assertThat(added).doesNotContainAnyElementsOf(
            current.stream().map(SetCandidate::spotifyId).toList());
    }

    @Test
    @DisplayName("uzupełnianie: pozycje dalszego ciągu zaczynają się na końcu setu")
    void shouldNumberAddedTracksAfterTheExistingSet() {

        SetProposal proposal =
            generator.extend(bigPool(), existingSet(10), Duration.ofMinutes(120), SetCurve.STANDARD, 5L);

        assertThat(proposal.tracks().getFirst().position()).isEqualTo(10);
        assertThat(proposal.tracks().stream().map(SetProposal.ProposedTrack::position))
            .isSorted();
    }

    @Test
    @DisplayName("uzupełnianie nie zaczyna wieczoru od nowa — set w połowie dostaje szczyt, nie rozgrzewkę")
    void shouldContinueTheEveningCurveInsteadOfRestartingIt() {

        // 20 utworów po 3,5 min = 70 min z zamówionych 120 — to już faza szczytu
        SetProposal proposal =
            generator.extend(bigPool(), existingSet(20), Duration.ofMinutes(120), SetCurve.STANDARD, 5L);

        List<DjSlot> slots = proposal.tracks().stream()
            .map(SetProposal.ProposedTrack::djSlot)
            .toList();

        assertThat(slots).isNotEmpty();
        assertThat(slots).doesNotContain(DjSlot.WARMUP, DjSlot.MIDDLE);
        assertThat(slots).startsWith(DjSlot.PEAK);
        assertThat(slots).endsWith(DjSlot.CLOSING);
    }

    @Test
    @DisplayName("uzupełnianie: set dłuższy od zamówionego czasu dostaje notatkę zamiast utworów")
    void shouldAddNothingWhenSetIsAlreadyLongEnough() {

        SetProposal proposal =
            generator.extend(bigPool(), existingSet(40), Duration.ofMinutes(120), SetCurve.STANDARD, 5L);

        assertThat(proposal.tracks()).isEmpty();
        assertThat(proposal.notes()).isNotEmpty();
        assertThat(proposal.notes().getFirst()).contains("co najmniej tyle");
    }

    @Test
    @DisplayName("uzupełnianie: wykonawca z końcówki setu nie wraca od razu na początku dobranej części")
    void shouldRespectArtistGapAcrossTheExistingSet() {

        SetCandidate lastOnSet = candidate("sp-na-secie", "Wykonawca 0", 120, DjSlot.PEAK, 5);

        SetProposal proposal =
            generator.extend(bigPool(), List.of(lastOnSet), Duration.ofMinutes(60), SetCurve.STANDARD, 5L);

        List<String> firstArtists = proposal.tracks().stream()
            .limit(3)
            .map(track -> track.track().getArtist())
            .toList();
        assertThat(firstArtists).doesNotContain("Wykonawca 0");
    }

    /** Set, który już stoi — inne identyfikatory i wykonawcy niż pula kandydatów. */
    private List<SetCandidate> existingSet(int size) {

        return IntStream.range(0, size)
            .mapToObj(index -> candidate(
                "na-secie-" + index, "Zespół " + index, 120, DjSlot.MIDDLE, 4))
            .toList();
    }

    private int indexOfFirst(List<DjSlot> slots, DjSlot slot) {
        return slots.indexOf(slot);
    }

    private int count(SetProposal proposal, DjSlot slot) {

        return (int) proposal.tracks().stream()
            .filter(track -> track.djSlot() == slot)
            .count();
    }

    private List<String> idsOf(SetProposal proposal) {

        return proposal.tracks().stream().map(track -> track.track().getSpotifyId()).toList();
    }

    /** Odstępy w minutach między kolejnymi wystąpieniami tego samego wykonawcy. */
    private List<Long> minutesBetweenSameArtist(SetProposal proposal) {

        List<Long> gaps = new ArrayList<>();
        List<SetProposal.ProposedTrack> tracks = proposal.tracks();
        long elapsed = 0;
        var lastByArtist = new java.util.HashMap<String, Long>();
        for (SetProposal.ProposedTrack track : tracks) {
            String artist = track.track().getArtist();
            Long previous = lastByArtist.put(artist, elapsed);
            if (Objects.nonNull(previous)) {
                gaps.add(Duration.ofMillis(elapsed - previous).toMinutes());
            }
            elapsed += Objects.requireNonNullElse(track.track().getDurationMs(), 0);
        }
        return gaps;
    }

    /** Pula z powtarzającymi się wykonawcami i wszystkimi fazami wieczoru. */
    private List<SetCandidate> bigPool() {

        List<DjSlot> slots = List.of(DjSlot.WARMUP, DjSlot.MIDDLE, DjSlot.PEAK, DjSlot.CLOSING);
        return IntStream.range(0, 120)
            .mapToObj(index -> candidate(
                "sp-" + index,
                "Wykonawca " + (index % 12),
                90 + (index % 8) * 6,
                slots.get(index % slots.size()),
                1 + index % 5))
            .toList();
    }

    private SetCandidate candidate(String spotifyId, String artist, int bpm, DjSlot slot,
                                   int rating) {

        TrackCatalog track = new TrackCatalog(spotifyId, "Utwór " + spotifyId, artist);
        track.setBpm(bpm);
        track.setDurationMs(TRACK_SECONDS * 1000);
        track.setGenreFamily(GenreFamily.LATIN);
        return new SetCandidate(track, slot, rating);
    }
}
