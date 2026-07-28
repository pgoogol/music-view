// Konto Spotify (M2.2) — w M3.1 panel pokazuje pełny stan połączenia
// (kto, do kiedy ważny token, jakie zakresy) i rozpisuje import własnych playlist.

import { useCallback, useEffect, useState } from 'react'
import { api, type IngestPlaylistResponse, type SpotifyAccountResponse } from '../api'
import { useToast } from './Toasts'
import { formatDateTime } from '../format'

interface Props {
  onImported: () => void
}

export default function SpotifyPanel({ onImported }: Props) {

  const { notify, reportError } = useToast()
  const [account, setAccount] = useState<SpotifyAccountResponse | null>(null)
  const [reports, setReports] = useState<IngestPlaylistResponse[] | null>(null)
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
      const tracks = imported.reduce((sum, report) => sum + report.imported, 0)
      notify(`Zaimportowano ${imported.length} playlist, ${tracks} nowych utworów`)
      onImported()
    } catch (error) {
      reportError(error, 'Import własnych playlist nie powiódł się')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel" aria-label="Konto Spotify">
      <h2>Konto Spotify</h2>

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

      {reports && (
        <ul className="report" data-testid="my-playlists-report">
          {reports.map((report) => (
            <li key={report.spotifyPlaylistId}>
              „{report.name}" — {report.tracks} utworów, nowych: <strong>{report.imported}</strong>
              {report.skipped.length > 0 && (
                <span className="muted"> , pominięte: {report.skipped.length}</span>
              )}
            </li>
          ))}
          {reports.length === 0 && <li className="muted">Konto nie ma playlist do zaimportowania.</li>}
        </ul>
      )}
    </section>
  )
}
