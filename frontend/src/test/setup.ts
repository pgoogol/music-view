import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach } from 'vitest'

// jsdom nie zna `matchMedia`, a animacje przeglądu pytają o nią przed startem
// (M5.4). Stub deklaruje ograniczony ruch: liczniki i wykresy pokazują wartości
// docelowe od razu, więc asercje nie ścigają się z klatkami animacji.
window.matchMedia = ((query: string) => ({
  matches: query.includes('prefers-reduced-motion'),
  media: query,
  onchange: null,
  addEventListener: () => {},
  removeEventListener: () => {},
  addListener: () => {},
  removeListener: () => {},
  dispatchEvent: () => false,
})) as unknown as typeof window.matchMedia

beforeEach(() => {
  // każdy test startuje z czystego adresu — widoki trzymają stan w hashu
  window.location.hash = ''
})

afterEach(cleanup)
