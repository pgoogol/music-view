// Konto Spotify (M2.2) — panel pokazuje pełny stan połączenia (kto, do kiedy
// ważny token, jakie zakresy) i importuje własne playlisty konta (tryb C).
// W M3.2 import kończy się modalem z raportem: leci długo (playlista po
// playliście), więc podsumowanie nie może zniknąć razem z toastem.

import { useCallback, useEffect, useState } from 'react'
import { api, type IngestMyPlaylistsResponse, type SpotifyAccountResponse } from '../api'
import Modal from './Modal'
import { useToast } from './Toasts'
import { formatDateTime } from '../format'

interface Props {
  onImported: () => void
}

export default function SpotifyPanel({ onImported }: Props) {

  const { notify, reportError } = useToast()
  const [account, setAccount] = useState<SpotifyAccountResponse | null>(null)
  const [reports, setReports] = useState<IngestMyPlaylistsResponse | null>(null)
  const [busy, setBusy] = useState(false)

  const refresh = useCallback(() => {
    api.spotifyAccount().then(setAccount).catch(() => setAccount(null))
  }, [])

  useEffect(refresh, [refresh])

  const importMine = async () => {
    setBusy(true)
    try {
      const imported = await api.ingestMyPlaylists()
      setReports(imported)
      onImported()
    } catch (error) {
      reportError(error, 'Import własnych playlist nie powiódł się')
    } finally {
      setBusy(false)
    }
  }

  const closeReport = () => {
    const finished = reports
    setReports(null)
    if (finished && finished.imported.length > 0) {
      notify(`Zaimportowano ${finished.imported.length} playlist`)
    }
  }

  const imported = reports?.imported ?? []
  const failed = reports?.failed ?? []
  const totalTracks = imported.reduce((sum, report) => sum + report.imported, 0)
  const totalSkipped = imported.reduce((sum, report) => sum + report.skipped.length, 0)

  return (
    <section className="panel" aria-label="Konto Spotify">
      <h2>Moje playlisty ze Spotify</h2>

      {account?.connected ? (
        <div className="report" data-testid="spotify-status">
          <p>
            połączone jako <strong>{account.displayName ?? account.spotifyUserId}</strong>
          </p>
          <dl className="track-facts">
            <dt>Token ważny do</dt>
            <dd>{formatDateTime(account.expiresAt)}</dd>
            <dt>Połączono</dt>
            <dd>{formatDateTime(account.connectedAt)}</dd>
            <dt>Zakresy</dt>
            <dd className="muted">{account.scopes ?? '—'}</dd>
          </dl>
        </div>
      ) : (
        <p className="muted" data-testid="spotify-status">
          konto niepołączone — potrzebne do importu własnych playlist i eksportu setów
        </p>
      )}

      <div className="row">
        {/* pełne przeładowanie, bo /login kończy się przekierowaniem na ekran zgody Spotify */}
        <a className="button-link" href="/api/auth/spotify/login" data-testid="spotify-connect">
          {account?.connected ? 'Połącz ponownie' : 'Połącz konto'}
        </a>
        <button onClick={importMine} disabled={busy || !account?.connected} data-testid="import-my-playlists">
          {busy ? 'Importuję…' : 'Importuj moje playlisty'}
        </button>
        <button className="link" onClick={refresh}>
          odśwież stan
        </button>
      </div>

      {busy && (
        <p className="muted" data-testid="import-progress">
          Zaciągam playlisty konta — przy większej bibliotece potrwa to chwilę.
        </p>
      )}

      {reports && (
        <Modal title="Import playlist zakończony" onClose={closeReport} testId="my-playlists-modal">
          {imported.length === 0 && failed.length === 0 ? (
            <p className="muted">Konto nie ma playlist do zaimportowania.</p>
          ) : (
            <>
              <p>
                Playlist: <strong>{imported.length}</strong>, nowych utworów w bibliotece:{' '}
                <strong>{totalTracks}</strong>
                {totalSkipped > 0 && (
                  <>
                    , pominiętych pozycji: <strong>{totalSkipped}</strong>
                  </>
                )}
              </p>
              <ul className="modal-list" data-testid="my-playlists-report">
                {imported.map((report) => (
                  <li key={report.spotifyPlaylistId}>
                    „{report.name}" — {report.tracks} utworów, nowych:{' '}
                    <strong>{report.imported}</strong>
                    {report.skipped.length > 0 && (
                      <span className="muted">, pominięte: {report.skipped.length}</span>
                    )}
                  </li>
                ))}
              </ul>
              {failed.length > 0 && (
                <>
                  {/* import leci dalej mimo awarii — tutaj widać, co powtórzyć po linku */}
                  <p className="error">
                    Nieudane playlisty: <strong>{failed.length}</strong>
                  </p>
                  <ul className="modal-list" data-testid="my-playlists-failed">
                    {failed.map((playlist) => (
                      <li key={playlist.spotifyPlaylistId}>
                        „{playlist.name}" — {playlist.reason}{' '}
                        <span className="muted">({playlist.errorCode})</span>
                      </li>
                    ))}
                  </ul>
                </>
              )}
            </>
          )}
        </Modal>
      )}
    </section>
  )
}
