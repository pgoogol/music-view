import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SetGeneratorPanel from './SetGeneratorPanel'
import { aTrack } from '../test/fixtures'
import { jsonResponse, renderWithToasts } from '../test/renderWithToasts'

const proposal = {
  trackCount: 2,
  totalDurationMs: 420_000,
  targetDurationMs: 3_600_000,
  seed: 4242,
  notes: ['Set jest krótszy od zamówionego (7 z 60 min) — pula kandydatów się skończyła'],
  tracks: [
    { position: 0, djSlot: 'WARMUP', track: aTrack({ spotifyId: 'sp-1', title: 'Rozgrzewka' }) },
    { position: 1, djSlot: 'PEAK', track: aTrack({ spotifyId: 'sp-2', title: 'Szczyt' }) },
  ],
}

let fetchMock: ReturnType<typeof vi.fn>

function bodyOf(call: unknown[]): Record<string, unknown> {
  return JSON.parse(String((call[1] as RequestInit).body))
}

function proposeCalls() {
  return fetchMock.mock.calls.filter((call) => String(call[0]).includes('/api/sets/propose'))
}

beforeEach(() => {
  fetchMock = vi.fn().mockImplementation((url: string) => {
    const target = String(url)
    if (target.includes('/api/sets/propose')) return Promise.resolve(jsonResponse(proposal))
    if (target.includes('/api/playlists/')) return Promise.resolve(jsonResponse({}))
    return Promise.resolve(jsonResponse({ id: 9, name: 'Propozycja', trackCount: 0 }))
  })
  globalThis.fetch = fetchMock as unknown as typeof fetch
})

describe('SetGeneratorPanel', () => {

  it('pokazuje propozycję z notatkami i nie zapisuje niczego z automatu', async () => {

    const user = userEvent.setup()
    renderWithToasts(<SetGeneratorPanel onCreated={vi.fn()} />)

    await user.click(screen.getByTestId('propose-set'))

    expect(await screen.findByTestId('set-proposal')).toBeInTheDocument()
    expect(screen.getByText('Rozgrzewka')).toBeInTheDocument()
    expect(screen.getByTestId('proposal-notes')).toHaveTextContent('krótszy')
    expect(fetchMock.mock.calls.some((call) => String(call[0]).match(/\/api\/playlists$/))).toBe(
      false,
    )
  })

  it('wysyła długość i filtry puli w żądaniu', async () => {

    const user = userEvent.setup()
    renderWithToasts(<SetGeneratorPanel onCreated={vi.fn()} />)

    await user.selectOptions(screen.getByLabelText('długość setu'), '180')
    await user.selectOptions(screen.getByLabelText('gatunek puli'), 'LATIN')
    await user.selectOptions(screen.getByLabelText('ocena co najmniej w puli'), '4')
    await user.click(screen.getByTestId('propose-set'))

    await waitFor(() => expect(proposeCalls()).toHaveLength(1))
    expect(bodyOf(proposeCalls()[0])).toMatchObject({
      targetMinutes: 180,
      genreFamily: 'LATIN',
      ratingMin: 4,
      inLibrary: true,
    })
  })

  it('„powtórz ten układ" wysyła seed z poprzedniej propozycji, a „spróbuj inaczej" nie', async () => {

    const user = userEvent.setup()
    renderWithToasts(<SetGeneratorPanel onCreated={vi.fn()} />)
    await user.click(screen.getByTestId('propose-set'))
    await screen.findByTestId('set-proposal')

    await user.click(screen.getByTitle('ten sam seed daje tę samą propozycję'))
    await waitFor(() => expect(proposeCalls()).toHaveLength(2))
    expect(bodyOf(proposeCalls()[1])).toMatchObject({ seed: 4242 })

    await user.click(screen.getByTestId('reroll-set'))
    await waitFor(() => expect(proposeCalls()).toHaveLength(3))
    expect(bodyOf(proposeCalls()[2]).seed).toBeUndefined()
  })

  it('profil wieczoru jedzie w żądaniu; domyślnie standardowy', async () => {

    const user = userEvent.setup()
    renderWithToasts(<SetGeneratorPanel onCreated={vi.fn()} />)

    await user.click(screen.getByTestId('propose-set'))
    await waitFor(() => expect(proposeCalls()).toHaveLength(1))
    expect(bodyOf(proposeCalls()[0]).curve).toBe('STANDARD')

    await user.selectOptions(screen.getByLabelText('profil wieczoru'), 'WEDDING')
    await user.click(screen.getByTestId('propose-set'))

    await waitFor(() => expect(proposeCalls()).toHaveLength(2))
    expect(bodyOf(proposeCalls()[1]).curve).toBe('WEDDING')
  })

  it('zapis zakłada playlistę i dokłada utwory istniejącą drogą', async () => {

    const user = userEvent.setup()
    const onCreated = vi.fn()
    renderWithToasts(<SetGeneratorPanel onCreated={onCreated} />)
    await user.click(screen.getByTestId('propose-set'))
    await screen.findByTestId('set-proposal')

    await user.click(screen.getByTestId('materialize-set'))

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(9))
    const addCalls = fetchMock.mock.calls.filter((call) =>
      String(call[0]).includes('/api/playlists/9/tracks'),
    )
    expect(addCalls).toHaveLength(2)
  })
})
