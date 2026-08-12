// Widok wzbogacania (M1.8, rozbudowa M3.1, koszty M5.1): pokrycie pól D11
// na paskach, zlecenie joba (zakres + grupy pól) i historia wykonań z restartem.
// Szacunek liczby utworów i kosztu pokazujemy PRZED startem joba (D28) — po
// starcie jest już za późno, żeby się rozmyślić.

import { useCallback, useEffect, useRef, useState } from 'react'
import {
  api,
  type EnrichJobResponse,
  type EnrichmentEstimateResponse,
  type MissingCountResponse,
} from '../api'
import JobHistory from '../components/JobHistory'
import { useToast } from '../components/Toasts'

const FIELD_GROUPS = ['METADATA', 'AUDIO', 'AI'] as const
const GROUP_LABELS: Record<string, string> = {
  METADATA: 'metadane (Spotify)',
  AUDIO: 'audio (BPM, tonacja)',
  AI: 'opisy AI',
}
const SCOPES = ['MISSING', 'SELECTED', 'OUTDATED'] as const
type Scope = (typeof SCOPES)[number]
const ACTIVE_STATUSES = new Set(['STARTING', 'STARTED', 'STOPPING'])
const POLL_INTERVAL_MS = 2000

interface Props {
  selectedIds: ReadonlySet<string>
  onJobFinished: () => void
}

