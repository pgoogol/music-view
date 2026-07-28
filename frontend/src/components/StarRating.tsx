// Ocena utworu jako klikalne gwiazdki (M3.1) — wcześniej lista rozwijana.

interface Props {
  value: number
  onChange?: (rating: number) => void
  label?: string
}

const STARS = [1, 2, 3, 4, 5]

export default function StarRating({ value, onChange, label = 'ocena' }: Props) {

  const readOnly = onChange === undefined

  if (readOnly) {
    return (
      <span className="stars stars-static" aria-label={`${label}: ${value} z 5`}>
        {STARS.map((star) => (
          <span key={star} className={star <= value ? 'star on' : 'star'}>
            ★
          </span>
        ))}
      </span>
    )
  }

  return (
    <span className="stars" role="radiogroup" aria-label={label}>
      {STARS.map((star) => (
        <button
          key={star}
          type="button"
          role="radio"
          aria-checked={star === value}
          aria-label={`${star} z 5`}
          className={star <= value ? 'star on' : 'star'}
          // ponowne kliknięcie tej samej gwiazdki czyści ocenę (rating 0 = brak, D3)
          onClick={() => onChange(star === value ? 0 : star)}
        >
          ★
        </button>
      ))}
      <button type="button" className="link star-clear" onClick={() => onChange(0)}>
        wyczyść
      </button>
    </span>
  )
}
