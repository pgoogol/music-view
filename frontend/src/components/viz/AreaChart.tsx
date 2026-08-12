// Wykres wstęgowy (M5.4) — ta sama kreska co w krzywej tempa z M3.1, tylko
// z wypełnieniem pod spodem i rysowana od lewej przy wejściu na ekran.
// Inline SVG, bez biblioteki wykresów i bez zasobów z sieci (D23).
//
// Używany tam, gdzie oś X jest uporządkowana i niesie znaczenie: histogram BPM
// (gęstość biblioteki w tempie) i narastający przyrost biblioteki w czasie.

import { useId } from 'react'
import type { BucketResponse } from '../../api'

interface Props {
  points: readonly BucketResponse[]
  /** Opis dla czytnika ekranu i tytułu wykresu. */
  title: string
  caption?: string
  /** Jednostka w dymku pod kursorem — „utworów", „BPM". */
  unit?: string
  testId?: string
}

const WIDTH = 640
const HEIGHT = 190
const PADDING = { top: 16, right: 14, bottom: 32, left: 40 }

export default function AreaChart({ points, title, caption, unit = 'utworów', testId }: Props) {

  const gradientId = useId()

  if (points.length < 2) {
    return (
      <section className="viz">
        <h4>{title}</h4>
        <p className="muted">Za mało danych, żeby narysować wykres.</p>
      </section>
    )
  }

  const max = points.reduce((highest, point) => Math.max(highest, point.count), 0) || 1
  const plotWidth = WIDTH - PADDING.left - PADDING.right
  const plotHeight = HEIGHT - PADDING.top - PADDING.bottom
  const baseline = PADDING.top + plotHeight

  const x = (index: number) => PADDING.left + (index / (points.length - 1)) * plotWidth
  const y = (count: number) => baseline - (count / max) * plotHeight

  const line = points.map((point, index) => `${x(index)},${y(point.count)}`).join(' ')
  const area = `${PADDING.left},${baseline} ${line} ${x(points.length - 1)},${baseline}`
  // przy wielu koszykach podpisujemy co n-ty, żeby etykiety się nie zlewały
  const labelEvery = Math.ceil(points.length / 8)

  return (
    <section className="viz">
      <h4>{title}</h4>
      <figure className="chart">
        <svg
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          role="img"
          data-testid={testId}
          aria-label={`${title}: ${points.length} punktów, największa wartość ${max}`}
        >
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop className="area-stop-top" offset="0%" />
              <stop className="area-stop-bottom" offset="100%" />
            </linearGradient>
          </defs>

          <line
            className="chart-axis"
            x1={PADDING.left}
            x2={WIDTH - PADDING.right}
            y1={baseline}
            y2={baseline}
          />
          <line
            className="chart-axis"
            x1={PADDING.left}
            x2={WIDTH - PADDING.right}
            y1={PADDING.top + plotHeight / 2}
            y2={PADDING.top + plotHeight / 2}
          />

          <polygon className="area-fill" points={area} fill={`url(#${gradientId})`} />
          {/* ta sama kreska dwa razy: najpierw rozmyta poświata, potem ostry odczyt */}
          <polyline className="chart-line-glow" points={line} />
          <polyline className="area-line" points={line} pathLength={1} />

          <text className="chart-tick" x={4} y={PADDING.top + 8}>
            {max}
          </text>

          {points.map((point, index) => (
            <g key={point.label}>
              <circle className="area-dot" cx={x(index)} cy={y(point.count)} r={3} />
              {/* powiększony, przezroczysty cel najazdu — punkty są za małe na kursor */}
              <rect
                className="chart-hit"
                x={x(index) - plotWidth / points.length / 2}
                y={PADDING.top}
                width={plotWidth / points.length}
                height={plotHeight}
              >
                <title>{`${point.label}: ${point.count} ${unit}`}</title>
              </rect>
              {index % labelEvery === 0 && (
                <text className="chart-tick" x={x(index)} y={HEIGHT - 12} textAnchor="middle">
                  {point.label}
                </text>
              )}
            </g>
          ))}
        </svg>
        {caption && <figcaption className="muted chart-caption">{caption}</figcaption>}
      </figure>
    </section>
  )
}
