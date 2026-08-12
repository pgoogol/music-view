// Koło Camelot (M5.4) — 24 pozycje tonacji jako tarcza, bo tak DJ ich używa:
// sąsiedztwo na kole jest zgodnością harmoniczną (D25), a lista alfabetyczna
// tę informację gubi. Pierścień wewnętrzny to strona molowa (A), zewnętrzny
// durowa (B); nasycenie wycinka = ile utworów siedzi w tej tonacji.
//
// Pozycja koła jest liczona z tonacji, nigdy zapisana (D25) — backend podaje
// gotowe koszyki, tu zostaje sama geometria.

import type { BucketResponse } from '../../api'
import { compatibleCamelot, largestBucket, NO_KEY } from '../../overviewInsights'

interface Props {
  buckets: readonly BucketResponse[]
  testId?: string
}

const CENTER = 100
const RADII = { outer: 94, middle: 64, inner: 36 }
const POSITIONS = 12
const SECTOR = 360 / POSITIONS

/** Punkt na okręgu; kąt liczony od godziny dwunastej, zgodnie z ruchem wskazówek. */
function point(angleDegrees: number, radius: number) {
  const radians = ((angleDegrees - 90) * Math.PI) / 180
  return {
    x: CENTER + radius * Math.cos(radians),
    y: CENTER + radius * Math.sin(radians),
  }
}

/** Wycinek pierścienia: łuk zewnętrzny w prawo, wewnętrzny z powrotem w lewo. */
function sectorPath(index: number, from: number, to: number): string {
  const start = index * SECTOR - SECTOR / 2
  const end = start + SECTOR
  const outerStart = point(start, to)
  const outerEnd = point(end, to)
  const innerEnd = point(end, from)
  const innerStart = point(start, from)
  return [
    `M ${outerStart.x} ${outerStart.y}`,
    `A ${to} ${to} 0 0 1 ${outerEnd.x} ${outerEnd.y}`,
    `L ${innerEnd.x} ${innerEnd.y}`,
    `A ${from} ${from} 0 0 0 ${innerStart.x} ${innerStart.y}`,
    'Z',
  ].join(' ')
}

export default function CamelotWheel({ buckets, testId }: Props) {

  const counts = new Map(buckets.map((bucket) => [bucket.label, bucket.count]))
  const dominant = largestBucket(buckets, [NO_KEY])
  const compatible = new Set(dominant === null ? [] : compatibleCamelot(dominant.label))
  const known = buckets
    .filter((bucket) => bucket.label !== NO_KEY)
    .reduce((sum, bucket) => sum + bucket.count, 0)
  const max = buckets
    .filter((bucket) => bucket.label !== NO_KEY)
    .reduce((highest, bucket) => Math.max(highest, bucket.count), 0)
  const withoutKey = counts.get(NO_KEY) ?? 0

  if (known === 0) {
    return (
      <p className="muted" data-testid={testId}>
        Żaden utwór nie ma jeszcze rozpoznanej tonacji — koło zapali się po
        wzbogaceniu grupy audio.
      </p>
    )
  }

  const cells = Array.from({ length: POSITIONS }, (_, index) => index).flatMap((index) =>
    (['A', 'B'] as const).map((side) => {
      const label = `${index + 1}${side}`
      const count = counts.get(label) ?? 0
      const ring = side === 'A'
        ? { from: RADII.inner, to: RADII.middle }
        : { from: RADII.middle, to: RADII.outer }
      return { label, count, side, index, ring }
    }),
  )

  return (
    <figure className="chart wheel">
      <svg
        viewBox="0 0 200 200"
        role="img"
        data-testid={testId}
        aria-label={`Koło Camelot: ${known} utworów w ${
          buckets.filter((bucket) => bucket.label !== NO_KEY).length
        } tonacjach`}
      >
        {cells.map((cell, order) => (
          <path
            key={cell.label}
            className={[
              'wheel-cell',
              `wheel-side-${cell.side}`,
              dominant?.label === cell.label ? 'dominant' : '',
              compatible.has(cell.label) ? 'compatible' : '',
            ]
              .filter(Boolean)
              .join(' ')}
            d={sectorPath(cell.index, cell.ring.from, cell.ring.to)}
            style={{
              opacity: max === 0 ? 0.1 : 0.12 + (cell.count / max) * 0.88,
              animationDelay: `${order * 18}ms`,
            }}
          >
            <title>{`${cell.label}: ${cell.count} utworów`}</title>
          </path>
        ))}

        {Array.from({ length: POSITIONS }, (_, index) => {
          const position = point(index * SECTOR, (RADII.middle + RADII.outer) / 2)
          return (
            <text
              key={index}
              className="wheel-number"
              x={position.x}
              y={position.y + 3}
              textAnchor="middle"
            >
              {index + 1}
            </text>
          )
        })}

        <circle className="wheel-hub" cx={CENTER} cy={CENTER} r={RADII.inner - 3} />
        {dominant !== null && (
          <>
            <text className="wheel-hub-value" x={CENTER} y={CENTER + 2} textAnchor="middle">
              {dominant.label}
            </text>
            <text className="wheel-hub-label" x={CENTER} y={CENTER + 18} textAnchor="middle">
              dominuje
            </text>
          </>
        )}
      </svg>
      <figcaption className="muted chart-caption">
        Pierścień wewnętrzny — moll (A), zewnętrzny — dur (B). Obrys pokazuje tonacje
        wchodzące zgodnie z {dominant?.label ?? '—'} (D25)
        {withoutKey > 0 && `; ${withoutKey} utworów nadal bez tonacji`}.
      </figcaption>
    </figure>
  )
}