export default function EnrichView({ selectedIds, onJobFinished }: Props) {

  const { notify, reportError } = useToast()
  const [fields, setFields] = useState<Set<string>>(new Set(FIELD_GROUPS))
  const [scope, setScope] = useState<Scope>('MISSING')
  const [estimate, setEstimate] = useState<EnrichmentEstimateResponse | null>(null)
  const [estimateError, setEstimateError] = useState<string | null>(null)
  const [missing, setMissing] = useState<MissingCountResponse | null>(null)
  const [total, setTotal] = useState<number | null>(null)
  const [jobs, setJobs] = useState<EnrichJobResponse[]>([])
  const [busy, setBusy] = useState(false)
  const pollTimer = useRef<number | null>(null)

  const refreshOverview = useCallback(() => {
    api.missingCount().then(setMissing).catch(() => setMissing(null))
    api
      .searchTracks({ size: 1 })
      .then((page) => setTotal(page.totalElements))
      .catch(() => setTotal(null))
    api.listJobs(15).then(setJobs).catch(() => setJobs([]))
  }, [])

  useEffect(() => {
    refreshOverview()
    return () => {
      if (pollTimer.current !== null) window.clearTimeout(pollTimer.current)
    }
  }, [refreshOverview])

  // szacunek odświeża się przy każdej zmianie zakresu/pól — to jedno tanie
  // zapytanie, a bez niego DJ zlecałby job w ciemno
  useEffect(() => {
    if (fields.size === 0 || (scope === 'SELECTED' && selectedIds.size === 0)) {
      setEstimate(null)
      setEstimateError(null)
      return
    }
    let current = true
    const ids = scope === 'SELECTED' ? [...selectedIds] : []
    api
      .estimateEnrichment(scope, [...fields], ids)
      .then((loaded) => {
        if (!current) return
        setEstimate(loaded)
        setEstimateError(null)
      })
      .catch((error) => {
        if (!current) return
        setEstimate(null)
        setEstimateError(error instanceof Error ? error.message : 'nie udało się oszacować')
      })
    return () => {
      current = false
    }
  }, [scope, fields, selectedIds])

  const poll = useCallback(
    (executionId: number) => {
      api
        .jobStatus(executionId)
        .then((status) => {
          setJobs((current) =>
            current.some((job) => job.executionId === status.executionId)
              ? current.map((job) => (job.executionId === status.executionId ? status : job))
              : [status, ...current],
          )
          if (ACTIVE_STATUSES.has(status.status)) {
            pollTimer.current = window.setTimeout(() => poll(executionId), POLL_INTERVAL_MS)
            return
          }
          setBusy(false)
          refreshOverview()
          onJobFinished()
          if (status.status === 'COMPLETED') {
            notify(`Job #${status.executionId} zakończony — wzbogacono ${status.writeCount} utworów`)
          } else {
            notify(`Job #${status.executionId}: ${status.status}`, 'error')
          }
        })
        .catch((error) => {
          setBusy(false)
          reportError(error, 'Utracono kontakt z jobem')
        })
    },
    [notify, onJobFinished, refreshOverview, reportError],
  )

  const start = async () => {
    setBusy(true)
    try {
      const ids = scope === 'SELECTED' ? [...selectedIds] : []
      const { executionId } = await api.startEnrichment(scope, [...fields], ids)
      notify(`Zlecono job #${executionId}`)
      poll(executionId)
    } catch (error) {
      setBusy(false)
      reportError(error, 'Nie udało się zlecić wzbogacania')
    }
  }

  const restart = async (executionId: number) => {
    setBusy(true)
    try {
      const restarted = await api.restartJob(executionId)
      notify(`Restart joba #${executionId} → #${restarted.executionId}`)
      poll(restarted.executionId)
    } catch (error) {
      setBusy(false)
      reportError(error, 'Restart joba nie powiódł się')
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

  const missingFor = (group: string): number | null => {
    if (!missing) return null
    if (group === 'METADATA') return missing.metadata
    if (group === 'AUDIO') return missing.audio
    return missing.ai
  }

  return (
    <div className="enrich-layout">
      <section className="panel" aria-label="Pokrycie pól">
        <h2>Pokrycie biblioteki</h2>
        <p className="muted">
          {total === null ? 'Ładowanie…' : `${total} utworów w katalogu`}
        </p>
        <ul className="coverage" data-testid="coverage">
          {FIELD_GROUPS.map((group) => {
            const gaps = missingFor(group)
            // licznik braków i suma katalogu to dwa osobne zapytania — zaokrąglamy
            // wynik do 0–100%, żeby wyścig między nimi nie dał ujemnego paska
            const covered = total !== null && gaps !== null ? Math.max(0, total - gaps) : null
            const percent =
              covered !== null && total ? Math.min(100, Math.round((covered / total) * 100)) : null
            return (
              <li key={group}>
                <div className="coverage-head">
                  <span>{GROUP_LABELS[group]}</span>
                  <strong>{percent === null ? '—' : `${percent}%`}</strong>
                </div>
                <div className="coverage-bar">
                  <span className="coverage-fill" style={{ width: `${percent ?? 0}%` }} />
                </div>
                <span className="muted">
                  {gaps === null ? 'brak danych' : `braki: ${gaps}`}
                </span>
              </li>
            )
          })}
        </ul>
      </section>

      <section className="panel" aria-label="Wzbogacanie">
        <h2>Zleć wzbogacanie</h2>

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
          <label title="utwory opisane starszym modelem albo starszą wersją promptu (D28)">
            <input
              type="radio"
              name="scope"
              checked={scope === 'OUTDATED'}
              onChange={() => {
                setScope('OUTDATED')
                setFields(new Set(['AI']))
              }}
            />
            do przeliczenia (stary model/prompt)
          </label>
        </div>

        <div className="row">
          {FIELD_GROUPS.map((group) => (
            <label key={group}>
              <input
                type="checkbox"
                checked={fields.has(group)}
                onChange={() => toggleField(group)}
                disabled={scope === 'OUTDATED' && group !== 'AI'}
              />
              {GROUP_LABELS[group]}
            </label>
          ))}
        </div>

        <p className="estimate" data-testid="enrich-estimate">
          {estimateError && <span className="error">Szacunek niedostępny: {estimateError}</span>}
          {!estimateError && !estimate && <span className="muted">Wybierz zakres i grupy pól.</span>}
          {estimate && (
            <>
              <strong>{estimate.trackCount}</strong> utworów w zleceniu
              {estimate.aiTracks > 0 && (
                <>
                  {' · płatnych '}
                  <strong>{estimate.aiTracks}</strong>
                  {' · koszt '}
                  <strong>
                    {estimate.estimatedCost === null
                      ? 'nieznany'
                      : `~$${estimate.estimatedCost.toFixed(4)}`}
                  </strong>
                </>
              )}
              {estimate.aiTracks === 0 && ' · bez kosztu i bez limitu (darmowe źródła)'}
              {estimate.estimatedCost === null && estimate.aiTracks > 0 && (
                <span className="muted">
                  {' '}
                  — ustaw llm.cost.input-per-1m i llm.cost.output-per-1m
                </span>
              )}
              {/* sufit dotyczy wyłącznie utworów idących do modelu (D37) —
                  metadane i audio mogą lecieć w dowolnej liczbie */}
              {!estimate.withinLimit && (
                <span className="error">
                  {' '}
                  — do modelu idzie {estimate.aiTracks} przy limicie {estimate.limit}, job nie
                  wystartuje. Odznacz grupę „analiza AI", żeby puścić resztę bez sufitu.
                </span>
              )}
            </>
          )}
        </p>

        <div className="row">
          <button
            onClick={start}
            disabled={
              busy ||
              fields.size === 0 ||
              (scope === 'SELECTED' && selectedIds.size === 0) ||
              estimate?.withinLimit === false
            }
            data-testid="enrich-start"
          >
            {busy ? 'Job w toku…' : 'Start'}
          </button>
        </div>

        <h3>Ostatnie joby</h3>
        <JobHistory jobs={jobs} onRestart={restart} busy={busy} />
      </section>
    </div>
  )
}
