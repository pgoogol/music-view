// Import playlisty po linku (M2.1, tryby B/D). W M3.2 zniknął z UI import
// z pliku CSV — biblioteka jedzie ze Spotify, a endpoint `/api/ingest/file`
// zostaje w API jako awaryjne wejście (tryb A z D6).

import { useState } from 'react'
import { api, type IngestPlaylistResponse } from '../api'
import { useToast } from './Toasts'

interface Props {
  onImported: () => void
}

export default function ImportPanel({ onImported }: Props) {

  const { notify, reportError } = useToast()
  const [playlistUrl, setPlaylistUrl] = useState('')
  const [busy, setBusy] = useState(false)
  const [playlistReport, setPlaylistReport] = useState<IngestPlaylistResponse | null>(null)

  const importPlaylist = async () => {
    if (!playlistUrl.trim()) {
      notify('Wklej link do playlisty Spotify', 'error')
      return
    }
    setBusy(true)
    try {
      const report = await api.ingestPlaylist(playlistUrl.trim())
      setPlaylistReport(report)
      notify(`„${report.name}": ${report.imported} nowych utworów w bibliotece`)
      onImported()
    } catch (error) {
      reportError(error, 'Import playlisty nie powiódł się')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel" aria-label="Import">
      <h2>Import playlisty po linku</h2>
      <p className="muted">
        Dowolna playlista Spotify — także cudza. Utwory trafiają do katalogu i biblioteki,
        a sama playlista pojawia się w zakładce Playlisty.
      </p>

      <div className="row">
        <input
          type="url"
          placeholder="https://open.spotify.com/playlist/…"
          value={playlistUrl}
          onChange={(event) => setPlaylistUrl(event.target.value)}
          data-testid="playlist-url"
        />
        <button onClick={importPlaylist} disabled={busy} data-testid="playlist-import">
          {busy ? 'Importuję…' : 'Importuj playlistę'}
        </button>
      </div>

      {playlistReport && (
        <p className="report" data-testid="playlist-report">
          „{playlistReport.name}": <strong>{playlistReport.tracks}</strong> utworów, nowe w
          bibliotece: <strong>{playlistReport.imported}</strong>, pominięte:{' '}
          <strong>{playlistReport.skipped.length}</strong>
        </p>
      )}
    </section>
  )
}
