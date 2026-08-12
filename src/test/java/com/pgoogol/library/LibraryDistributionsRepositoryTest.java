package com.pgoogol.library;

import com.pgoogol.library.LibraryOverview.Bucket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Przeliczenie surowych tonacji na koło Camelot jest czystą logiką — baza podaje
 * tylko {@code group by musical_key}, więc ta część nie potrzebuje Postgresa.
 * Reszta repozytorium (SQL) ma pokrycie w {@code LibraryOverviewIntegrationTest}.
 */
class LibraryDistributionsRepositoryTest {

    private final LibraryDistributionsRepository repository =
        new LibraryDistributionsRepository(null);

    @Test
    @DisplayName("zapisy enharmoniczne tej samej tonacji schodzą się w jedną pozycję koła")
    void camelotKeys_mergesEnharmonicSpellings() {

        List<Bucket> raw = List.of(
            new Bucket("EB MINOR", 12),
            new Bucket("D# MINOR", 8));

        List<Bucket> wheel = repository.camelotKeys(raw);

        assertThat(wheel).containsExactly(new Bucket("2A", 20));
    }

    @Test
    @DisplayName("kolejność jest kolejnością koła, a nie alfabetyczną — 10A stoi za 9A")
    void camelotKeys_areOrderedByWheelPosition() {

        List<Bucket> raw = List.of(
            new Bucket("B MINOR", 3),      // 10A
            new Bucket("E MINOR", 5),      // 9A
            new Bucket("C MAJOR", 7));     // 8B

        List<Bucket> wheel = repository.camelotKeys(raw);

        assertThat(wheel).extracting(Bucket::label).containsExactly("8B", "9A", "10A");
    }

    @Test
    @DisplayName("tonacje nieczytelne i puste lądują we wspólnym koszyku, zamiast znikać")
    void camelotKeys_keepUnparsableKeysAsOwnBucket() {

        List<Bucket> raw = List.of(
            new Bucket("BEZ TONACJI", 380),
            new Bucket("H-DUR", 4),
            new Bucket("A MINOR", 6));

        List<Bucket> wheel = repository.camelotKeys(raw);

        assertThat(wheel).containsExactly(
            new Bucket("8A", 6),
            new Bucket("BEZ TONACJI", 384));
    }

    @Test
    @DisplayName("pusty katalog daje puste koło, a nie 24 zera")
    void camelotKeys_emptyInputGivesEmptyWheel() {
        assertThat(repository.camelotKeys(List.of())).isEmpty();
    }
}
