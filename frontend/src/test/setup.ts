import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach } from 'vitest'

beforeEach(() => {
  // każdy test startuje z czystego adresu — widoki trzymają stan w hashu
  window.location.hash = ''
})

afterEach(cleanup)
