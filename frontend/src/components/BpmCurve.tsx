// Krzywa BPM setu (M3.1) — jedna seria, więc bez legendy; linia 2px w kolorze
// akcentu, punkty bez BPM zaznaczone pustym znacznikiem na dole skali.
// Etykiety tylko na skrajnych wartościach; szczegóły pod kursorem (title).

import { DASH } from '../format'

export interface BpmPoint {
  position: number
  bpm: number | null
  label: string
}

interface Props {
  points: readonly BpmPoint[]
}

const WIDTH = 640
const HEIGHT = 120
const PADDING = { top: 12, right: 12, bottom: 18, left: 32 }

export default function BpmCurve({ points }: Props) {

  const known = points.filter((point) => point.bpm !== null)
  if (known.length < 2) {
    return (
      <p className="muted" data-testid="bpm-curve-empty">
        Za mało utworów z BPM, żeby narysować krzywą tempa.
      </p>
    )
  }

  const values = known.map((point) => point.bpm as number)
  const min = Math.min(...values)
  const max = Math.max(...values)
  // płaski set (wszystkie te same BPM) nie może dzielić przez zero — rysujemy go w połowie
  const span = max - min || 1
  const plotWidth = WIDTH - PADDING.left - PADDING.right
  const plotHeight = HEIGHT - PADDING.top - PADDING.bottom

  const x = (index: number) =>
    PADDING.left + (points.length === 1 ? plotWidth / 2 : (index / (points.length - 1)) * plotWidth)
  const y = (bpm: number) => PADDING.top + plotHeight - ((bpm - min) / span) * plotHeight

  const line = points
    .map((point, index) => (point.bpm === null ? null : `${x(index)},${y(point.bpm)}`))
    .filter((coordinate): coordinate is string => coordinate !== null)
    .join(' ')

  return (
    <figure className="chart" aria-label={`Krzywa tempa setu, od ${min} do ${max} BPM`}>
      <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="img" data-testid="bpm-curve">
        <line
          className="chart-axis"
          x1={PADDING.left}
          x2={WIDTH - PADDING.right}
          y1={PADDING.top + plotHeight}
          y2={PADDING.top + plotHeight}
        />
        <text className="chart-tick" x={4} y={PADDING.top + 4}>
          {max}
        </text>
        <text className="chart-tick" x={4} y={PADDING.top + plotHeight}>
          {min}
        </text>
        <polyline className="chart-line" points={line} />
        {points.map((point, index) => (
          <g key={point.position}>
            <circle
              className={point.bpm === null ? 'chart-dot missing' : 'chart-dot'}
              cx={x(index)}
              cy={point.bpm === null ? PADDING.top + plotHeight : y(point.bpm)}
              r={point.bpm === null ? 3 : 4}
            />
            {/* powiększony, przezroczysty cel najazdu — punkty są za małe na kursor */}
            <circle className="chart-hit" cx={x(index)} cy={HEIGHT / 2} r={10}>
              <title>
                {point.position}. {point.label} — {point.bpm ?? DASH} BPM
              </title>
            </circle>
          </g>
        ))}
      </svg>
      <figcaption className="muted">
        tempo kolejnych pozycji setu ({min}–{max} BPM)
      </figcaption>
    </figure>
  )
}
