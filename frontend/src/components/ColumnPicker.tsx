// Wybierak kolumn tabeli biblioteki (M5.6). Zestaw siedzi w adresie razem
// z filtrami, więc „mój widok do układania wesela" jest linkiem, a nie ustawieniem
// schowanym w przeglądarce.

import { useEffect, useRef, useState } from 'react'
import { DEFAULT_COLUMN_KEYS, LIBRARY_COLUMNS } from '../library/columns'

interface Props {
  selected: readonly string[]
  onChange: (keys: string[]) => void
}

export default function ColumnPicker({ selected, onChange }: Props) {

  const [open, setOpen] = useState(false)
  const box = useRef<HTMLDivElement>(null)

  // klik poza panelem zamyka go — inaczej lista kolumn zasłania tabelę,
  // której dotyczy, dopóki ktoś nie trafi ponownie w przycisk
  useEffect(() => {
    if (!open) return
    const close = (event: MouseEvent) => {
      if (!box.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])

  const toggle = (key: string) => {
    const next = selected.includes(key)
      ? selected.filter((current) => current !== key)
      : [...selected, key]
    onChange(next)
  }

  return (
    <div className="column-picker" ref={box}>
      <button
        type="button"
        className="link"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
        data-testid="column-picker-toggle"
      >
        kolumny ({selected.length})
      </button>
      {open && (
        <div className="column-picker-panel" data-testid="column-picker-panel">
          {LIBRARY_COLUMNS.map((column) => (
            <label key={column.key} className="filter-check">
              <input
                type="checkbox"
                checked={selected.includes(column.key)}
                disabled={column.fixed}
                onChange={() => toggle(column.key)}
              />
              <span>{column.label}</span>
            </label>
          ))}
          <button
            type="button"
            className="link"
            onClick={() => onChange([...DEFAULT_COLUMN_KEYS])}
            data-testid="columns-reset"
          >
            przywróć domyślne
          </button>
        </div>
      )}
    </div>
  )
}
