// Pajęczyna cech audio (M5.4) — uśredniony profil brzmienia biblioteki
// z metryk wgranych ręcznie (D24). Siedem osi w skali 0..1; wielokąt rysuje się
// przy wejściu na ekran, żeby było widać kształt, a nie tylko siedem liczb.

import type { MetricResponse } from '../../api'

interface Props {
  metrics: readonly MetricResponse[]
  /** Ile utworów ma metryki — bez tego procenty wiszą w próżni. */
  basis: number
  testId?: string
}

// Płótno jest szersze niż wysokie, bo etykiety osi wychodzą na boki: przy
// kwadratowym viewBoksie „instrumentalność" ucinało się o krawędź.
const WIDTH = 340
const HEIGHT = 232
const CENTER = { x: WIDTH / 2, y: 108 }
const RADIUS = 70
const LABEL_GAP = 18
const RINGS = [0.25, 0.5, 0.75, 1]

/** Etykiety osi po polsku; nieznana cecha zostaje przy nazwie z pliku. */
const AXIS_LABELS: Record<string, string> = {
  danceability: 'taneczność',
  energy: 'energia',
  valence: 'nastrój',
  acousticness: 'akustyka',
  instrumentalness: 'instrum.',
  speechiness: 'mowa',
  liveness: 'live',
}

function point(index: number, count: number, radius: number) {
  const angle = ((index / count) * 360 - 90) * (Math.PI / 180)
  return { x: CENTER.x + radius * Math.cos(angle), y: CENTER.y + radius * Math.sin(angle) }
}

export default function RadarChart({ metrics, basis, testId }: Props) {

  if (metrics.length < 3) {
    return (
      <p className="muted" data-testid={testId}>
        Profil brzmienia policzy się po wgraniu metryk z pliku (zakładka Import).
      </p>
    )
  }

  const shape = metrics
    .map((metric, index) => {
      const position = point(index, metrics.length, RADIUS * Math.max(0, Math.min(1, metric.value)))
      return `${position.x},${position.y}`
    })
    .join(' ')

  return (
    <figure className="chart radar">
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        data-testid={testId}
        aria-label={`Profil brzmienia z ${basis} utworów: ${metrics
          .map((metric) => `${AXIS_LABELS[metric.label] ?? metric.label} ${Math.round(metric.value * 100)}`)
          .join(', ')}`}
      >
        {RINGS.map((ring) => (
          <polygon
            key={ring}
            className="radar-ring"
            points={metrics
              .map((_, index) => {
                const position = point(index, metrics.length, RADIUS * ring)
                return `${position.x},${position.y}`
              })
              .join(' ')}
          />
        ))}
        {metrics.map((metric, index) => {
          const spoke = point(index, metrics.length, RADIUS)
          const label = point(index, metrics.length, RADIUS + LABEL_GAP)
          return (
            <g key={metric.label}>
              <line className="radar-spoke" x1={CENTER.x} y1={CENTER.y} x2={spoke.x} y2={spoke.y} />
              <text
                className="radar-label"
                x={label.x}
                y={label.y + 3}
                textAnchor={
                  label.x > CENTER.x + 4 ? 'start' : label.x < CENTER.x - 4 ? 'end' : 'middle'
                }
              >
                {AXIS_LABELS[metric.label] ?? metric.label}
              </text>
            </g>
          )
        })}
        <polygon className="radar-shape" points={shape} />
        {metrics.map((metric, index) => {
          const position = point(index, metrics.length, RADIUS * Math.max(0, Math.min(1, metric.value)))
          return (
            <circle key={metric.label} className="radar-dot" cx={position.x} cy={position.y} r={3}>
              <title>{`${AXIS_LABELS[metric.label] ?? metric.label}: ${Math.round(metric.value * 100)} / 100`}</title>
            </circle>
          )
        })}
      </svg>
      <figcaption className="muted chart-caption">
        Średnia z {basis} utworów z metrykami (D24) — reszta biblioteki nie wchodzi do wykresu.
      </figcaption>
    </figure>
  )
}
