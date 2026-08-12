// Ranking jako lista rosnących słupków (M5.4) — następca `Distribution` z M4.3
// dla list typu „top 10". Numer pozycji stoi przed etykietą, bo przy dziesięciu
// wykonawcach o zbliżonej liczbie utworów kolejność czyta się z cyfry szybciej
// niż z długości paska.

import type { BucketResponse } from '../../api'
import { share } from '../../overviewInsights'

interface Props {
  buckets: readonly BucketResponse[]
  /** Odniesienie dla procentu — domyślnie suma słupków. */
  total?: number
  /** Pokaż numer pozycji (rankingi) albo nie (rozkłady kategorii). */
  ranked?: boolean
  labels?: Record<string, string>
  emptyText?: string
  testId?: string
}

export default function RankedBars({
  buckets,
  total,
  ranked = false,
  labels,
  emptyText = 'Brak danych.',
  testId,
}: Props) {

  if (buckets.length === 0) {
    return <p className="muted" data-testid={testId}>{emptyText}</p>
  }

  const max = buckets.reduce((highest, bucket) => Math.max(highest, bucket.count), 0)
  const basis = total ?? buckets.reduce((sum, bucket) => sum + bucket.count, 0)

  return (
    <ol className={ranked ? 'ranked ranked-numbered' : 'ranked'} data-testid={testId}>
      {buckets.map((bucket, index) => (
        <li key={bucket.label} style={{ animationDelay: `${index * 40}ms` }}>
          {ranked && <span className="ranked-position">{index + 1}</span>}
          <span className="ranked-label" title={bucket.label}>
            {labels?.[bucket.label] ?? bucket.label}
          </span>
          <span className="ranked-track">
            <span
              className="ranked-fill"
              style={{ width: `${max === 0 ? 0 : (bucket.count / max) * 100}%` }}
            />
          </span>
          <span className="ranked-value">
            {bucket.count}
            {basis > 0 && (
              <span className="muted"> · {Math.round(share(bucket.count, basis) * 100)}%</span>
            )}
          </span>
        </li>
      ))}
    </ol>
  )
}
