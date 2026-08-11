package com.pgoogol.ingestion;

import com.pgoogol.common.ExternalServiceException;
import com.pgoogol.enrichment.spotify.SpotifyAccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Cykliczne odświeżanie playlist (D35). Zadanie leci w tle bez nikogo, kto by
 * je oglądał, więc najważniejsze jest to, czego <b>nie</b> robi: nie woła
 * Spotify bez połączonego konta i nie wypuszcza wyjątku, który zatrzymałby
 * harmonogram.
 */
@ExtendWith(MockitoExtension.class)
class PlaylistRefreshSchedulerTest {

    @Mock
    private SpotifyAccountService accountService;

    @Mock
    private MyPlaylistsIngestionService ingestionService;

    @Test
    @DisplayName("odświeża playlisty połączonego konta i zapamiętuje wynik")
    void refresh_whenAccountConnected_runsImportAndRecordsOutcome() {

        // given
        PlaylistRefreshScheduler scheduler = scheduler(true);
        given(accountService.connectedUserId()).willReturn(Optional.of("dj-pgoogol"));
        given(ingestionService.ingestMyPlaylists()).willReturn(report(3, 1));

        // when
        scheduler.refresh();

        // then
        PlaylistRefreshStatus status = scheduler.status();
        assertThat(status.outcome()).isEqualTo(PlaylistRefreshStatus.Outcome.REFRESHED);
        assertThat(status.refreshedPlaylists()).isEqualTo(3);
        assertThat(status.failedPlaylists()).isEqualTo(1);
        assertThat(status.lastRunAt()).isNotNull();
    }

    @Test
    @DisplayName("bez połączonego konta nie woła Spotify — to normalny stan, nie awaria")
    void refresh_whenAccountNotConnected_skipsWithoutCallingSpotify() {

        // given — świeża instalacja, OAuth jeszcze nieprzeprowadzone (D20)
        PlaylistRefreshScheduler scheduler = scheduler(true);
        given(accountService.connectedUserId()).willReturn(Optional.empty());

        // when
        scheduler.refresh();

        // then
        verify(ingestionService, never()).ingestMyPlaylists();
        assertThat(scheduler.status().outcome())
            .isEqualTo(PlaylistRefreshStatus.Outcome.SKIPPED_NOT_CONNECTED);
    }

    @Test
    @DisplayName("wyłączone odświeżanie nie sięga nawet po stan konta")
    void refresh_whenDisabled_doesNothing() {

        // given
        PlaylistRefreshScheduler scheduler = scheduler(false);

        // when
        scheduler.refresh();

        // then
        verify(ingestionService, never()).ingestMyPlaylists();
        assertThat(scheduler.status().outcome()).isEqualTo(PlaylistRefreshStatus.Outcome.DISABLED);
    }

    @Test
    @DisplayName("awaria Spotify nie wychodzi z zadania — trafia do statusu")
    void refresh_whenImportFails_recordsFailureInsteadOfPropagating() {

        // given — wyjątek z zadania cyklicznego nie ma komu wyświetlić się na ekranie
        PlaylistRefreshScheduler scheduler = scheduler(true);
        given(accountService.connectedUserId()).willReturn(Optional.of("dj-pgoogol"));
        given(ingestionService.ingestMyPlaylists())
            .willThrow(new ExternalServiceException("SPOTIFY_UNAVAILABLE", "Spotify nie odpowiada"));

        // when
        scheduler.refresh();

        // then
        PlaylistRefreshStatus status = scheduler.status();
        assertThat(status.outcome()).isEqualTo(PlaylistRefreshStatus.Outcome.FAILED);
        assertThat(status.message()).contains("Spotify nie odpowiada");
    }

    @Test
    @DisplayName("przed pierwszym przebiegiem status mówi wprost, że nic jeszcze nie poszło")
    void status_beforeFirstRun_reportsNeverRun() {

        assertThat(scheduler(true).status().outcome())
            .isEqualTo(PlaylistRefreshStatus.Outcome.NEVER_RUN);
    }

    private PlaylistRefreshScheduler scheduler(boolean enabled) {

        return new PlaylistRefreshScheduler(accountService, ingestionService,
            new PlaylistRefreshProperties(enabled, Duration.ofMinutes(5), Duration.ofSeconds(10)));
    }

    private MyPlaylistsIngestReport report(int imported, int failed) {

        List<PlaylistIngestReport> importedReports = java.util.stream.IntStream.range(0, imported)
            .mapToObj(index -> new PlaylistIngestReport(
                (long) index, "sp-%d".formatted(index), "Playlista " + index, 10, 5, 5, List.of()))
            .toList();
        List<FailedPlaylist> failedReports = java.util.stream.IntStream.range(0, failed)
            .mapToObj(index -> new FailedPlaylist(
                "sp-fail-%d".formatted(index), "Padła " + index, "SPOTIFY_ERROR", "powód"))
            .toList();
        return new MyPlaylistsIngestReport(importedReports, failedReports);
    }
}
