// Wykres słupkowy rysowany inline w SVG (M4.3) — jak krzywa tempa z M3.1,
// bez biblioteki wykresów i bez zasobów z sieci (D23). Używany tam, gdzie oś
// jest uporządkowana i niesie znaczenie: histogram BPM i przyrost po miesiącach.

import type { BucketResponse } from '../api'

interface Props {
  title: string
  buckets: readonly BucketResponse[]
  caption?: string
  testId?: string
}

const WIDTH = 640
const HEIGHT = 150
const PADDING = { top: 12, right: 12, bottom: 30, left: 34 }

export default function ColumnChart({ title, buckets, caption, testId }: Props) {

  if (buckets.length === 0) {
    return (
      <section className="distribution">
        <h4>{title}</h4>
        <p className="muted">Brak danych.</p>
      </section>
    )
  }

  const max = buckets.reduce((highest, bucket) => Math.max(highest, bucket.count), 0) || 1
  const plotWidth = WIDTH - PADDING.left - PADDING.right
  const plotHeight = HEIGHT - PADDING.top - PADDING.bottom
  const slot = plotWidth / buckets.length
  const barWidth = Math.max(2, slot * 0.7)
  // przy wielu koszykach podpisujemy co drugi, żeby etykiety się nie zlewały
  const labelEvery = buckets.length > 10 ? 2 : 1

  return (
    <section className="distribution">
      <h4>{title}</h4>
      <figure className="chart">
        <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="img" data-testid={testId}
             aria-label={`${title}: ${buckets.length} kategorii, największa ${max}`}>
          <line
            className="chart-axis"
            x1={PADDING.left}
            x2={WIDTH - PADDING.right}
            y1={PADDING.top + plotHeight}
            y2={PADDING.top + plotHeight}
          />
          {buckets.map((bucket, index) => {
            const height = (bucket.count / max) * plotHeight
            const x = PADDING.left + index * slot + (slot - barWidth) / 2
            return (
              <g key={bucket.label}>
                <rect
                  className="chart-bar"
                  x={x}
                  y={PADDING.top + plotHeight - height}
                  width={barWidth}
                  height={height}
                >
                  <title>{`${bucket.label}: ${bucket.count}`}</title>
                </rect>
                {index % labelEvery === 0 && (
                  <text
                    className="chart-tick"
                    x={x + barWidth / 2}
                    y={HEIGHT - 10}
                    textAnchor="middle"
                  >
                    {bucket.label}
                  </text>
                )}
              </g>
            )
          })}
          <text className="chart-tick" x={4} y={PADDING.top + 8}>
            {max}
          </text>
        </svg>
        {caption && <figcaption className="muted chart-caption">{caption}</figcaption>}
      </figure>
    </section>
  )
}
