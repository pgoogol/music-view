// Rozkład jako lista słupków (M4.3). Kategorie są nieuporządkowane albo mają
// własną kolejność z bazy, więc czyta się je jako listę z długością słupka —
// nie jako wykres wymagający osi.

import type { BucketResponse } from '../api'

interface Props {
  title: string
  buckets: readonly BucketResponse[]
  /** Podpis pod listą — mówi, co liczba znaczy, gdy nie wynika to z tytułu. */
  caption?: string
  testId?: string
}

export default function Distribution({ title, buckets, caption, testId }: Props) {

  const max = buckets.reduce((highest, bucket) => Math.max(highest, bucket.count), 0)
  const total = buckets.reduce((sum, bucket) => sum + bucket.count, 0)

  return (
    <section className="distribution" data-testid={testId}>
      <h4>{title}</h4>
      {buckets.length === 0 && <p className="muted">Brak danych.</p>}
      <ul className="bars">
        {buckets.map((bucket) => (
          <li key={bucket.label}>
            <span className="bar-label" title={bucket.label}>
              {bucket.label}
            </span>
            <span className="bar-track">
              <span
                className="bar-fill"
                style={{ width: `${max === 0 ? 0 : (bucket.count / max) * 100}%` }}
              />
            </span>
            <span className="bar-value">
              {bucket.count}
              {total > 0 && (
                <span className="muted"> · {Math.round((bucket.count / total) * 100)}%</span>
              )}
            </span>
          </li>
        ))}
      </ul>
      {caption && <p className="muted chart-caption">{caption}</p>}
    </section>
  )
}
