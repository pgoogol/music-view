import { useRef, useState } from 'react'
import { api, ApiError, type IngestFileResponse, type IngestPlaylistResponse } from '../api'

interface Props {
  onImported: () => void
}

export default function ImportPanel({ onImported }: Props) {
  const fileInput = useRef<HTMLInputElement>(null)
  const [playlistUrl, setPlaylistUrl] = useState('')
  const [busy, setBusy] = useState(false)
  const [report, setReport] = useState<IngestFileResponse | null>(null)
  const [playlistReport, setPlaylistReport] = useState<IngestPlaylistResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  const run = async <T,>(action: () => Promise<T>, onDone: (result: T) => void) => {
    setBusy(true)
    setError(null)
    try {
      onDone(await action())
      onImported()
    } catch (ex) {
      setError(ex instanceof ApiError ? `${ex.errorCode}: ${ex.message}` : String(ex))
    } finally {
      setBusy(false)
    }
  }

  const upload = () => {
    const file = fileInput.current?.files?.[0]
    if (!file) {
      setError('Wybierz plik CSV (eksport Exportify lub własny)')
      return
    }
    setPlaylistReport(null)
    return run(() => api.ingestFile(file), setReport)
  }

  const importPlaylist = () => {
    if (!playlistUrl.trim()) {
      setError('Wklej link do playlisty Spotify')
      return
    }
    setReport(null)
    return run(() => api.ingestPlaylist(playlistUrl), setPlaylistReport)
  }

  return (
    <section className="panel" aria-label="Import">
      <h2>Import</h2>
      <div className="row">
        <input ref={fileInput} type="file" accept=".csv,text/csv" data-testid="csv-input" />
        <button onClick={upload} disabled={busy} data-testid="csv-upload">
          {busy ? 'Importuję…' : 'Importuj CSV'}
        </button>
      </div>
      <div className="row">
        <input
          type="url"
          placeholder="https://open.spotify.com/playlist/…"
          value={playlistUrl}
          onChange={(e) => setPlaylistUrl(e.target.value)}
          data-testid="playlist-url"
        />
        <button onClick={importPlaylist} disabled={busy} data-testid="playlist-import">
          Importuj playlistę
        </button>
      </div>
      {report && (
        <p className="report" data-testid="import-report">
          zaimportowane: <strong>{report.imported}</strong>, już w bibliotece:{' '}
          <strong>{report.alreadyExisted}</strong>, odrzucone: <strong>{report.failed.length}</strong>
          {report.failed.length > 0 && (
            <span className="muted">
              {' '}
              ({report.failed.map((f) => `w. ${f.line}: ${f.reason}`).join('; ')})
            </span>
          )}
        </p>
      )}
      {playlistReport && (
        <p className="report" data-testid="playlist-report">
          „{playlistReport.name}": <strong>{playlistReport.tracks}</strong> utworów, nowe w
          bibliotece: <strong>{playlistReport.imported}</strong>, pominięte:{' '}
          <strong>{playlistReport.skipped.length}</strong>
        </p>
      )}
      {error && <p className="error">{error}</p>}
    </section>
  )
}
