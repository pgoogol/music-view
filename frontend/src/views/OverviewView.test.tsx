import { screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import OverviewView from './OverviewView'
import { jsonResponse, renderWithToasts } from '../test/renderWithToasts'

const overview = {
  catalogTracks: 2500,
  libraryTracks: 2310,
  tracksWithMetrics: 120,
  metadataMissing: 4,
  audioMissing: 380,
  aiMissing: 90,
  genres: [
    { label: 'LATIN', count: 1500 },
    { label: 'BEZ GATUNKU', count: 100 },
  ],
  tempoClasses: [{ label: 'MEDIUM', count: 900 }],
  energies: [{ label: 'high', count: 1200 }],
  bpmSources: [
    { label: 'MANUAL', count: 120 },
    { label: 'DEEZER', count: 900 },
    { label: 'LLM', count: 1100 },
    { label: 'BRAK BPM', count: 380 },
  ],
  ratings: [{ label: 'bez oceny', count: 2000 }],
  bpmHistogram: [
    { label: '90–99', count: 200 },
    { label: '100–109', count: 400 },
  ],
  topArtists: [{ label: 'Marc Anthony', count: 42 }],
  monthlyGrowth: [{ label: '2026-07', count: 300 }],
}

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn().mockResolvedValue(jsonResponse(overview))
  globalThis.fetch = fetchMock as unknown as typeof fetch
})

describe('OverviewView', () => {

  it('pobiera przegląd jednym wywołaniem i pokazuje liczby nagłówkowe', async () => {

    renderWithToasts(<OverviewView refreshKey={0} />)

    const headline = await screen.findByTestId('overview-headline')

    expect(within(headline).getByText('2500')).toBeInTheDocument()
    expect(within(headline).getByText('2310')).toBeInTheDocument()
    expect(within(headline).getByText('120')).toBeInTheDocument()
    // opisane przez AI = katalog minus braki grupy AI
    expect(within(headline).getByText('2410')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/library/overview')
  })

  it('pokazuje udział źródeł BPM — ile faktu, ile estymaty (D19)', async () => {

    renderWithToasts(<OverviewView refreshKey={0} />)

    const sources = await screen.findByTestId('bpm-sources')

    expect(within(sources).getByText('MANUAL')).toBeInTheDocument()
    expect(within(sources).getByText('LLM')).toBeInTheDocument()
    expect(within(sources).getByText('BRAK BPM')).toBeInTheDocument()
  })

  it('rysuje histogram BPM i przyrost jako wykresy SVG', async () => {

    renderWithToasts(<OverviewView refreshKey={0} />)

    expect(await screen.findByTestId('bpm-histogram')).toBeInTheDocument()
    expect(screen.getByTestId('monthly-growth')).toBeInTheDocument()
  })

  it('gdy przegląd padnie, mówi o tym zamiast pokazywać puste wykresy', async () => {

    fetchMock.mockResolvedValue(jsonResponse({ errorCode: 'INTERNAL', message: 'padło' }, 500))

    renderWithToasts(<OverviewView refreshKey={0} />)

    expect(await screen.findByText(/chwilowo niedostępny/)).toBeInTheDocument()
  })
})
