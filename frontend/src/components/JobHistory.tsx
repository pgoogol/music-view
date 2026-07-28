// Historia jobów wzbogacania (M3.1) — wcześniej front pokazywał tylko job
// uruchomiony w bieżącej sesji, więc po odświeżeniu strony postęp „znikał".

import type { EnrichJobResponse } from '../api'
import { DASH, formatDateTime } from '../format'

interface Props {
  jobs: readonly EnrichJobResponse[]
  onRestart: (executionId: number) => void
  busy: boolean
}

export default function JobHistory({ jobs, onRestart, busy }: Props) {

  return (
    <div className="table-wrap">
      <table data-testid="job-history">
        <thead>
          <tr>
            <th>Job</th>
            <th>Status</th>
            <th>Zakres</th>
            <th>Pola</th>
            <th>Przetworzone</th>
            <th>Start</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {jobs.map((job) => (
            <tr key={job.executionId}>
              <td>#{job.executionId}</td>
              <td>
                <span className={`status-${job.status}`}>{job.status}</span>
              </td>
              <td>{job.scope}</td>
              <td>{job.fields}</td>
              <td>
                {job.writeCount} / {job.readCount || DASH}
              </td>
              <td className="muted">{formatDateTime(job.startTime)}</td>
              <td>
                {job.status === 'FAILED' && (
                  <button className="link" disabled={busy} onClick={() => onRestart(job.executionId)}>
                    restart od checkpointu
                  </button>
                )}
                {job.exitDescription && (
                  <span className="muted" title={job.exitDescription}>
                    szczegóły
                  </span>
                )}
              </td>
            </tr>
          ))}
          {jobs.length === 0 && (
            <tr>
              <td colSpan={7} className="muted empty-row">
                Żaden job jeszcze nie startował.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}
