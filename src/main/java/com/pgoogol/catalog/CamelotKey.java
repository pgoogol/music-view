package com.pgoogol.catalog;

import org.springframework.lang.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pozycja na kole Camelot — 1A–12A (moll) i 1B–12B (dur). Wartość jest
 * <b>wyliczana z {@code track_catalog.musical_key}, nigdy nie zapisywana</b>
 * (D25), tak samo jak slot wieczoru (D9): mapowanie tonacja ↔ Camelot jest
 * bijekcją na 24 wartościach, więc kolumna nie niosłaby żadnej informacji
 * ponad tę, którą już mamy.
 *
 * <p>Liczenie z {@code musical_key}, a nie z {@code manual_metrics.camelot},
 * daje pokrycie wszędzie tam, gdzie w ogóle znamy tonację — także dla utworów
 * z dumpa AcousticBrainz (D7), których nie ma w pliku z metrykami (D24).</p>
 *
 * <p>Zgodność harmoniczna (D25) to klasyczny zestaw czterech pozycji: ta sama
 * tonacja, sąsiedzi na kole (±1) i tonacja równoległa (ta sama liczba, druga
 * litera). Skok energetyczny „+2" świadomie pomijamy — to chwyt na konkretny
 * moment wieczoru, nie reguła do wpisania w filtr.</p>
 */
public record CamelotKey(int number, boolean minor) {

    private static final int POSITIONS = 12;

    private static final Pattern LABEL = Pattern.compile("^\\s*(\\d{1,2})\\s*([ABab])\\s*$");

    /**
     * Nazwa dźwięku, opcjonalny znak chromatyczny, reszta = tryb. Bemol łapiemy
     * w obu wielkościach liter („Bb" i „BB"), bo filtr porównuje zapisy podane
     * wielkimi literami — po zjedzeniu nazwy dźwięku przez pierwszą grupę każde
     * kolejne „b"/„B" może być już tylko bemolem.
     */
    private static final Pattern MUSICAL_KEY = Pattern.compile("^([A-Ga-g])\\s*([#bB])?\\s*(.*)$");

    /**
     * Numer na kole dla tonacji molowych, indeksowany klasą wysokości dźwięku
     * (0 = C). Obie strony koła to kwintowe okręgi — kolejna pozycja leży
     * o 7 półtonów wyżej; strona durowa jest przesunięta o tercję małą
     * (tonacja równoległa: 8A = a-moll, 8B = C-dur).
     */
    private static final Map<Integer, Integer> MINOR_BY_PITCH = Map.ofEntries(
        Map.entry(8, 1), Map.entry(3, 2), Map.entry(10, 3), Map.entry(5, 4),
        Map.entry(0, 5), Map.entry(7, 6), Map.entry(2, 7), Map.entry(9, 8),
        Map.entry(4, 9), Map.entry(11, 10), Map.entry(6, 11), Map.entry(1, 12));

    private static final Map<Integer, Integer> MAJOR_BY_PITCH = Map.ofEntries(
        Map.entry(11, 1), Map.entry(6, 2), Map.entry(1, 3), Map.entry(8, 4),
        Map.entry(3, 5), Map.entry(10, 6), Map.entry(5, 7), Map.entry(0, 8),
        Map.entry(7, 9), Map.entry(2, 10), Map.entry(9, 11), Map.entry(4, 12));

    /** Zapisy enharmoniczne czytamy wszystkie — plik od DJ-a bywa niesforny (D25). */
    private static final Map<String, Integer> PITCH_BY_ROOT = Map.ofEntries(
        Map.entry("C", 0), Map.entry("B#", 0),
        Map.entry("C#", 1), Map.entry("DB", 1),
        Map.entry("D", 2),
        Map.entry("D#", 3), Map.entry("EB", 3),
        Map.entry("E", 4), Map.entry("FB", 4),
        Map.entry("F", 5), Map.entry("E#", 5),
        Map.entry("F#", 6), Map.entry("GB", 6),
        Map.entry("G", 7),
        Map.entry("G#", 8), Map.entry("AB", 8),
        Map.entry("A", 9),
        Map.entry("A#", 10), Map.entry("BB", 10),
        Map.entry("B", 11), Map.entry("CB", 11));

    public CamelotKey {

        if (number < 1 || number > POSITIONS) {
            throw new IllegalArgumentException("Numer koła Camelot poza 1..12: " + number);
        }
    }

    /**
     * Tonacja w zapisie katalogu („G minor", „C major") → pozycja na kole.
     * Brak trybu czytamy jako dur — tak samo jak przy normalizacji importu (D24).
     */
    public static Optional<CamelotKey> ofMusicalKey(@Nullable String musicalKey) {

        if (Objects.isNull(musicalKey) || musicalKey.isBlank()) {
            return Optional.empty();
        }
        String normalized = musicalKey.trim().replace('♯', '#').replace('♭', 'b');
        Matcher matcher = MUSICAL_KEY.matcher(normalized);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String root = (matcher.group(1) + Objects.requireNonNullElse(matcher.group(2), ""))
            .toUpperCase(Locale.ROOT);
        boolean minor = matcher.group(3).toLowerCase(Locale.ROOT).startsWith("min");
        return Optional.ofNullable(PITCH_BY_ROOT.get(root))
            .map(pitch -> (minor ? MINOR_BY_PITCH : MAJOR_BY_PITCH).get(pitch))
            .map(number -> new CamelotKey(number, minor));
    }

    /** Etykieta koła („8A") — wejście filtra i wartość z pliku z metrykami (D24). */
    public static Optional<CamelotKey> ofLabel(@Nullable String label) {

        if (Objects.isNull(label) || label.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = LABEL.matcher(label);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int number = Integer.parseInt(matcher.group(1));
        if (number < 1 || number > POSITIONS) {
            return Optional.empty();
        }
        return Optional.of(new CamelotKey(number, matcher.group(2).equalsIgnoreCase("A")));
    }

    public String label() {
        return number + (minor ? "A" : "B");
    }

    /** Ta sama tonacja, sąsiedzi na kole (±1) i tonacja równoległa (D25). */
    public Set<CamelotKey> compatible() {

        return new LinkedHashSet<>(List.of(
            this,
            new CamelotKey(number % POSITIONS + 1, minor),
            new CamelotKey((number + POSITIONS - 2) % POSITIONS + 1, minor),
            new CamelotKey(number, !minor)));
    }

    public boolean isCompatibleWith(CamelotKey other) {
        return compatible().contains(Objects.requireNonNull(other, "other"));
    }

    /**
     * Wszystkie akceptowane zapisy tej tonacji, WIELKIMI LITERAMI — filtr
     * porównuje je z {@code upper(musical_key)}, bo w katalogu leży to,
     * co dało źródło („Eb minor" z jednego pliku, „D# minor" z drugiego).
     */
    public List<String> musicalKeySpellings() {

        int pitch = pitchClass();
        String mode = minor ? " MINOR" : " MAJOR";
        return PITCH_BY_ROOT.entrySet().stream()
            .filter(entry -> entry.getValue() == pitch)
            .map(Map.Entry::getKey)
            .sorted()
            .map(root -> root + mode)
            .toList();
    }

    private int pitchClass() {

        Map<Integer, Integer> wheel = minor ? MINOR_BY_PITCH : MAJOR_BY_PITCH;
        return wheel.entrySet().stream()
            .filter(entry -> entry.getValue() == number)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow();
    }
}
