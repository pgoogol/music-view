import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { aPage } from './test/fixtures'
import { jsonResponse } from './test/renderWithToasts'
import { render } from '@testing-library/react'

beforeEach(() => {
  // każdy widok startuje od pobrania danych — pusta biblioteka wystarczy do nawigacji
  globalThis.fetch = vi.fn().mockImplementation((url: string) =>
    Promise.resolve(jsonResponse(String(url).includes('/api/catalog') ? aPage([]) : [])),
  ) as unknown as typeof fetch
})

describe('App', () => {

  it('startuje na bibliotece', async () => {

    render(<App />)

    expect(await screen.findByRole('region', { name: 'Biblioteka' })).toBeInTheDocument()
  })

  it('przełącza widok zakładką i zapisuje go w adresie', async () => {

    const user = userEvent.setup()
    render(<App />)
    await screen.findByRole('region', { name: 'Biblioteka' })

    await user.click(screen.getByTestId('tab-sets'))

    expect(await screen.findByRole('region', { name: 'Sety' })).toBeInTheDocument()
    await waitFor(() => expect(window.location.hash).toBe('#/sets'))
  })

  it('otwiera widok wskazany w adresie po odświeżeniu strony', async () => {

    window.location.hash = '#/import'

    render(<App />)

    expect(await screen.findByRole('region', { name: 'Import' })).toBeInTheDocument()
  })
})
