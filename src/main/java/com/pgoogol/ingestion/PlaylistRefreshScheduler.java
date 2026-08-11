package com.pgoogol.ingestion;

import com.pgoogol.enrichment.spotify.SpotifyAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cykliczne odświeżanie własnych playlist ze Spotify (M4.7, D35): to samo, co
 * przycisk „Importuj moje playlisty" (tryb C, M2.2), tylko bez klikania —
 * przy starcie aplikacji i potem co {@code ingestion.playlist-refresh.interval}.
 *
 * <p><b>Przebiegi się nie nakładają</b>, bo {@code fixedDelay} liczy przerwę od
 * <i>zakończenia</i> poprzedniego, a nie od jego startu. To nie jest szczegół:
 * import kilkudziesięciu playlist bywa dłuższy niż domyślne pięć minut, więc
 * przy {@code fixedRate} kolejne przebiegi wchodziłyby sobie na głowę
 * i mnożyły wywołania Spotify.</p>
 *
 * <p><b>Brak połączonego konta to normalny stan</b>, nie awaria (D20): świeża
 * instalacja nie ma jeszcze przeprowadzonego OAuth, a aplikacja nie może z tego
 * powodu zasypywać logów błędami co pięć minut.</p>
 */
@Component
public class PlaylistRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(PlaylistRefreshScheduler.class);

    private final SpotifyAccountService accountService;
    private final MyPlaylistsIngestionService ingestionService;
    private final PlaylistRefreshProperties properties;
    private final AtomicReference<PlaylistRefreshStatus> lastRun;

    public PlaylistRefreshScheduler(SpotifyAccountService accountService,
                                    MyPlaylistsIngestionService ingestionService,
                                    PlaylistRefreshProperties properties) {

        this.accountService = accountService;
        this.ingestionService = ingestionService;
        this.properties = properties;
        this.lastRun = new AtomicReference<>(
            properties.enabled() ? PlaylistRefreshStatus.neverRun() : PlaylistRefreshStatus.disabled());
    }

    /**
     * Wartości muszą tu być placeholderami, bo {@code @Scheduled} czyta
     * środowisko, a nie {@link PlaylistRefreshProperties}. Zadanie rejestruje się
     * także przy wyłączonym odświeżaniu — sprawdzenie flagi na wejściu jest
     * tańsze niż warunkowy bean, którego i tak trzeba by szukać przez
     * {@code ObjectProvider} w kontrolerze.
     */
    @Scheduled(
        initialDelayString = "${ingestion.playlist-refresh.initial-delay}",
        fixedDelayString = "${ingestion.playlist-refresh.interval}")
    public void refresh() {

        if (!properties.enabled()) {
            return;
        }
        if (accountService.connectedUserId().isEmpty()) {
            lastRun.set(PlaylistRefreshStatus.skipped(Instant.now()));
            log.debug("Odświeżanie playlist pominięte — konto Spotify nie jest połączone");
            return;
        }
        runRefresh();
    }

    public PlaylistRefreshStatus status() {
        return lastRun.get();
    }

    /**
     * Wyjątek nie może wyjść z zadania: przebieg leci w tle bez nikogo, kto by
     * go obejrzał, a Spotify potrafi nie odpowiedzieć z powodów, które miną same.
     * Powód ląduje w statusie, żeby UI mógł powiedzieć, że dane są nieświeże.
     */
    private void runRefresh() {

        Instant startedAt = Instant.now();
        try {
            MyPlaylistsIngestReport report = ingestionService.ingestMyPlaylists();
            lastRun.set(PlaylistRefreshStatus.refreshed(
                startedAt, report.imported().size(), report.failed().size()));
            log.info("Automatyczne odświeżanie playlist: {} odświeżonych, {} nieudanych "
                    + "(następne za {})",
                report.imported().size(), report.failed().size(), properties.interval());
        } catch (RuntimeException ex) {
            lastRun.set(PlaylistRefreshStatus.failed(startedAt, reason(ex)));
            log.warn("Automatyczne odświeżanie playlist nie powiodło się — ponowię za {}",
                properties.interval(), ex);
        }
    }

    private String reason(RuntimeException ex) {
        return Objects.toString(ex.getMessage(), ex.getClass().getSimpleName());
    }
}
