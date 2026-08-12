// Historia jobów wzbogacania (M3.1) — wcześniej front pokazywał tylko job
// uruchomiony w bieżącej sesji, więc po odświeżeniu strony postęp „znikał".
//
// Od M5.5 (D37) job nie przerywa się na pierwszym błędzie, tylko pomija to, co
// padło. Kolumna „nieudane" pokazuje ile, a „szczegóły" — co i dlaczego:
// wcześniej był to `title` na spanie, czyli dymek systemowy, którego nie da się
// otworzyć z klawiatury ani z dotyku, a przy dłuższym opisie i tak był ucinany.

import { useState } from 'react'
import { api, type EnrichFailureResponse, type EnrichJobResponse } from '../api'
import { DASH, formatDateTime } from '../format'
import Modal from './Modal'
import { useToast } from './Toasts'

interface Props {
  jobs: readonly EnrichJobResponse[]
  onRestart: (executionId: number) => void
  busy: boolean
}

export default function JobHistory({ jobs, onRestart, busy }: Props) {

  const { reportError } = useToast()
  const [openJob, setOpenJob] = useState<EnrichJobResponse | null>(null)
  const [failures, setFailures] = useState<EnrichFailureResponse[] | null>(null)

  const showDetails = (job: EnrichJobResponse) => {
    setOpenJob(job)
    setFailures(null)
    if (job.failedCount > 0) {
      api
        .jobFailures(job.executionId)
        .then(setFailures)
        .catch((error) => reportError(error, 'Nie udało się pobrać listy nieudanych utworów'))
    }
  }

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
            <th>Nieudane</th>
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
              <td>
                {job.failedCount > 0 ? (
                  <span className="job-failed" data-testid={`job-failed-${job.executionId}`}>
                    {job.failedCount}
                  </span>
                ) : (
                  <span className="muted">0</span>
                )}
              </td>
              <td className="muted">{formatDateTime(job.startTime)}</td>
              <td>
                <div className="job-actions">
                  {job.status === 'FAILED' && (
                    <button
                      className="link"
                      disabled={busy}
                      onClick={() => onRestart(job.executionId)}
                    >
                      restart od checkpointu
                    </button>
                  )}
                  {(job.failedCount > 0 || job.exitDescription) && (
                    <button
                      className="link"
                      onClick={() => showDetails(job)}
                      data-testid={`job-details-${job.executionId}`}
                    >
                      szczegóły
                    </button>
                  )}
                </div>
              </td>
            </tr>
          ))}
          {jobs.length === 0 && (
            <tr>
              <td colSpan={8} className="muted empty-row">
                Żaden job jeszcze nie startował.
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {openJob && (
        <Modal title={`Job #${openJob.executionId} — szczegóły`} onClose={() => setOpenJob(null)}>
          <dl className="track-facts">
            <dt>status</dt>
            <dd className={`status-${openJob.status}`}>{openJob.status}</dd>
            <dt>przetworzone</dt>
            <dd>
              {openJob.writeCount} z {openJob.readCount || DASH}
            </dd>
            <dt>nieudane</dt>
            <dd>{openJob.failedCount}</dd>
          </dl>

          {openJob.exitDescription && (
            <>
              <h3>Komunikat zakończenia</h3>
              <pre className="job-exit" data-testid="job-exit-description">
                {openJob.exitDescription}
              </pre>
            </>
          )}

          {openJob.failedCount > 0 && (
            <>
              <h3>Utwory pominięte</h3>
              {failures === null && <p className="muted">Ładowanie…</p>}
              {failures !== null && (
                <ul className="modal-list" data-testid="job-failures">
                  {failures.map((failure) => (
                    <li key={`${failure.spotifyId}-${failure.failedAt}`}>
                      <code>{failure.spotifyId}</code>
                      <p className="muted">{failure.reason}</p>
                    </li>
                  ))}
                </ul>
              )}
              {failures !== null && failures.length < openJob.failedCount && (
                <p className="muted">
                  Pokazano {failures.length} z {openJob.failedCount} — resztę znajdziesz w logu.
                </p>
              )}
            </>
          )}

          {openJob.failedCount === 0 && !openJob.exitDescription && (
            <p className="muted">Przebieg zakończył się bez pominiętych utworów.</p>
          )}
        </Modal>
      )}
    </div>
  )
}
