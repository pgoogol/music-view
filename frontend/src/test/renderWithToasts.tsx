import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { ToastProvider } from '../components/Toasts'

/** Widoki zgłaszają błędy przez kontekst toastów — testy muszą go dostarczyć. */
export function renderWithToasts(ui: ReactElement) {
  return render(<ToastProvider>{ui}</ToastProvider>)
}

/** Odpowiedź `fetch` w kształcie, którego oczekuje klient API (api.ts). */
export function jsonResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: 'OK',
    json: async () => body,
  } as Response
}
