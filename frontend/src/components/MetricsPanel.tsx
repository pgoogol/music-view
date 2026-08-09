// Ręczne wgrywanie metryk utworów z CSV (D24) — obejście na czas, gdy Spotify
// nie oddaje już audio-features. Panel celowo mówi wprost, że plik tylko
// uzupełnia utwory, które są już w katalogu: biblioteka jedzie ze Spotify.
// Eksport analizatora idzie per playlista, więc plików wybiera się kilka naraz
// — każdy ma własny wiersz raportu, bo numer wiersza bez nazwy pliku nic nie mówi.

import { useRef, useState } from 'react'
import { api, type IngestMetricsResponse, type MetricsFileReportResponse } from '../api'
import { useToast } from './Toasts'

interface Props {
  onImported: () => void
}

const MAX_LISTED_ROWS = 10

export default function MetricsPanel({ onImported }: Props) {

  const { notify, reportError } = useToast()
  const fileInput = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [report, setReport] = useState<IngestMetricsResponse | null>(null)

  const upload = async () => {
    const files = Array.from(fileInput.current?.files ?? [])
    if (files.length === 0) {
      notify('Wybierz plik CSV z metrykami', 'error')
      return
    }
    setBusy(true)
    try {
      const uploaded = await api.ingestMetrics(files)
      setReport(uploaded)
      const broken = uploaded.files.filter((file) => file.error)
      if (broken.length === uploaded.files.length) {
        notify(broken[0].error ?? 'Nie udało się wczytać żadnego pliku', 'error')
      } else {
        notify(`Uzupełniono metryki dla ${uploaded.applied} utworów`)
      }
      onImported()
    } catch (error) {
      reportError(error, 'Import metryk nie powiódł się')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel" aria-label="Metryki">
      <h2>Metryki utworów (CSV)</h2>
      <p className="muted">
        BPM, tonacja, Camelot i cechy audio wgrywane ręcznie. Wiersze dopasowywane po
        Spotify Track Id, a gdy go brak — po ISRC; utwory spoza katalogu są pomijane.
        Można wskazać kilka plików naraz; ponowny import nadpisuje metryki.
      </p>

      <div className="row">
        <input
          ref={fileInput}
          type="file"
          accept=".csv,text/csv"
          multiple
          data-testid="metrics-input"
        />
        <button onClick={upload} disabled={busy} data-testid="metrics-upload">
          {busy ? 'Wgrywam…' : 'Wgraj metryki'}
        </button>
      </div>

      {report && (
        <div className="report" data-testid="metrics-report">
          <p>
            uzupełnione utwory: <strong>{report.applied}</strong>, dopasowane po ISRC:{' '}
            <strong>{report.matchedByIsrc}</strong>, spoza katalogu:{' '}
            <strong>{report.skippedRows}</strong>, odrzucone wiersze:{' '}
            <strong>{report.failedRows}</strong>
          </p>
          <ul className="modal-list">
            {report.files.map((file) => (
              <li key={file.file}>
                <FileReport file={file} />
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}

function FileReport({ file }: { file: MetricsFileReportResponse }) {

  if (file.error) {
    return (
      <>
        <strong>{file.file}</strong> — <span className="error">plik odrzucony: {file.error}</span>
      </>
    )
  }
  return (
    <>
      <strong>{file.file}</strong> — uzupełnione: {file.applied}
      {file.skipped.length > 0 && <span className="muted">, spoza katalogu: {file.skipped.length}</span>}
      {file.failed.length > 0 && <span className="muted">, odrzucone: {file.failed.length}</span>}
      <ProblemRows rows={[...file.skipped, ...file.failed]} />
    </>
  )
}

function ProblemRows({ rows }: { rows: { line: number; reason: string }[] }) {

  if (rows.length === 0) {
    return null
  }
  return (
    <ul className="muted error-list">
      {rows.slice(0, MAX_LISTED_ROWS).map((row) => (
        <li key={`${row.line}-${row.reason}`}>
          wiersz {row.line}: {row.reason}
        </li>
      ))}
      {rows.length > MAX_LISTED_ROWS && <li>…i {rows.length - MAX_LISTED_ROWS} dalszych</li>}
    </ul>
  )
}
