import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SetFillPanel from './SetFillPanel'
import { aTrack } from '../test/fixtures'
import { jsonResponse, renderWithToasts } from '../test/renderWithToasts'

const fill = {
  currentTrackCount: 2,
  currentDurationMs: 420_000,
  addedTrackCount: 2,
  totalDurationMs: 1_260_000,
  targetDurationMs: 10_800_000,
  seed: 4242,
  notes: ['Set jest krótszy od zamówionego (21 z 180 min) — pula kandydatów się skończyła'],
  tracks: [
    { position: 2, djSlot: 'PEAK', track: aTrack({ spotifyId: 'sp-3', title: 'Szczyt' }) },
    { position: 3, djSlot: 'CLOSING', track: aTrack({ spotifyId: 'sp-4', title: 'Zamknięcie' }) },
  ],
}

let fetchMock: ReturnType<typeof vi.fn>

function fillCalls() {
  return fetchMock.mock.calls.filter((call) => String(call[0]).includes('/fill'))
}

function bodyOf(call: unknown[]): Record<string, unknown> {
  return JSON.parse(String((call[1] as RequestInit).body))
}

beforeEach(() => {
  fetchMock = vi.fn().mockResolvedValue(jsonResponse(fill))
  globalThis.fetch = fetchMock as unknown as typeof fetch
})

describe('SetFillPanel', () => {

  it('pokazuje dalszy ciąg setu wraz z notatkami, zanim cokolwiek dopisze', async () => {
    const onAppend = vi.fn().mockResolvedValue(undefined)
    renderWithToasts(<SetFillPanel playlistId={7} disabled={false} onAppend={onAppend} />)

    await userEvent.click(screen.getByTestId('fill-set'))

    await waitFor(() => expect(screen.getByTestId('fill-preview')).toBeInTheDocument())
    expect(screen.getByText('Szczyt')).toBeInTheDocument()
    expect(screen.getByTestId('fill-notes')).toHaveTextContent('krótszy')
    expect(onAppend).not.toHaveBeenCalled()
  })

  it('dopisuje utwory dopiero po kliknięciu i czyści podgląd', async () => {
    const onAppend = vi.fn().mockResolvedValue(undefined)
    renderWithToasts(<SetFillPanel playlistId={7} disabled={false} onAppend={onAppend} />)

    await userEvent.click(screen.getByTestId('fill-set'))
    await waitFor(() => expect(screen.getByTestId('fill-preview')).toBeInTheDocument())
    await userEvent.click(screen.getByTestId('fill-append'))

    await waitFor(() => expect(onAppend).toHaveBeenCalledWith(['sp-3', 'sp-4']))
    expect(screen.queryByTestId('fill-preview')).not.toBeInTheDocument()
  })

  it('„powtórz ten układ" odsyła seed z poprzedniej odpowiedzi', async () => {
    renderWithToasts(<SetFillPanel playlistId={7} disabled={false} onAppend={vi.fn()} />)

    await userEvent.click(screen.getByTestId('fill-set'))
    await waitFor(() => expect(screen.getByTestId('fill-preview')).toBeInTheDocument())
    await userEvent.click(screen.getByTitle('ten sam seed daje ten sam dalszy ciąg'))

    await waitFor(() => expect(fillCalls()).toHaveLength(2))
    expect(bodyOf(fillCalls()[0]).seed).toBeUndefined()
    expect(bodyOf(fillCalls()[1]).seed).toBe(4242)
  })

  it('woła endpoint otwartego setu z długością całego wieczoru i profilem', async () => {
    renderWithToasts(<SetFillPanel playlistId={42} disabled={false} onAppend={vi.fn()} />)

    await userEvent.selectOptions(screen.getByLabelText('docelowa długość setu'), '240')
    await userEvent.selectOptions(
      screen.getByLabelText('profil wieczoru przy uzupełnianiu'),
      'CLUB',
    )
    await userEvent.click(screen.getByTestId('fill-set'))

    await waitFor(() => expect(fillCalls()).toHaveLength(1))
    expect(String(fillCalls()[0][0])).toContain('/api/sets/42/fill')
    expect(bodyOf(fillCalls()[0]).targetMinutes).toBe(240)
    expect(bodyOf(fillCalls()[0]).curve).toBe('CLUB')
  })

  it('błąd API nie zostawia podglądu na ekranie', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ errorCode: 'SET_NO_CANDIDATES', message: 'Brak kandydatów' }, 400),
    )
    renderWithToasts(<SetFillPanel playlistId={7} disabled={false} onAppend={vi.fn()} />)

    await userEvent.click(screen.getByTestId('fill-set'))

    await waitFor(() => expect(screen.getByText(/Brak kandydatów/)).toBeInTheDocument())
    expect(screen.queryByTestId('fill-preview')).not.toBeInTheDocument()
  })
})
