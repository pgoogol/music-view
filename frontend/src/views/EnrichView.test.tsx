import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import EnrichView from './EnrichView'
import type { EnrichmentEstimateResponse } from '../api'
import { aPage } from '../test/fixtures'
import { jsonResponse, renderWithToasts } from '../test/renderWithToasts'

let fetchMock: ReturnType<typeof vi.fn>
let estimate: EnrichmentEstimateResponse

function estimateCalls() {
  return fetchMock.mock.calls.filter((call) => String(call[0]).includes('/api/enrich/estimate'))
}

function lastEstimateBody(): Record<string, unknown> {
  const call = estimateCalls().at(-1)!
  return JSON.parse(String((call[1] as RequestInit).body))
}

beforeEach(() => {
  estimate = { trackCount: 340, aiTracks: 340, estimatedCost: 0.2516, limit: 500, withinLimit: true }
  fetchMock = vi.fn().mockImplementation((url: string) => {
    const target = String(url)
    if (target.includes('/api/enrich/estimate')) return Promise.resolve(jsonResponse(estimate))
    if (target.includes('/api/enrich/missing-count')) {
      return Promise.resolve(jsonResponse({ metadata: 4, audio: 380, ai: 340 }))
    }
    if (target.includes('/api/enrich/jobs')) return Promise.resolve(jsonResponse([]))
    return Promise.resolve(jsonResponse(aPage([], { totalElements: 2500 })))
  })
  globalThis.fetch = fetchMock as unknown as typeof fetch
})

describe('EnrichView — szacunek przed startem (M5.1)', () => {

  it('pokazuje liczbę utworów i koszt zanim job wystartuje', async () => {

    renderWithToasts(<EnrichView selectedIds={new Set()} onJobFinished={vi.fn()} />)

    const box = await screen.findByTestId('enrich-estimate')

    await waitFor(() => expect(box).toHaveTextContent('340'))
    expect(box).toHaveTextContent('$0.2516')
    expect(fetchMock.mock.calls.some((call) => String(call[0]).match(/\/api\/enrich$/))).toBe(false)
  })

  it('gdy brak stawek w konfiguracji, mówi „nieznany" zamiast zera', async () => {

    estimate = { ...estimate, estimatedCost: null }

    renderWithToasts(<EnrichView selectedIds={new Set()} onJobFinished={vi.fn()} />)

    const box = await screen.findByTestId('enrich-estimate')
    await waitFor(() => expect(box).toHaveTextContent('nieznany'))
    expect(box).toHaveTextContent('llm.cost.input-per-1m')
  })

  it('zlecenie ponad limit blokuje start i mówi, jak je odblokować', async () => {

    estimate = { trackCount: 2500, aiTracks: 2500, estimatedCost: 1.85, limit: 500, withinLimit: false }

    renderWithToasts(<EnrichView selectedIds={new Set()} onJobFinished={vi.fn()} />)

    const box = await screen.findByTestId('enrich-estimate')

    await waitFor(() => expect(box).toHaveTextContent('do modelu idzie 2500 przy limicie 500'))
    expect(box).toHaveTextContent('Odznacz grupę')
    expect(screen.getByTestId('enrich-start')).toBeDisabled()
  })

  it('zlecenie bez grupy AI nie ma sufitu, choćby obejmowało cały katalog (D37)', async () => {

    // 2500 utworów, zero płatnych — metadane i audio jadą z darmowych źródeł (D6)
    estimate = { trackCount: 2500, aiTracks: 0, estimatedCost: null, limit: 500, withinLimit: true }

    renderWithToasts(<EnrichView selectedIds={new Set()} onJobFinished={vi.fn()} />)

    const box = await screen.findByTestId('enrich-estimate')

    await waitFor(() => expect(box).toHaveTextContent('bez kosztu i bez limitu'))
    expect(box).not.toHaveTextContent('job nie wystartuje')
    expect(screen.getByTestId('enrich-start')).toBeEnabled()
  })

  it('zakres „do przeliczenia" wysyła OUTDATED i zawęża pola do samego AI', async () => {

    const user = userEvent.setup()
    renderWithToasts(<EnrichView selectedIds={new Set()} onJobFinished={vi.fn()} />)
    await screen.findByTestId('enrich-estimate')

    await user.click(screen.getByLabelText(/do przeliczenia/))

    await waitFor(() => expect(lastEstimateBody()).toMatchObject({ scope: 'OUTDATED', fields: ['AI'] }))
    expect(screen.getByLabelText('metadane (Spotify)')).toBeDisabled()
  })
})
