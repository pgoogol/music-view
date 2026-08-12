// Wskaźnik pierścieniowy (M5.4) — udział 0..1 jako łuk domykający się przy
// wejściu na ekran. Wartość liczbowa stoi w środku, bo sam łuk pozwala tylko
// porównywać, a DJ chce wiedzieć „ile dokładnie".

import { useCountUp } from '../../hooks/useCountUp'

interface Props {
  ratio: number
  label: string
  /** Podpis pod pierścieniem — „1840 z 2500", nie powtórzenie procentu. */
  caption?: string
  testId?: string
}

const SIZE = 120
const STROKE = 10
const RADIUS = (SIZE - STROKE) / 2
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

export default function Gauge({ ratio, label, caption, testId }: Props) {

  const safe = Math.max(0, Math.min(1, ratio))
  const animated = useCountUp(safe)
  const percent = Math.round(safe * 100)

  return (
    <figure className="gauge" data-testid={testId}>
      <svg viewBox={`0 0 ${SIZE} ${SIZE}`} role="img" aria-label={`${label}: ${percent}%`}>
        {/* obrót o ćwierć koła: łuk startuje z godziny dwunastej, nie trzeciej */}
        <g transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}>
          <circle
            className="gauge-track"
            cx={SIZE / 2}
            cy={SIZE / 2}
            r={RADIUS}
            strokeWidth={STROKE}
          />
          <circle
            className="gauge-fill"
            cx={SIZE / 2}
            cy={SIZE / 2}
            r={RADIUS}
            strokeWidth={STROKE}
            strokeDasharray={CIRCUMFERENCE}
            strokeDashoffset={CIRCUMFERENCE * (1 - animated)}
          />
        </g>
        <text className="gauge-value" x={SIZE / 2} y={SIZE / 2 + 6} textAnchor="middle">
          {percent}%
        </text>
      </svg>
      <figcaption>
        <span className="gauge-label">{label}</span>
        {caption && <span className="muted"> {caption}</span>}
      </figcaption>
    </figure>
  )
}
