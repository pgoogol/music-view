import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PlaylistsView from './PlaylistsView'
import type { PlaylistRefreshStatusResponse } from '../api'
import { aPlaylist, aPlaylistSummary, aPlaylistTrack } from '../test/fixtures'
import { jsonResponse, renderWithToasts } from '../test/renderWithToasts'

const summaries = [
  aPlaylistSummary({ id: 1, name: 'Sabor Latino — piątek', trackCount: 3 }),
  aPlaylistSummary({ id: 2, name: 'Wesela 2026', spotifyPlaylistId: 'sp-1', trackCount: 1 }),
]

const detail = aPlaylist([
  aPlaylistTrack({ spotifyId: 'a', title: 'Vivir Mi Vida', artist: 'Marc Anthony', bpm: 92 }, 'WARMUP', 0),
  aPlaylistTrack({ spotifyId: 'b', title: 'La Gozadera', artist: 'Gente de Zona', bpm: 96 }, 'MIDDLE', 1),
  aPlaylistTrack({ spotifyId: 'c', title: 'Bailando', artist: 'Enrique Iglesias', bpm: 102 }, 'PEAK', 2),
])

/** Stan odświeżania w tle (M4.7/D35) — testy podmieniają tylko to, co badają. */
let refreshStatus: PlaylistRefreshStatusResponse = {
  outcome: 'REFRESHED',
  lastRunAt: '2026-08-11T20:15:00Z',
  refreshedPlaylists: 12,
  failedPlaylists: 0,
  message: null,
  intervalSeconds: 300,
}

let fetchMock: ReturnType<typeof vi.fn>

function listCalls() {
  return fetchMock.mock.calls.filter((call) => String(call[0]).endsWith('/api/playlists'))
}

beforeEach(() => {
  window.location.hash = '#/playlists'
  refreshStatus = {
    outcome: 'REFRESHED',
    lastRunAt: '2026-08-11T20:15:00Z',
    refreshedPlaylists: 12,
    failedPlaylists: 0,
    message: null,
    intervalSeconds: 300,
  }
  fetchMock = vi.fn().mockImplementation((url: string) => {
    const target = String(url)
    if (target.includes('/refresh-status')) return Promise.resolve(jsonResponse(refreshStatus))
    return Promise.resolve(jsonResponse(/\/api\/playlists\/\d+$/.test(target) ? detail : summaries))
  })
  globalThis.fetch = fetchMock as unknown as typeof fetch
})

describe('PlaylistsView', () => {

  it('wypisuje playlisty jako kafle i szuka po nazwie', async () => {

    const user = userEvent.setup()
    renderWithToasts(<PlaylistsView refreshKey={0} />)
    expect(await screen.findByTestId('playlist-card-1')).toBeInTheDocument()

    await user.type(screen.getByTestId('playlist-search'), 'wesela')

    expect(screen.getByTestId('playlist-card-2')).toBeInTheDocument()
    expect(screen.queryByTestId('playlist-card-1')).not.toBeInTheDocument()
  })

  it('wejście do playlisty zapisuje się w adresie i rysuje krzywą tempa', async () => {

    const user = userEvent.setup()
    renderWithToasts(<PlaylistsView refreshKey={0} />)

    await user.click(await screen.findByTestId('playlist-card-1'))

    expect(await screen.findByTestId('bpm-curve')).toBeInTheDocument()
    await waitFor(() => expect(window.location.hash).toContain('pl=1'))
  })

  it('utwory idą do zwijanych sekcji faz wieczoru', async () => {

    const user = userEvent.setup()
    window.location.hash = '#/playlists?pl=1'
    renderWithToasts(<PlaylistsView refreshKey={0} />)

    const peak = await screen.findByTestId('section-PEAK')
    expect(within(peak).getByText(/Bailando/)).toBeInTheDocument()

    await user.click(within(peak).getByRole('button', { name: /szczyt/ }))

    expect(within(peak).queryByText(/Bailando/)).not.toBeInTheDocument()
  })

  it('szukanie w środku zawęża sekcje i podaje liczbę trafień', async () => {

    const user = userEvent.setup()
    window.location.hash = '#/playlists?pl=1'
    renderWithToasts(<PlaylistsView refreshKey={0} />)
    await screen.findByTestId('section-PEAK')

    await user.type(screen.getByTestId('track-search'), 'gozadera')

    expect(await screen.findByTestId('section-MIDDLE')).toBeInTheDocument()
    expect(screen.queryByTestId('section-PEAK')).not.toBeInTheDocument()
    expect(screen.getByTestId('track-search-summary')).toHaveTextContent('pasuje 1 z 3')
  })

  it('gdy nic nie pasuje, mówi o tym zamiast pokazywać pustą playlistę', async () => {

    const user = userEvent.setup()
    window.location.hash = '#/playlists?pl=1'
    renderWithToasts(<PlaylistsView refreshKey={0} />)
    await screen.findByTestId('section-PEAK')

    await user.type(screen.getByTestId('track-search'), 'techno')

    expect(await screen.findByTestId('no-track-hits')).toBeInTheDocument()
  })
})

describe('PlaylistsView — odświeżanie w tle (M4.7)', () => {

  it('pokazuje, kiedy backend ostatnio odświeżył playlisty', async () => {

    renderWithToasts(<PlaylistsView refreshKey={0} />)

    expect(await screen.findByTestId('playlist-refresh-status')).toHaveTextContent(
      /Odświeżono automatycznie/,
    )
    expect(screen.getByTestId('playlist-refresh-status')).toHaveTextContent('playlist: 12')
  })

  it('brak połączonego konta opisuje jako oczekiwanie, nie awarię', async () => {

    refreshStatus = { ...refreshStatus, outcome: 'SKIPPED_NOT_CONNECTED', lastRunAt: null }

    renderWithToasts(<PlaylistsView refreshKey={0} />)

    expect(await screen.findByTestId('playlist-refresh-status')).toHaveTextContent(
      /czeka na połączenie/,
    )
  })

  it('po przebiegu w tle lista przeładowuje się sama', async () => {

    vi.useFakeTimers()
    try {
      renderWithToasts(<PlaylistsView refreshKey={0} />)
      await act(async () => await vi.advanceTimersByTimeAsync(0))
      expect(listCalls()).toHaveLength(1)

      // backend zdążył odświeżyć playlisty — zmiana znacznika ma pociągnąć nowy odczyt
      refreshStatus = { ...refreshStatus, lastRunAt: '2026-08-11T20:20:00Z' }
      await act(async () => await vi.advanceTimersByTimeAsync(60_000))

      expect(listCalls()).toHaveLength(2)
    } finally {
      vi.useRealTimers()
    }
  })
})
