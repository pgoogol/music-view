// Import biblioteki (M1.2 CSV, M2.1 playlisty). W M3.1 doszedł upuszczany plik
// i raport w postaci listy — przy 2500 wierszach zdanie w jednej linii było
// nieczytelne, a odrzucone wiersze trzeba widzieć wiersz po wierszu.

import { useRef, useState } from 'react'
import { api, type IngestFileResponse, type IngestPlaylistResponse } from '../api'
import { useToast } from './Toasts'

interface Props {
  onImported: () => void
}

const MAX_LISTED_ERRORS = 10

export default function ImportPanel({ onImported }: Props) {

  const { notify, reportError } = useToast()
  const fileInput = useRef<HTMLInputElement>(null)
  const [playlistUrl, setPlaylistUrl] = useState('')
  const [busy, setBusy] = useState(false)
  const [dropActive, setDropActive] = useState(false)
  const [fileReport, setFileReport] = useState<IngestFileResponse | null>(null)
  const [playlistReport, setPlaylistReport] = useState<IngestPlaylistResponse | null>(null)

  const uploadFile = async (file: File) => {
    setBusy(true)
    setPlaylistReport(null)
    try {
      const report = await api.ingestFile(file)
      setFileReport(report)
      notify(`Zaimportowano ${report.imported} utworów z pliku ${file.name}`)
      onImported()
    } catch (error) {
      reportError(error, 'Import pliku nie powiódł się')
    } finally {
      setBusy(false)
    }
  }

  const upload = () => {
    const file = fileInput.current?.files?.[0]
    if (!file) {
      notify('Wybierz plik CSV (eksport Exportify lub własny)', 'error')
      return
    }
    return uploadFile(file)
  }

  const importPlaylist = async () => {
    if (!playlistUrl.trim()) {
      notify('Wklej link do playlisty Spotify', 'error')
      return
    }
    setBusy(true)
    setFileReport(null)
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
      <h2>Import z pliku</h2>

      <div
        className={dropActive ? 'dropzone active' : 'dropzone'}
        onDragOver={(event) => {
          event.preventDefault()
          setDropActive(true)
        }}
        onDragLeave={() => setDropActive(false)}
        onDrop={(event) => {
          event.preventDefault()
          setDropActive(false)
          const file = event.dataTransfer.files?.[0]
          if (file) uploadFile(file)
        }}
        data-testid="csv-dropzone"
      >
        Upuść tutaj plik CSV albo wybierz go poniżej
      </div>

      <div className="row">
        <input ref={fileInput} type="file" accept=".csv,text/csv" data-testid="csv-input" />
        <button onClick={upload} disabled={busy} data-testid="csv-upload">
          {busy ? 'Importuję…' : 'Importuj CSV'}
        </button>
      </div>

      {fileReport && (
        <div className="report" data-testid="import-report">
          <p>
            zaimportowane: <strong>{fileReport.imported}</strong>, już w bibliotece:{' '}
            <strong>{fileReport.alreadyExisted}</strong>, odrzucone:{' '}
            <strong>{fileReport.failed.length}</strong>
          </p>
          {fileReport.failed.length > 0 && (
            <ul className="muted error-list">
              {fileReport.failed.slice(0, MAX_LISTED_ERRORS).map((failure) => (
                <li key={failure.line}>
                  wiersz {failure.line}: {failure.reason}
                </li>
              ))}
              {fileReport.failed.length > MAX_LISTED_ERRORS && (
                <li>…i {fileReport.failed.length - MAX_LISTED_ERRORS} dalszych</li>
              )}
            </ul>
          )}
        </div>
      )}

      <h2>Import playlisty po linku</h2>
      <div className="row">
        <input
          type="url"
          placeholder="https://open.spotify.com/playlist/…"
          value={playlistUrl}
          onChange={(event) => setPlaylistUrl(event.target.value)}
          data-testid="playlist-url"
        />
        <button onClick={importPlaylist} disabled={busy} data-testid="playlist-import">
          Importuj playlistę
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
