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

    private final SetGenerator generator = new SetGenerator();

    @Test
    @DisplayName("układa set zbliżony do zamówionego czasu")
    void shouldFillRequestedDuration() {

        SetProposal proposal = generator.generate(bigPool(), Duration.ofMinutes(60), 1L);

        assertThat(proposal.tracks()).isNotEmpty();
        assertThat(proposal.totalDurationMs())
            .isBetween(Duration.ofMinutes(58).toMillis(), Duration.ofMinutes(66).toMillis());
        assertThat(proposal.targetDurationMs()).isEqualTo(Duration.ofMinutes(60).toMillis());
    }

    @Test
    @DisplayName("prowadzi set przez fazy wieczoru w kolejności D9")
    void shouldFollowEveningCurve() {

        SetProposal proposal = generator.generate(bigPool(), Duration.ofMinutes(120), 7L);

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

        SetProposal proposal = generator.generate(bigPool(), Duration.ofMinutes(180), 3L);

        List<String> ids = proposal.tracks().stream()
            .map(track -> track.track().getSpotifyId())
            .toList();

        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("twarde ograniczenie: ten sam wykonawca nie wraca przed upływem 30 minut")
    void shouldKeepArtistsApart() {

        SetProposal proposal = generator.generate(bigPool(), Duration.ofMinutes(120), 5L);

        assertThat(minutesBetweenSameArtist(proposal))
            .allSatisfy(gap -> assertThat(gap).isGreaterThanOrEqualTo(SetGenerator.ARTIST_GAP_MINUTES));
    }

    @Test
    @DisplayName("wykonawca bez nazwy nie blokuje puli — odstęp liczymy tylko po znanych")
    void shouldNotBlockOnMissingArtist() {

        List<SetCandidate> pool = IntStream.range(0, 40)
            .mapToObj(index -> candidate("sp-" + index, null, 120, DjSlot.MIDDLE, 3))
            .toList();

        SetProposal proposal = generator.generate(pool, Duration.ofMinutes(60), 11L);

        assertThat(proposal.tracks()).hasSizeGreaterThan(10);
    }

    @Test
    @DisplayName("ten sam seed daje ten sam set, inny — inny")
    void shouldBeReproducibleForTheSameSeed() {

        List<String> first = idsOf(generator.generate(bigPool(), Duration.ofMinutes(60), 42L));
        List<String> same = idsOf(generator.generate(bigPool(), Duration.ofMinutes(60), 42L));
        List<String> other = idsOf(generator.generate(bigPool(), Duration.ofMinutes(60), 43L));

        assertThat(same).isEqualTo(first);
        assertThat(other).isNotEqualTo(first);
    }

    @Test
    @DisplayName("bez seeda oddaje wylosowane ziarno, żeby dało się wrócić do propozycji")
    void shouldReturnGeneratedSeed() {

        SetProposal proposal = generator.generate(bigPool(), Duration.ofMinutes(60), null);

        List<String> repeated = idsOf(
            generator.generate(bigPool(), Duration.ofMinutes(60), proposal.seed()));

        assertThat(repeated).isEqualTo(idsOf(proposal));
    }

    @Test
    @DisplayName("miękkie ograniczenia: przy równych szansach woli utwór bez skoku tempa")
    void shouldPreferSmoothTempoTransitions() {

        List<SetCandidate> pool = List.of(
            candidate("sp-start", "Start", 120, DjSlot.WARMUP, 5),
            candidate("sp-blisko", "Blisko", 124, DjSlot.WARMUP, 5),
            candidate("sp-daleko", "Daleko", 175, DjSlot.WARMUP, 5));

        SetProposal proposal = generator.generate(pool, Duration.ofMinutes(15), 1L);

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

        SetProposal proposal = generator.generate(pool, Duration.ofMinutes(240), 1L);

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

        SetProposal proposal = generator.generate(pool, Duration.ofMinutes(30), 2L);

        assertThat(proposal.tracks()).isNotEmpty();
        assertThat(proposal.totalDurationMs())
            .isGreaterThanOrEqualTo(Duration.ofMinutes(30).toMillis());
    }

    @Test
    @DisplayName("pusta pula daje pusty set z notatką, nie wyjątek")
    void shouldHandleEmptyPool() {

        SetProposal proposal = generator.generate(List.of(), Duration.ofMinutes(60), 1L);

        assertThat(proposal.tracks()).isEmpty();
        assertThat(proposal.notes()).isNotEmpty();
    }

    private int indexOfFirst(List<DjSlot> slots, DjSlot slot) {
        return slots.indexOf(slot);
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
