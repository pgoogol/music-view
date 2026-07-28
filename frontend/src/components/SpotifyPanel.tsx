import { useCallback, useEffect, useState } from 'react'
import { api, ApiError, type IngestPlaylistResponse, type SpotifyAccountResponse } from '../api'

interface Props {
  onImported: () => void
}

export default function SpotifyPanel({ onImported }: Props) {
  const [account, setAccount] = useState<SpotifyAccountResponse | null>(null)
  const [reports, setReports] = useState<IngestPlaylistResponse[] | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(() => {
    api.spotifyAccount().then(setAccount).catch(() => setAccount(null))
  }, [])

  useEffect(refresh, [refresh])

  const importMine = async () => {
    setBusy(true)
    setError(null)
    try {
      setReports(await api.ingestMyPlaylists())
      onImported()
    } catch (ex) {
      setError(ex instanceof ApiError ? `${ex.errorCode}: ${ex.message}` : String(ex))
    } finally {
      setBusy(false)
    }
  }

  const importedTracks = (reports ?? []).reduce((sum, report) => sum + report.imported, 0)

  return (
    <section className="panel" aria-label="Konto Spotify">
      <h2>Konto Spotify</h2>

      {account?.connected ? (
        <p className="report" data-testid="spotify-status">
          połączone jako <strong>{account.displayName ?? account.spotifyUserId}</strong>
        </p>
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
        <button
          onClick={importMine}
          disabled={busy || !account?.connected}
          data-testid="import-my-playlists"
        >
          {busy ? 'Importuję…' : 'Importuj moje playlisty'}
        </button>
        <button className="link" onClick={refresh}>
          odśwież stan
        </button>
      </div>

      {reports && (
        <p className="report" data-testid="my-playlists-report">
          playlisty: <strong>{reports.length}</strong>, nowe utwory w bibliotece:{' '}
          <strong>{importedTracks}</strong>
        </p>
      )}
      {error && <p className="error">{error}</p>}
    </section>
  )
}
