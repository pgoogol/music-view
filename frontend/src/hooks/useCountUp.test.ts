import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useCountUp } from './useCountUp'

/** Domyślny stub z setup.ts deklaruje ograniczony ruch; tu go czasem odkręcamy. */
function allowMotion(reduced: boolean) {
  window.matchMedia = ((query: string) => ({
    matches: reduced && query.includes('prefers-reduced-motion'),
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  })) as unknown as typeof window.matchMedia
}

afterEach(() => {
  allowMotion(true)
  vi.useRealTimers()
})

describe('useCountUp', () => {

  it('przy ograniczonym ruchu podaje wartość docelową od razu', () => {

    allowMotion(true)

    const { result } = renderHook(() => useCountUp(2500))

    expect(result.current).toBe(2500)
  })

  it('animując, startuje od zera i dochodzi do wartości docelowej', async () => {

    allowMotion(false)
    const frames: FrameRequestCallback[] = []
    const requestFrame = vi
      .spyOn(globalThis, 'requestAnimationFrame')
      .mockImplementation((callback: FrameRequestCallback) => {
        frames.push(callback)
        return frames.length
      })

    const { result } = renderHook(() => useCountUp(100, 1000))

    expect(result.current).toBe(0)

    // pierwsza klatka po połowie czasu — licznik jest w drodze, ale jeszcze nie u celu
    act(() => frames[0](performance.now() + 500))
    expect(result.current).toBeGreaterThan(0)
    expect(result.current).toBeLessThan(100)

    // klatka po upływie całego czasu domyka odliczanie dokładnie na wartości
    act(() => frames[frames.length - 1](performance.now() + 2000))
    expect(result.current).toBe(100)

    requestFrame.mockRestore()
  })
})
