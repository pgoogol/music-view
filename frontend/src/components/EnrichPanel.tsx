import { useCallback, useEffect, useRef, useState } from 'react'
import { api, ApiError, type EnrichJobResponse, type MissingCountResponse } from '../api'

const FIELD_GROUPS = ['METADATA', 'AUDIO', 'AI'] as const
const ACTIVE_STATUSES = new Set(['STARTING', 'STARTED', 'STOPPING'])
const POLL_INTERVAL_MS = 2000

interface Props {
  selectedIds: ReadonlySet<string>
  onJobFinished: () => void
}

export default function EnrichPanel({ selectedIds, onJobFinished }: Props) {
  const [fields, setFields] = useState<Set<string>>(new Set(FIELD_GROUPS))
  const [scope, setScope] = useState<'MISSING' | 'SELECTED'>('MISSING')
  const [missing, setMissing] = useState<MissingCountResponse | null>(null)
  const [job, setJob] = useState<EnrichJobResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const pollTimer = useRef<number | null>(null)

  const refreshMissing = useCallback(() => {
    api.missingCount().then(setMissing).catch(() => setMissing(null))
  }, [])

  useEffect(() => {
    refreshMissing()
    return () => {
      if (pollTimer.current !== null) window.clearTimeout(pollTimer.current)
    }
  }, [refreshMissing])

  const poll = useCallback(
    (executionId: number) => {
      api
        .jobStatus(executionId)
        .then((status) => {
          setJob(status)
          if (ACTIVE_STATUSES.has(status.status)) {
            pollTimer.current = window.setTimeout(() => poll(executionId), POLL_INTERVAL_MS)
          } else {
            refreshMissing()
            onJobFinished()
          }
        })
        .catch((ex) => setError(String(ex)))
    },
    [onJobFinished, refreshMissing],
  )

  const start = async () => {
    setError(null)
    try {
      const ids = scope === 'SELECTED' ? [...selectedIds] : []
      const { executionId } = await api.startEnrichment(scope, [...fields], ids)
      poll(executionId)
    } catch (ex) {
      setError(ex instanceof ApiError ? `${ex.errorCode}: ${ex.message}` : String(ex))
    }
  }

  const restart = async () => {
    if (!job) return
    setError(null)
    try {
      const { executionId } = await api.restartJob(job.executionId)
      poll(executionId)
    } catch (ex) {
      setError(ex instanceof ApiError ? `${ex.errorCode}: ${ex.message}` : String(ex))
    }
  }

  const toggleField = (group: string) => {
    setFields((current) => {
      const next = new Set(current)
      if (next.has(group)) next.delete(group)
      else next.add(group)
      return next
    })
  }

  const jobActive = job !== null && ACTIVE_STATUSES.has(job.status)

  return (
    <section className="panel" aria-label="Wzbogacanie">
      <h2>Wzbogacanie</h2>

      {missing && (
        <p className="muted" data-testid="missing-count">
          braki — metadane: {missing.metadata}, audio: {missing.audio}, AI: {missing.ai}
        </p>
      )}

      <div className="row">
        <label>
          <input
            type="radio"
            name="scope"
            checked={scope === 'MISSING'}
            onChange={() => setScope('MISSING')}
          />
          wszystkie z brakami
        </label>
        <label>
          <input
            type="radio"
            name="scope"
            checked={scope === 'SELECTED'}
            onChange={() => setScope('SELECTED')}
            disabled={selectedIds.size === 0}
          />
          zaznaczone ({selectedIds.size})
        </label>
      </div>

      <div className="row">
        {FIELD_GROUPS.map((group) => (
          <label key={group}>
            <input
              type="checkbox"
              checked={fields.has(group)}
              onChange={() => toggleField(group)}
            />
            {group}
          </label>
        ))}
      </div>

      <div className="row">
        <button
          onClick={start}
          disabled={jobActive || fields.size === 0 || (scope === 'SELECTED' && selectedIds.size === 0)}
          data-testid="enrich-start"
        >
          {jobActive ? 'Job w toku…' : 'Start'}
        </button>
        {job?.status === 'FAILED' && (
          <button onClick={restart} data-testid="enrich-restart">
            Restart od checkpointu
          </button>
        )}
      </div>

      {job && (
        <p className="report" data-testid="job-status">
          job #{job.executionId}: <strong className={`status-${job.status}`}>{job.status}</strong>
          {' — '}przetworzone {job.writeCount} utworów ({job.fields}, {job.scope})
        </p>
      )}
      {error && <p className="error">{error}</p>}
    </section>
  )
}
