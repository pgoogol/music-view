// Chmura własnych tagów DJ-a (M5.4). Tagi to jedyny rozkład, który powstał
// wprost z decyzji człowieka, a nie z metadanych — rozmiar pigułki niesie
// częstość, liczba obok zostaje, bo sam rozmiar nie da się odczytać dokładnie.

import type { BucketResponse } from '../../api'

interface Props {
  buckets: readonly BucketResponse[]
  testId?: string
}

const MIN_SIZE = 12
const MAX_SIZE = 22

export default function TagCloud({ buckets, testId }: Props) {

  if (buckets.length === 0) {
    return (
      <p className="muted" data-testid={testId}>
        Nie ma jeszcze żadnego własnego tagu — nadasz je w szufladzie utworu.
      </p>
    )
  }

  const max = buckets.reduce((highest, bucket) => Math.max(highest, bucket.count), 0)
  const min = buckets.reduce((lowest, bucket) => Math.min(lowest, bucket.count), max)
  const span = max - min || 1

  return (
    <ul className="tag-cloud" data-testid={testId}>
      {buckets.map((bucket, index) => (
        <li
          key={bucket.label}
          style={{
            fontSize: `${MIN_SIZE + ((bucket.count - min) / span) * (MAX_SIZE - MIN_SIZE)}px`,
            animationDelay: `${index * 35}ms`,
          }}
        >
          <span className="tag-name">{bucket.label}</span>
          <span className="tag-count">{bucket.count}</span>
        </li>
      ))}
    </ul>
  )
}
