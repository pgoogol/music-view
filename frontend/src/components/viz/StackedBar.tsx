// Jeden pasek podzielony na udziały (M5.4) — dla rozkładów, w których liczy się
// proporcja całości, a nie porównanie kategorii między sobą: skąd znamy tempo,
// skąd wpisy trafiły do biblioteki. Kolor odróżnia segmenty, ale znaczenie
// niesie legenda pod paskiem (D23) — kolor sam nigdy nie wystarcza.

import type { BucketResponse } from '../../api'
import { share } from '../../overviewInsights'

interface Props {
  buckets: readonly BucketResponse[]
  /** Tłumaczenie etykiet z bazy na polskie nazwy; brak = etykieta wprost. */
  labels?: Record<string, string>
  caption?: string
  testId?: string
}

export default function StackedBar({ buckets, labels, caption, testId }: Props) {

  const total = buckets.reduce((sum, bucket) => sum + bucket.count, 0)

  if (total === 0) {
    return <p className="muted">Brak danych.</p>
  }

  const name = (label: string) => labels?.[label] ?? label

  return (
    <div className="stacked" data-testid={testId}>
      <div className="stacked-bar" role="img"
           aria-label={buckets
             .map((bucket) => `${name(bucket.label)} ${Math.round(share(bucket.count, total) * 100)}%`)
             .join(', ')}>
        {buckets.map((bucket, index) => (
          <span
            key={bucket.label}
            className={`stacked-seg viz-tone-${index % 6}`}
            style={{ width: `${share(bucket.count, total) * 100}%` }}
            title={`${name(bucket.label)}: ${bucket.count}`}
          />
        ))}
      </div>
      <ul className="stacked-legend">
        {buckets.map((bucket, index) => (
          <li key={bucket.label}>
            <span className={`legend-dot viz-tone-${index % 6}`} aria-hidden="true" />
            <span className="legend-label">{name(bucket.label)}</span>
            <span className="legend-value">
              {bucket.count}
              <span className="muted"> · {Math.round(share(bucket.count, total) * 100)}%</span>
            </span>
          </li>
        ))}
      </ul>
      {caption && <p className="muted chart-caption">{caption}</p>}
    </div>
  )
}
