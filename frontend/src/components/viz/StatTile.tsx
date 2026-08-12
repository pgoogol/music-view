// Kafel odczytu (M5.4) — jedna liczba nabijana od zera, etykieta i zdanie
// wyjaśniające, co ta liczba znaczy. Kolor niesie ton (akcent / pomiar /
// ostrzeżenie), ale nigdy sam nie niesie znaczenia — zawsze jest etykieta (D23).

import { useCountUp } from '../../hooks/useCountUp'

export type StatTone = 'accent' | 'measure' | 'warn'

interface Props {
  label: string
  value: number
  /** Domyślnie liczba całkowita; format podmienia się np. na godziny. */
  format?: (value: number) => string
  hint?: string
  unit?: string
  tone?: StatTone
  testId?: string
}

export default function StatTile({
  label,
  value,
  format,
  hint,
  unit,
  tone = 'measure',
  testId,
}: Props) {

  const animated = useCountUp(value)
  const shown = format ? format(animated) : String(Math.round(animated))

  return (
    <div className={`stat-tile tone-${tone}`} data-testid={testId}>
      <span className="stat-tile-label">{label}</span>
      <span className="stat-tile-value">
        {shown}
        {unit && <span className="stat-tile-unit">{unit}</span>}
      </span>
      {hint && <span className="stat-tile-hint">{hint}</span>}
    </div>
  )
}
