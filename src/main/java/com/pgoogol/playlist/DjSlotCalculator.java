package com.pgoogol.playlist;

import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.TrackCatalog;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Slot wieczoru liczony z bpm + energy + genre_family (D9) — w warstwie
 * serwisowej, nigdy w katalogu: ta sama piosenka gra inaczej na weselu
 * i na potańcówce, a ostatnie słowo ma i tak override DJ-a.
 *
 * <p>Kaskada (pierwszy pasujący warunek wygrywa):</p>
 * <ol>
 *   <li>bpm &lt; 75 → {@code BREAK} (ballada na oddech),</li>
 *   <li>energia „low" albo bpm &lt; 95 → {@code WARMUP},</li>
 *   <li>energia „high" → {@code PEAK} przy bpm ≥ 120 (dla gatunków parkietowych
 *       już od 110), w przeciwnym razie {@code CLOSING},</li>
 *   <li>reszta → {@code MIDDLE}.</li>
 * </ol>
 *
 * <p>Brak bpm i energii = brak slotu (utwór przed wzbogaceniem); brak jednego
 * z nich nie przeszkadza — kaskada korzysta z tego, co jest. BPM jest już po
 * korekcie half-time (M1.4), więc salsa trafia tu z realnym ~180, nie 90.</p>
 */
@Component
public class DjSlotCalculator {

    private static final int BREAK_MAX_BPM = 75;
    private static final int WARMUP_MAX_BPM = 95;
    private static final int PEAK_MIN_BPM = 120;
    private static final int DANCE_FLOOR_PEAK_MIN_BPM = 110;
    private static final String LOW = "low";
    private static final String HIGH = "high";
    private static final Set<GenreFamily> DANCE_FLOOR_GENRES = Set.of(
        GenreFamily.LATIN, GenreFamily.DISCO, GenreFamily.DISCO_POLO, GenreFamily.ELECTRONIC);

    public Optional<DjSlot> calculate(TrackCatalog track) {

        Objects.requireNonNull(track, "track");
        return calculate(track.getBpm(), track.getEnergy(), track.getGenreFamily());
    }

    public Optional<DjSlot> calculate(Integer bpm, String energy, GenreFamily genreFamily) {

        String level = normalized(energy);
        if (Objects.isNull(bpm) && Objects.isNull(level)) {
            return Optional.empty();
        }
        if (Objects.nonNull(bpm) && bpm < BREAK_MAX_BPM) {
            return Optional.of(DjSlot.BREAK);
        }
        if (Objects.equals(level, LOW) || (Objects.nonNull(bpm) && bpm < WARMUP_MAX_BPM)) {
            return Optional.of(DjSlot.WARMUP);
        }
        if (Objects.equals(level, HIGH)) {
            return Optional.of(isPeak(bpm, genreFamily) ? DjSlot.PEAK : DjSlot.CLOSING);
        }
        return Optional.of(DjSlot.MIDDLE);
    }

    private boolean isPeak(Integer bpm, GenreFamily genreFamily) {

        if (Objects.isNull(bpm)) {
            return true;
        }
        return bpm >= PEAK_MIN_BPM
            || (DANCE_FLOOR_GENRES.contains(genreFamily) && bpm >= DANCE_FLOOR_PEAK_MIN_BPM);
    }

    private String normalized(String energy) {

        return Objects.isNull(energy) || energy.isBlank()
            ? null
            : energy.trim().toLowerCase(Locale.ROOT);
    }
}
