// Ręczne wgrywanie metryk utworów z CSV (D24) — obejście na czas, gdy Spotify
// nie oddaje już audio-features. Panel celowo mówi wprost, że plik tylko
// uzupełnia utwory, które są już w katalogu: biblioteka jedzie ze Spotify.

import { useRef, useState } from 'react'
import { api, type IngestMetricsResponse } from '../api'
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
    const file = fileInput.current?.files?.[0]
    if (!file) {
      notify('Wybierz plik CSV z metrykami', 'error')
      return
    }
    setBusy(true)
    try {
      const uploaded = await api.ingestMetrics(file)
      setReport(uploaded)
      notify(`Uzupełniono metryki dla ${uploaded.applied} utworów`)
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
        Ponowny import nadpisuje metryki.
      </p>

      <div className="row">
        <input ref={fileInput} type="file" accept=".csv,text/csv" data-testid="metrics-input" />
        <button onClick={upload} disabled={busy} data-testid="metrics-upload">
          {busy ? 'Wgrywam…' : 'Wgraj metryki'}
        </button>
      </div>

      {report && (
        <div className="report" data-testid="metrics-report">
          <p>
            uzupełnione utwory: <strong>{report.applied}</strong>, dopasowane po ISRC:{' '}
            <strong>{report.matchedByIsrc}</strong>, spoza katalogu:{' '}
            <strong>{report.skipped.length}</strong>, odrzucone wiersze:{' '}
            <strong>{report.failed.length}</strong>
          </p>
          <ProblemRows rows={[...report.skipped, ...report.failed]} />
        </div>
      )}
    </section>
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
