package com.pgoogol.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * Automatyczne odświeżanie playlist ze Spotify (M4.7, D35).
 *
 * <p>{@code interval} i {@code initialDelay} muszą być powtórzone jako
 * placeholdery w adnotacji {@code @Scheduled} — czyta ona środowisko, nie ten
 * bean. Rekord zostaje mimo to, bo nazwy właściwości mają jedno miejsce
 * z opisem, a {@code interval} jedzie do UI, żeby DJ wiedział, co ile aplikacja
 * sama sięga po zmiany.</p>
 */
@ConfigurationProperties(prefix = "ingestion.playlist-refresh")
public record PlaylistRefreshProperties(boolean enabled, Duration interval, Duration initialDelay) {

    public PlaylistRefreshProperties {
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(initialDelay, "initialDelay");
    }
}
