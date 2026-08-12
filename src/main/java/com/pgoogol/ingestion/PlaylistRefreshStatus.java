package com.pgoogol.ingestion;

import org.springframework.lang.Nullable;

import java.time.Instant;

/**
 * Wynik ostatniego automatycznego odświeżenia playlist (M4.7, D35). Trzymany
 * w pamięci: to informacja o bieżącym uruchomieniu aplikacji, a nie dane,
 * które warto przeżywać restart — po restarcie i tak zaraz leci odświeżenie.
 *
 * @param outcome dlaczego stan wygląda, jak wygląda — pominięcie z braku
 *                połączonego konta to normalny przebieg, nie awaria
 */
public record PlaylistRefreshStatus(
    Outcome outcome,
    @Nullable Instant lastRunAt,
    int refreshedPlaylists,
    int failedPlaylists,
    @Nullable String message) {

    public enum Outcome {

        /** Aplikacja wstała, pierwsze odświeżenie jeszcze nie poszło. */
        NEVER_RUN,

        /** Odświeżanie wyłączone w konfiguracji. */
        DISABLED,

        /** Nie ma po co pytać Spotify — konto nie jest połączone (D20). */
        SKIPPED_NOT_CONNECTED,

        /** Przebieg się odbył; {@code failedPlaylists} mówi, ile playlist padło (D31). */
        REFRESHED,

        /** Przebieg wywrócił się w całości — np. Spotify nie odpowiada. */
        FAILED
    }

    public static PlaylistRefreshStatus neverRun() {
        return new PlaylistRefreshStatus(Outcome.NEVER_RUN, null, 0, 0, null);
    }

    public static PlaylistRefreshStatus disabled() {
        return new PlaylistRefreshStatus(Outcome.DISABLED, null, 0, 0, null);
    }

    public static PlaylistRefreshStatus skipped(Instant at) {
        return new PlaylistRefreshStatus(Outcome.SKIPPED_NOT_CONNECTED, at, 0, 0,
            "Konto Spotify nie jest połączone");
    }

    public static PlaylistRefreshStatus refreshed(Instant at, int refreshed, int failed) {
        return new PlaylistRefreshStatus(Outcome.REFRESHED, at, refreshed, failed, null);
    }

    public static PlaylistRefreshStatus failed(Instant at, String message) {
        return new PlaylistRefreshStatus(Outcome.FAILED, at, 0, 0, message);
    }
}
