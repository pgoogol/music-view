import { useRef, useState } from 'react'
import { api, ApiError, type IngestFileResponse } from '../api'

interface Props {
  onImported: () => void
}

export default function ImportPanel({ onImported }: Props) {
  const fileInput = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [report, setReport] = useState<IngestFileResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  const upload = async () => {
    const file = fileInput.current?.files?.[0]
    if (!file) {
      setError('Wybierz plik CSV (eksport Exportify lub własny)')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const result = await api.ingestFile(file)
      setReport(result)
      onImported()
    } catch (ex) {
      setError(ex instanceof ApiError ? `${ex.errorCode}: ${ex.message}` : String(ex))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel" aria-label="Import CSV">
      <h2>Import CSV</h2>
      <div className="row">
        <input ref={fileInput} type="file" accept=".csv,text/csv" data-testid="csv-input" />
        <button onClick={upload} disabled={busy} data-testid="csv-upload">
          {busy ? 'Importuję…' : 'Importuj'}
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
      {error && <p className="error">{error}</p>}
    </section>
  )
}
