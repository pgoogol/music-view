package com.pgoogol.ingestion;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tłumaczy pojedyncze komórki CSV z metrykami (D24) na wartości domenowe.
 * Eksporty różnią się skalą i zapisem, więc parser jest wyrozumiały: przecinek
 * dziesiętny, procenty i jednostki lecą do kosza, a wartość spoza sensownego
 * zakresu jest traktowana jak brak danych — lepiej puste pole niż śmieć w bazie.
 */
@Component
public class MetricValueParser {

    private static final int SCORE_SCALE = 3;
    private static final BigDecimal PERCENT = new BigDecimal("100");
    private static final BigDecimal MIN_BPM = new BigDecimal("20");
    private static final BigDecimal MAX_BPM = new BigDecimal("400");
    private static final BigDecimal MIN_LOUDNESS = new BigDecimal("-60");
    private static final BigDecimal MAX_LOUDNESS = new BigDecimal("10");
    private static final int MIN_TIME_SIGNATURE = 1;
    private static final int MAX_TIME_SIGNATURE = 16;

    private static final Pattern NUMBER_NOISE = Pattern.compile("[^0-9,.\\-]");
    private static final Pattern NUMERIC = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern CAMELOT = Pattern.compile("(1[0-2]|[1-9])\\s*([ABab])");
    private static final Pattern MINOR = Pattern.compile("\\b(minor|moll|min)\\b");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Cecha w skali 0..1. Eksporty podają je raz jako ułamek, raz w procentach —
     * wartość powyżej 1 traktujemy jako procent (89 → 0.890).
     */
    public Optional<BigDecimal> score(@Nullable String raw) {

        return number(raw)
            .map(value -> value.compareTo(BigDecimal.ONE) > 0
                ? value.divide(PERCENT, SCORE_SCALE, RoundingMode.HALF_UP)
                : value.setScale(SCORE_SCALE, RoundingMode.HALF_UP))
            .filter(value -> value.signum() >= 0 && value.compareTo(BigDecimal.ONE) <= 0);
    }

    public Optional<BigDecimal> bpm(@Nullable String raw) {

        return number(raw)
            .filter(value -> value.compareTo(MIN_BPM) >= 0 && value.compareTo(MAX_BPM) <= 0)
            .map(value -> value.setScale(2, RoundingMode.HALF_UP));
    }

    public Optional<BigDecimal> loudness(@Nullable String raw) {

        return number(raw)
            .filter(value -> value.compareTo(MIN_LOUDNESS) >= 0 && value.compareTo(MAX_LOUDNESS) <= 0)
            .map(value -> value.setScale(2, RoundingMode.HALF_UP));
    }

    public Optional<Integer> timeSignature(@Nullable String raw) {

        return number(raw)
            .map(BigDecimal::intValue)
            .filter(value -> value >= MIN_TIME_SIGNATURE && value <= MAX_TIME_SIGNATURE);
    }

    /** Koło kwintowe Camelot (np. „6A") — klucz do miksowania harmonicznego. */
    public Optional<String> camelot(@Nullable String raw) {

        if (isBlank(raw)) {
            return Optional.empty();
        }
        Matcher matcher = CAMELOT.matcher(raw.trim());
        return matcher.matches()
            ? Optional.of(matcher.group(1) + matcher.group(2).toUpperCase(Locale.ROOT))
            : Optional.empty();
    }

    /**
     * Tonacja w zapisie AcousticBrainz („G minor", „C major"), żeby jedno pole
     * {@code musical_key} znaczyło zawsze to samo. Enharmoniczne zapisy typu
     * „F#/G♭ minor" tniemy do pierwszego (krzyżykowego), brak trybu = dur.
     */
    public Optional<String> musicalKey(@Nullable String raw) {

        if (isBlank(raw)) {
            return Optional.empty();
        }
        String normalized = WHITESPACE.matcher(raw.trim().replace('♯', '#').replace('♭', 'b'))
            .replaceAll(" ");
        boolean minor = MINOR.matcher(normalized.toLowerCase(Locale.ROOT)).find();
        String root = root(normalized);
        return root.isEmpty() || NUMERIC.matcher(root).matches()
            ? Optional.empty()
            : Optional.of("%s %s".formatted(root, minor ? "minor" : "major"));
    }

    private String root(String normalized) {

        String beforeScale = normalized.split(" ")[0];
        String firstSpelling = beforeScale.split("/")[0].trim();
        return firstSpelling.isEmpty()
            ? ""
            : firstSpelling.substring(0, 1).toUpperCase(Locale.ROOT) + firstSpelling.substring(1);
    }

    private Optional<BigDecimal> number(@Nullable String raw) {

        if (isBlank(raw)) {
            return Optional.empty();
        }
        String cleaned = NUMBER_NOISE.matcher(raw.trim()).replaceAll("").replace(',', '.');
        return NUMERIC.matcher(cleaned).matches()
            ? Optional.of(new BigDecimal(cleaned))
            : Optional.empty();
    }

    private boolean isBlank(@Nullable String value) {
        return Objects.isNull(value) || value.isBlank();
    }
}
