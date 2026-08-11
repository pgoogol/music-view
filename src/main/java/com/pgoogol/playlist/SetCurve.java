package com.pgoogol.playlist;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Kształt wieczoru dla generatora setu (M4.5, D33): jak długo trwa każda faza
 * D9 w stosunku do całości. Fazy i ich kolejność są stałe — profil zmienia
 * wyłącznie proporcje, bo to jedyne, co realnie różni wesele od klubu.
 *
 * <p>Do M4.4 krzywa była zaszyta w generatorze jako 25/30/30/15 (D26).
 * Wystarczała, dopóki narzędzie układało „jakiś wieczór"; przy trzeciej
 * imprezie pod rząd okazało się, że wesele potrzebuje długiej rozgrzewki
 * (goście jedzą), a klub odwrotnie — parkiet jest gorący od pierwszej godziny.
 * Parametryzacja udziałów co do procenta byłaby ustawianiem suwaków bez
 * odpowiedzi na pytanie „ile", więc profil jest wyborem z krótkiej listy.</p>
 */
public enum SetCurve {

    /** Domyślny przebieg z D26 — punkt wyjścia, gdy nie wiadomo, co za impreza. */
    STANDARD(0.25, 0.30, 0.30, 0.15),

    /** Wesele: długa rozgrzewka (goście przy stołach), szczyt krótszy i wcześniej. */
    WEDDING(0.30, 0.30, 0.25, 0.15),

    /** Klub: parkiet gotowy od początku, więc rozgrzewka krótka, a szczyt długi. */
    CLUB(0.15, 0.25, 0.45, 0.15),

    /** Bez wyraźnego szczytu — urodziny, impreza firmowa, granie w tle. */
    EVEN(0.25, 0.25, 0.25, 0.25);

    /** Sumę udziałów sprawdzamy z zapasem na błąd zaokrąglenia typu double. */
    private static final double SHARE_TOLERANCE = 0.001;

    private final List<Phase> phases;

    SetCurve(double warmup, double middle, double peak, double closing) {

        double total = warmup + middle + peak + closing;
        if (Math.abs(total - 1.0) > SHARE_TOLERANCE) {
            throw new IllegalStateException(
                "Udziały faz profilu %s nie sumują się do 1.0: %s".formatted(name(), total));
        }
        this.phases = List.of(
            new Phase(DjSlot.WARMUP, warmup),
            new Phase(DjSlot.MIDDLE, middle),
            new Phase(DjSlot.PEAK, peak),
            new Phase(DjSlot.CLOSING, closing));
    }

    public List<Phase> phases() {
        return phases;
    }

    /** Rozpoznaje nazwę profilu bez względu na wielkość liter; puste = brak wartości. */
    public static Optional<SetCurve> parse(String value) {

        if (Objects.isNull(value) || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(curve -> curve.name().equals(normalized))
            .findFirst();
    }

    /** Faza wieczoru wraz z udziałem w całkowitym czasie setu. */
    public record Phase(DjSlot slot, double share) {

    }
}
