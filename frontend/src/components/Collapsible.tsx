// Zwijana sekcja (M3.2) — playlista na 200 utworów rozbita na fazy wieczoru
// mieści się na ekranie dopiero wtedy, gdy da się ją pozwijać.

import { useId, useState, type ReactNode } from 'react'

interface Props {
  title: ReactNode
  /** Licznik po prawej — ile pozycji kryje sekcja (widoczny także po zwinięciu). */
  count?: ReactNode
  defaultOpen?: boolean
  children: ReactNode
  testId?: string
}

export default function Collapsible({
  title,
  count,
  defaultOpen = true,
  children,
  testId,
}: Props) {

  const [open, setOpen] = useState(defaultOpen)
  const bodyId = useId()

  return (
    <section className="collapsible" data-testid={testId}>
      <button
        className="collapsible-head"
        onClick={() => setOpen((current) => !current)}
        aria-expanded={open}
        aria-controls={bodyId}
      >
        <span className="collapsible-mark" aria-hidden="true">
          {open ? '▾' : '▸'}
        </span>
        {title}
        {count !== undefined && <span className="collapsible-count">{count}</span>}
      </button>
      {open && (
        <div className="collapsible-body" id={bodyId}>
          {children}
        </div>
      )}
    </section>
  )
}
