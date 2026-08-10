import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SetsView from './SetsView'
import { aPlaylist, aPlaylistSummary, aPlaylistTrack, aTrack } from '../test/fixtures'
import { jsonResponse, renderWithToasts } from '../test/renderWithToasts'

const summaries = [aPlaylistSummary({ id: 1, name: 'Sabor Latino — piątek', trackCount: 3 })]

const detail = aPlaylist([
  aPlaylistTrack({ spotifyId: 'a', title: 'Vivir Mi Vida', bpm: 92 }, 'WARMUP', 0),
  aPlaylistTrack({ spotifyId: 'b', title: 'La Gozadera', bpm: 96 }, 'MIDDLE', 1),
  aPlaylistTrack({ spotifyId: 'c', title: 'Bailando', bpm: 102 }, 'PEAK', 2),
])

/** Skład po dopisaniu dobranego utworu — API zawsze dokłada go na koniec. */
const afterAdd = aPlaylist(
  [
    ...detail.tracks,
    aPlaylistTrack({ spotifyId: 'nowy', title: 'Dobrany', bpm: 98 }, 'MIDDLE', 3),
  ],
  { version: 1 },
)

/** Backend rozwija brak pozycji na koniec setu i odsyła ją w odpowiedzi. */
function suggestionsFor(position: number) {
  return {
    position,
    suggestions: [
      {
        djSlot: 'MIDDLE',
        bpmDelta: 2,
        harmonic: true,
        track: aTrack({ spotifyId: 'nowy', title: 'Dobrany', camelot: '8A' }),
      },
    ],
  }
}

let fetchMock: ReturnType<typeof vi.fn>

function callsTo(fragment: string) {
  return fetchMock.mock.calls.filter((call) => String(call[0]).includes(fragment))
}

function bodyOf(call: unknown[]): Record<string, unknown> {
  return JSON.parse(String((call[1] as RequestInit).body))
}

beforeEach(() => {
  window.location.hash = '#/sets?set=1'
  fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
    const target = String(url)
    if (target.includes('/suggest')) {
      const requested = JSON.parse(String(init?.body)).position as number
      return Promise.resolve(jsonResponse(suggestionsFor(requested)))
    }
    if (target.includes('/tracks') && init?.method === 'POST') {
      return Promise.resolve(jsonResponse(afterAdd))
    }
    if (target.includes('/tracks') && init?.method === 'PUT') {
      return Promise.resolve(jsonResponse(afterAdd))
    }
    if (/\/api\/playlists\/\d+$/.test(target)) return Promise.resolve(jsonResponse(detail))
    return Promise.resolve(jsonResponse(summaries))
  })
  globalThis.fetch = fetchMock as unknown as typeof fetch
})

describe('SetsView — domykanie setu (M4.4)', () => {

  it('„dobierz" przy utworze pyta o kandydatów na miejsce zaraz po nim', async () => {

    renderWithToasts(<SetsView selectedIds={new Set()} onSelectionUsed={vi.fn()} />)

    await userEvent.click(
      await screen.findByLabelText('dobierz utwór po: La Gozadera'),
    )

    await waitFor(() => expect(callsTo('/suggest')).toHaveLength(1))
    expect(bodyOf(callsTo('/suggest')[0]).position).toBe(2)
    expect(await screen.findByTestId('set-suggestions')).toBeInTheDocument()
    expect(screen.getByText('Kandydaci na miejsce 3')).toBeInTheDocument()
  })

  it('wstawienie w środek dopisuje utwór i zaraz poprawia kolejność', async () => {

    renderWithToasts(<SetsView selectedIds={new Set()} onSelectionUsed={vi.fn()} />)
    await userEvent.click(await screen.findByLabelText('dobierz utwór po: La Gozadera'))
    await screen.findByTestId('set-suggestions')

    await userEvent.click(screen.getByLabelText('wstaw na miejsce 3: Dobrany'))

    await waitFor(() => expect(callsTo('/tracks').length).toBeGreaterThanOrEqual(2))
    const reorder = callsTo('/tracks').find(
      (call) => (call[1] as RequestInit).method === 'PUT',
    )
    expect(reorder).toBeDefined()
    expect(bodyOf(reorder!).spotifyIds).toEqual(['a', 'b', 'nowy', 'c'])
    // wersja z odpowiedzi na dopisanie, nie z widoku sprzed zmiany (D29)
    expect(bodyOf(reorder!).version).toBe(1)
    expect(screen.queryByTestId('set-suggestions')).not.toBeInTheDocument()
  })

  it('dobieranie na koniec obywa się bez zmiany kolejności', async () => {

    renderWithToasts(<SetsView selectedIds={new Set()} onSelectionUsed={vi.fn()} />)

    await userEvent.click(await screen.findByTestId('playlist-suggest-end'))
    await screen.findByTestId('set-suggestions')
    expect(bodyOf(callsTo('/suggest')[0]).position).toBe(3)

    await userEvent.click(screen.getByLabelText('wstaw na miejsce 4: Dobrany'))

    await waitFor(() =>
      expect(callsTo('/tracks').some((call) => (call[1] as RequestInit).method === 'POST'))
        .toBe(true),
    )
    expect(callsTo('/tracks').some((call) => (call[1] as RequestInit).method === 'PUT')).toBe(false)
  })
})
