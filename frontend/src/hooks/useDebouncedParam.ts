// Pole tekstowe filtra sprzężone z adresem (M3.1, wydzielone w M5.6): adres jest
// źródłem prawdy, ale wpisywanie trafia tam dopiero po chwili bezczynności —
// inaczej każda litera to nowe zapytanie i nowy wpis w historii przeglądarki.

import { useEffect, useRef, useState } from 'react'

export const SEARCH_DEBOUNCE_MS = 300

export function useDebouncedParam(value: string, push: (next: string) => void) {

  const [draft, setDraft] = useState(value)
  const lastPushed = useRef(value)

  // zmiana z zewnątrz (wyczyszczenie filtrów, wklejony link) dogania pole
  useEffect(() => {
    if (value !== lastPushed.current) {
      lastPushed.current = value
      setDraft(value)
    }
  }, [value])

  useEffect(() => {
    if (draft === value) return
    const timer = window.setTimeout(() => {
      lastPushed.current = draft
      push(draft)
    }, SEARCH_DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [draft, value, push])

  return [draft, setDraft] as const
}
