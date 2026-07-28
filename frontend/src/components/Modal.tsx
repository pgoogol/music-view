// Okno modalne (M3.2) — podsumowania, których nie wolno przegapić (import
// własnych playlist trwa i kończy się raportem; toast znika po kilku sekundach).
// Zamykanie: przycisk, Escape i kliknięcie w tło.

import { useEffect, useRef, type ReactNode } from 'react'

interface Props {
  title: string
  onClose: () => void
  children: ReactNode
  testId?: string
}

export default function Modal({ title, onClose, children, testId }: Props) {

  const closeButton = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    closeButton.current?.focus()
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(event) => event.stopPropagation()}
        data-testid={testId}
      >
        <h2>{title}</h2>
        {children}
        <div className="modal-foot">
          <button ref={closeButton} onClick={onClose} data-testid="modal-close">
            Zamknij
          </button>
        </div>
      </div>
    </div>
  )
}
