// Stronicowanie biblioteki (M1.8, rozbudowa M5.6). Przy 2500 utworach i stronie
// po 20 pozycji „następna" jest jedynym wyjściem tylko na papierze — stąd skok
// na pierwszą i ostatnią stronę oraz wpisanie numeru z ręki.

import { useEffect, useState } from 'react'

interface Props {
  page: number
  totalPages: number
  onPage: (page: number) => void
}

export default function Pager({ page, totalPages, onPage }: Props) {

  const [draft, setDraft] = useState(String(page + 1))

  // skok strzałkami albo zmiana filtrów musi dogonić pole numeru strony
  useEffect(() => setDraft(String(page + 1)), [page])

  const last = totalPages - 1

  const commit = () => {
    const requested = Number(draft)
    if (!Number.isFinite(requested)) {
      setDraft(String(page + 1))
      return
    }
    onPage(Math.min(Math.max(Math.round(requested) - 1, 0), last))
  }

  return (
    <div className="pager" data-testid="pager">
      <button disabled={page === 0} onClick={() => onPage(0)} aria-label="pierwsza strona">
        «
      </button>
      <button disabled={page === 0} onClick={() => onPage(page - 1)}>
        ‹ poprzednia
      </button>
      <span className="pager-jump">
        strona
        <input
          type="number"
          min="1"
          max={totalPages}
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onBlur={commit}
          onKeyDown={(event) => {
            if (event.key === 'Enter') commit()
          }}
          aria-label="numer strony"
          data-testid="page-input"
        />
        / {totalPages}
      </span>
      <button disabled={page >= last} onClick={() => onPage(page + 1)}>
        następna ›
      </button>
      <button disabled={page >= last} onClick={() => onPage(last)} aria-label="ostatnia strona">
        »
      </button>
    </div>
  )
}
