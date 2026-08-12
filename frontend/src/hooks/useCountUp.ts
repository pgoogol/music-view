// Odliczanie liczby od zera do wartości docelowej (M5.4) — „nabijanie się"
// licznika na pulpicie. Ruch jest dekoracją, więc przy `prefers-reduced-motion`
// wartość pojawia się od razu; tak samo w testach, gdzie stub `matchMedia`
// deklaruje ograniczony ruch i asercje nie muszą czekać na klatki.

import { useEffect, useRef, useState } from 'react'

const DURATION_MS = 900

export function prefersReducedMotion(): boolean {

  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false
  }
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/** Wygładzenie: szybki start, miękkie dojście do wartości (ease-out cubic). */
function easeOut(progress: number): number {
  return 1 - Math.pow(1 - progress, 3)
}

export function useCountUp(target: number, durationMs = DURATION_MS): number {

  const [value, setValue] = useState(() => (prefersReducedMotion() ? target : 0))
  const frame = useRef(0)

  useEffect(() => {
    if (prefersReducedMotion() || durationMs <= 0) {
      setValue(target)
      return
    }
    const startedAt = performance.now()
    const step = (now: number) => {
      const progress = Math.min(1, (now - startedAt) / durationMs)
      setValue(target * easeOut(progress))
      if (progress < 1) {
        frame.current = requestAnimationFrame(step)
      }
    }
    frame.current = requestAnimationFrame(step)
    return () => cancelAnimationFrame(frame.current)
  }, [target, durationMs])

  return value
}
