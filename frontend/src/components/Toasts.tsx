// Jeden punkt zgłaszania sukcesów i błędów (M3.1) — wcześniej każdy panel
// trzymał własne `error`/`report` i komunikat ginął po zmianie widoku.

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { ApiError } from '../api'

export type ToastKind = 'success' | 'error' | 'info'

export interface Toast {
  id: number
  kind: ToastKind
  message: string
}

interface ToastApi {
  notify: (message: string, kind?: ToastKind) => void
  /** Błąd API pokazujemy z kodem — po nim rozpoznaje się przyczynę w logach backendu. */
  reportError: (error: unknown, fallback?: string) => void
}

const AUTO_DISMISS_MS = 6000

const ToastContext = createContext<ToastApi | null>(null)

export function describeError(error: unknown, fallback = 'Nie udało się wykonać operacji'): string {

  if (error instanceof ApiError) return `${error.errorCode}: ${error.message}`
  if (error instanceof Error) return error.message || fallback
  return fallback
}

export function ToastProvider({ children }: { children: ReactNode }) {

  const [toasts, setToasts] = useState<Toast[]>([])
  const nextId = useRef(1)
  const timers = useRef<number[]>([])

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }, [])

  const notify = useCallback(
    (message: string, kind: ToastKind = 'success') => {
      const id = nextId.current++
      setToasts((current) => [...current, { id, kind, message }])
      timers.current.push(window.setTimeout(() => dismiss(id), AUTO_DISMISS_MS))
    },
    [dismiss],
  )

  const reportError = useCallback(
    (error: unknown, fallback?: string) => notify(describeError(error, fallback), 'error'),
    [notify],
  )

  useEffect(() => () => timers.current.forEach(window.clearTimeout), [])

  const api = useMemo<ToastApi>(() => ({ notify, reportError }), [notify, reportError])

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-host" role="status" aria-live="polite" data-testid="toast-host">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast toast-${toast.kind}`}>
            <span>{toast.message}</span>
            <button className="link toast-close" onClick={() => dismiss(toast.id)} aria-label="zamknij">
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastApi {

  const api = useContext(ToastContext)
  if (!api) throw new Error('useToast wymaga ToastProvider')
  return api
}
