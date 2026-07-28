// Podsumowanie setu (M3.1): kafle z liczbami, rozkład faz wieczoru jako jeden
// pasek z bezpośrednimi etykietami i lista ostrzeżeń dla układającego set.

import type { PlaylistTrackResponse } from '../api'
import { SLOT_LABELS, SLOT_ORDER, formatTotalDuration, DASH } from '../format'
import { computeSetStats, findSetWarnings } from '../setPlanner'
import BpmCurve from './BpmCurve'

interface Props {
  tracks: readonly PlaylistTrackResponse[]
}

const SEGMENTS = [...SLOT_ORDER, 'UNKNOWN'] as const

const SEGMENT_LABELS: Record<(typeof SEGMENTS)[number], string> = {
  ...SLOT_LABELS,
  UNKNOWN: 'bez slotu',
}

export default function SetStats({ tracks }: Props) {

  const stats = computeSetStats(tracks)
  const warnings = findSetWarnings(tracks)

  if (stats.trackCount === 0) {
    return null
  }

  const points = tracks.map((entry, index) => ({
    position: index + 1,
    bpm: entry.track.bpm,
    label: entry.track.title ?? entry.track.spotifyId,
  }))

  return (
    <div className="set-stats" data-testid="set-stats">
      <div className="tiles">
        <div className="tile">
          <span className="tile-value">{stats.trackCount}</span>
          <span className="tile-label">utworów</span>
        </div>
        <div className="tile">
          <span className="tile-value">{formatTotalDuration(stats.totalDurationMs)}</span>
          <span className="tile-label">
            czas{stats.tracksWithDuration < stats.trackCount ? ' (znanych utworów)' : ''}
          </span>
        </div>
        <div className="tile">
          <span className="tile-value">
            {stats.bpmMin === null ? DASH : `${stats.bpmMin}–${stats.bpmMax}`}
          </span>
          <span className="tile-label">zakres BPM</span>
        </div>
        <div className="tile">
          <span className="tile-value">{stats.averageBpm ?? DASH}</span>
          <span className="tile-label">średnie BPM</span>
        </div>
      </div>

      <div className="slot-bar" aria-hidden="true">
        {SEGMENTS.filter((segment) => stats.slotCounts[segment] > 0).map((segment) => (
          <span
            key={segment}
            className={`slot-seg slot-bg-${segment}`}
            style={{ flexGrow: stats.slotCounts[segment] }}
          />
        ))}
      </div>
      <ul className="slot-legend">
        {SEGMENTS.filter((segment) => stats.slotCounts[segment] > 0).map((segment) => (
          <li key={segment}>
            <span className={`slot-dot slot-bg-${segment}`} aria-hidden="true" />
            {SEGMENT_LABELS[segment]}: <strong>{stats.slotCounts[segment]}</strong>
          </li>
        ))}
      </ul>

      <BpmCurve points={points} />

      {warnings.length > 0 && (
        <div className="warnings" data-testid="set-warnings">
          <h4>Do sprawdzenia ({warnings.length})</h4>
          <ul>
            {warnings.map((warning) => (
              <li key={`${warning.kind}-${warning.position}`} className={`warn-${warning.kind}`}>
                {warning.message}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
