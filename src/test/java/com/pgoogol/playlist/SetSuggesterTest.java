package com.pgoogol.playlist;

import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.TrackCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SetSuggesterTest {

    private static final int TRACK_SECONDS = 210;

    private final SetSuggester suggester = new SetSuggester(new SetRules());

    @Test
    @DisplayName("dobiera na koniec setu i pomija utwory, które już w nim są")
    void shouldSkipTracksAlreadyOnTheSet() {

        SetCandidate onSet = candidate("sp-1", "A", 120, "A minor", DjSlot.PEAK);
        List<SetCandidate> pool = List.of(onSet, candidate("sp-2", "B", 122, "A minor", DjSlot.PEAK));

        List<SetSuggestion> suggestions =
            suggester.suggest(pool, List.of(onSet), 1, SetSuggester.DEFAULT_LIMIT);

        assertThat(suggestions).extracting(suggestion -> suggestion.track().getSpotifyId())
            .containsExactly("sp-2");
    }

    @Test
    @DisplayName("utwór wstawiany w środek liczy się z sąsiadem po obu stronach")
    void shouldWeighBothNeighboursWhenInsertingInTheMiddle() {

        List<SetCandidate> set = List.of(
            candidate("sp-przed", "Przed", 120, "A minor", DjSlot.PEAK),
            candidate("sp-po", "Po", 124, "A minor", DjSlot.PEAK));
        List<SetCandidate> pool = List.of(
            // pasuje do lewego sąsiada, ale zderza się z prawym
            candidate("sp-rwie", "Rwie", 122, "D# minor", DjSlot.PEAK),
            candidate("sp-gladko", "Gładko", 122, "A minor", DjSlot.PEAK));

        List<SetSuggestion> suggestions =
            suggester.suggest(pool, set, 1, SetSuggester.DEFAULT_LIMIT);

        assertThat(suggestions).extracting(suggestion -> suggestion.track().getSpotifyId())
            .containsExactly("sp-gladko", "sp-rwie");
    }

    @Test
    @DisplayName("twarde ograniczenie: wykonawca grający obok luki nie wraca przed upływem 30 minut")
    void shouldKeepArtistsApartAroundTheGap() {

        List<SetCandidate> set = List.of(candidate("sp-1", "Powtórka", 120, null, DjSlot.PEAK));
        List<SetCandidate> pool = List.of(
            candidate("sp-2", "Powtórka", 121, null, DjSlot.PEAK),
            candidate("sp-3", "Ktoś inny", 121, null, DjSlot.PEAK));

        List<SetSuggestion> suggestions =
            suggester.suggest(pool, set, 1, SetSuggester.DEFAULT_LIMIT);

        assertThat(suggestions).extracting(suggestion -> suggestion.track().getArtist())
            .containsExactly("Ktoś inny");
    }

    @Test
    @DisplayName("wykonawca z odległej części setu wraca bez przeszkód")
    void shouldAllowArtistFromFarPartOfTheSet() {

        // 12 utworów po 3,5 min = 42 min, więc wykonawca z pierwszego utworu
        // jest o ponad pół godziny od końca setu
        List<SetCandidate> set = IntStream.range(0, 12)
            .mapToObj(index -> candidate(
                "sp-%d".formatted(index), index == 0 ? "Powtórka" : "Wyk " + index,
                120, null, DjSlot.PEAK))
            .toList();
        List<SetCandidate> pool = List.of(candidate("sp-nowy", "Powtórka", 121, null, DjSlot.PEAK));

        List<SetSuggestion> suggestions =
            suggester.suggest(pool, set, set.size(), SetSuggester.DEFAULT_LIMIT);

        assertThat(suggestions).hasSize(1);
    }

    @Test
    @DisplayName("podaje powody: różnicę tempa i zgodność tonacji wobec sąsiada")
    void shouldReportTransitionReasons() {

        List<SetCandidate> set = List.of(candidate("sp-1", "A", 120, "A minor", DjSlot.PEAK));
        List<SetCandidate> pool = List.of(
            candidate("sp-zgodny", "B", 126, "C major", DjSlot.PEAK),
            candidate("sp-niezgodny", "C", 118, "D# minor", DjSlot.PEAK),
            candidate("sp-bez-danych", "D", null, null, DjSlot.PEAK));

        List<SetSuggestion> suggestions =
            suggester.suggest(pool, set, 1, SetSuggester.DEFAULT_LIMIT);

        assertThat(suggestions).anySatisfy(suggestion -> {
            assertThat(suggestion.track().getSpotifyId()).isEqualTo("sp-zgodny");
            assertThat(suggestion.bpmDelta()).isEqualTo(6);
            assertThat(suggestion.harmonic()).isTrue();
        });
        assertThat(suggestions).anySatisfy(suggestion -> {
            assertThat(suggestion.track().getSpotifyId()).isEqualTo("sp-niezgodny");
            assertThat(suggestion.bpmDelta()).isEqualTo(-2);
            assertThat(suggestion.harmonic()).isFalse();
        });
        // brak tonacji albo BPM to brak danych, nie zderzenie (D25)
        assertThat(suggestions).anySatisfy(suggestion -> {
            assertThat(suggestion.track().getSpotifyId()).isEqualTo("sp-bez-danych");
            assertThat(suggestion.bpmDelta()).isNull();
            assertThat(suggestion.harmonic()).isNull();
        });
    }

    @Test
    @DisplayName("dobieranie na początek opiera się o utwór, który po nim nastąpi")
    void shouldUseFollowingTrackAsAnchorWhenInsertingAtTheFront() {

        List<SetCandidate> set = List.of(candidate("sp-1", "A", 120, "A minor", DjSlot.WARMUP));
        List<SetCandidate> pool = List.of(candidate("sp-2", "B", 110, "A minor", DjSlot.WARMUP));

        List<SetSuggestion> suggestions =
            suggester.suggest(pool, set, 0, SetSuggester.DEFAULT_LIMIT);

        assertThat(suggestions).singleElement()
            .satisfies(suggestion -> assertThat(suggestion.bpmDelta()).isEqualTo(-10));
    }

    @Test
    @DisplayName("pusty set: dobieramy bez sąsiada, więc bez powodów przejścia")
    void shouldSuggestForEmptySetWithoutTransitionReasons() {

        List<SetCandidate> pool = List.of(candidate("sp-1", "A", 120, "A minor", DjSlot.WARMUP));

        List<SetSuggestion> suggestions =
            suggester.suggest(pool, List.of(), 0, SetSuggester.DEFAULT_LIMIT);

        assertThat(suggestions).singleElement().satisfies(suggestion -> {
            assertThat(suggestion.bpmDelta()).isNull();
            assertThat(suggestion.harmonic()).isNull();
        });
    }

    @Test
    @DisplayName("ta sama luka pytana dwa razy daje tę samą listę — dobieranie nie losuje")
    void shouldBeDeterministic() {

        List<SetCandidate> set = List.of(candidate("sp-1", "A", 120, "A minor", DjSlot.PEAK));

        List<String> first = idsOf(suggester.suggest(pool(), set, 1, SetSuggester.DEFAULT_LIMIT));
        List<String> second = idsOf(suggester.suggest(pool(), set, 1, SetSuggester.DEFAULT_LIMIT));

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("limit przycina listę, a jego brak zostawia domyślną piątkę")
    void shouldRespectLimit() {

        List<SetCandidate> set = List.of(candidate("sp-1", "A", 120, "A minor", DjSlot.PEAK));

        assertThat(suggester.suggest(pool(), set, 1, 3)).hasSize(3);
        assertThat(suggester.suggest(pool(), set, 1, SetSuggester.DEFAULT_LIMIT))
            .hasSize(SetSuggester.DEFAULT_LIMIT);
        assertThat(suggester.suggest(pool(), set, 1, 500)).hasSize(SetSuggester.MAX_LIMIT);
    }

    private List<String> idsOf(List<SetSuggestion> suggestions) {

        return suggestions.stream().map(suggestion -> suggestion.track().getSpotifyId()).toList();
    }

    /** Pula szersza niż limit, z różnym tempem i oceną — do testów kolejności. */
    private List<SetCandidate> pool() {

        return IntStream.range(0, 30)
            .mapToObj(index -> candidate(
                "pula-%02d".formatted(index), "Wykonawca " + index,
                110 + index, "A minor", DjSlot.PEAK))
            .toList();
    }

    private SetCandidate candidate(String spotifyId, String artist, @Nullable Integer bpm,
                                   @Nullable String musicalKey, DjSlot slot) {

        TrackCatalog track = new TrackCatalog(spotifyId, "Utwór " + spotifyId, artist);
        track.setBpm(bpm);
        track.setMusicalKey(musicalKey);
        track.setDurationMs(TRACK_SECONDS * 1000);
        track.setGenreFamily(GenreFamily.LATIN);
        return new SetCandidate(track, slot, 3);
    }
}
