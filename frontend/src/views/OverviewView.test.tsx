import { screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import OverviewView from './OverviewView'
import { overviewFixture } from '../test/fixtures'
import { jsonResponse, renderWithToasts } from '../test/renderWithToasts'

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn().mockResolvedValue(jsonResponse(overviewFixture))
  globalThis.fetch = fetchMock as unknown as typeof fetch
})

describe('OverviewView', () => {

  it('pobiera przegląd jednym wywołaniem i pokazuje liczby nagłówkowe', async () => {

    renderWithToasts(<OverviewView refreshKey={0} />)

    const headline = await screen.findByTestId('overview-headline')

    expect(within(headline).getByText('2500')).toBeInTheDocument()
    expect(within(headline).getByText('2310')).toBeInTheDocument()
    // 22 mln ms to nieco ponad sześć godzin grania
    expect(within(headline).getByText('6,1')).toBeInTheDocument()
    expect(within(headline).getByText('640')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/library/overview')
  })

  it('liczby nagłówkowe mówią o utworach, nie o playlistach ani setach', async () => {

    renderWithToasts(<OverviewView refreshKey={0} />)

    const headline = await screen.findByTestId('overview-headline')

    expect(within(headline).getByText('410')).toBeInTheDocument()
    expect(within(headline).getByText('3:51')).toBeInTheDocument()
    expect(within(headline).getByText('albumów')).toBeInTheDocument()
    expect(within(headline).queryByText(/playlist/i)).not.toBeInTheDocument()
    expect(within(headline).queryByText(/setami/i)).not.toBeInTheDocument()
  })

  it('nazywa braki po imieniu zamiast pokazywać samo „gotowość 80%"', async () => {

    renderWithToasts(<OverviewView refreshKey={0} />)

    const coverage = await screen.findByTestId('coverage')

    expect(within(coverage).getByText('metadane')).toBeInTheDocument()
    expect(within(coverage).getByText('analiza AI')).toBeInTheDocument()
    expect(within(coverage).getByText('metryki z pliku')).toBeInTheDocument()
    expect(screen.getByTestId('readiness')).toBeInTheDocument()
  })

  it('tłumaczy źródła BPM na słowa — ile faktu, ile estymaty (D19)', async () => {

    renderWithToasts(<OverviewView refreshKey={0} />)

    const sources = await screen.findByTestId('bpm-sources')

    expect(within(sources).getByText('pomiar z pliku')).toBeInTheDocument()
    expect(within(sources).getByText('estymata modelu')).toBeInTheDocument()
    expect(within(sources).getByText('bez tempa')).toBeInTheDocument()
  })

  it('wyciąga wnioski, a nie tylko rysuje słupki', async () => {

    renderWithToasts(<OverviewView refreshKey={0} />)

    const found = await screen.findByTestId('insights')

    expect(within(found).getByText('100–109 BPM')).toBeInTheDocument()
    expect(within(found).getByText('8A')).toBeInTheDocument()
    expect(within(found).getByText('Marc Anthony')).toBeInTheDocument()
  })

  it('rysuje komplet wykresów pulpitu', async () => {

    renderWithToasts(<OverviewView refreshKey={0} />)

    expect(await screen.findByTestId('bpm-histogram')).toBeInTheDocument()
    expect(screen.getByTestId('monthly-growth')).toBeInTheDocument()
    expect(screen.getByTestId('growth-cumulative')).toBeInTheDocument()
    expect(screen.getByTestId('camelot-wheel')).toBeInTheDocument()
    expect(screen.getByTestId('audio-profile')).toBeInTheDocument()
    expect(screen.getByTestId('tempo-energy')).toBeInTheDocument()
    expect(screen.getByTestId('top-artists')).toBeInTheDocument()
    expect(screen.getByTestId('top-albums')).toBeInTheDocument()
    expect(screen.getByTestId('top-tags')).toBeInTheDocument()
    expect(screen.getByTestId('recently-added')).toBeInTheDocument()
  })

  it('nie pokazuje niczego o playlistach ani generowaniu setów', async () => {

    const { container } = renderWithToasts(<OverviewView refreshKey={0} />)

    await screen.findByTestId('overview')

    expect(screen.queryByTestId('library-sources')).not.toBeInTheDocument()
    expect(container.textContent).not.toMatch(/playlist/i)
    // wszystkie przypadki „setu": set, setu, secie, setach, setami, sety, setów
    expect(container.textContent).not.toMatch(/\bset(y|u|ów|om|ami|ach)?\b|\bsecie\b/i)
    expect(container.textContent).not.toMatch(/wieczor/i)
  })

  it('macierz tempo × energia pokazuje skrzyżowanie obu wymiarów', async () => {

    renderWithToasts(<OverviewView refreshKey={0} />)

    const matrix = await screen.findByTestId('tempo-energy')

    // 700 utworów jest jednocześnie średnich tempem i wysokich energią
    expect(within(matrix).getByText('700')).toBeInTheDocument()
    expect(within(matrix).getByText('średnie')).toBeInTheDocument()
  })

  it('przy braku tonacji mówi o tym zamiast rysować puste koło', async () => {

    fetchMock.mockResolvedValue(jsonResponse({
      ...overviewFixture,
      sound: { ...overviewFixture.sound, camelotKeys: [{ label: 'BEZ TONACJI', count: 2500 }] },
    }))

    renderWithToasts(<OverviewView refreshKey={0} />)

    expect(await screen.findByText(/Żaden utwór nie ma jeszcze rozpoznanej tonacji/))
      .toBeInTheDocument()
  })

  it('gdy przegląd padnie, mówi o tym zamiast pokazywać puste wykresy', async () => {

    fetchMock.mockResolvedValue(jsonResponse({ errorCode: 'INTERNAL', message: 'padło' }, 500))

    renderWithToasts(<OverviewView refreshKey={0} />)

    expect(await screen.findByText(/chwilowo niedostępny/)).toBeInTheDocument()
  })
})
