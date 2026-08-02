import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TrackDetails from './TrackDetails'
import { aTrack } from '../test/fixtures'
import { jsonResponse, renderWithToasts } from '../test/renderWithToasts'

const entry = {
  id: 1,
  spotifyId: 'sp-vivir',
  source: 'PLAYLIST',
  addedAt: '2026-07-01T18:00:00Z',
  djNotes: 'stara notatka',
  customTags: ['wesele'],
  rating: 4,
  djSlotOverride: null,
  version: 3,
  track: aTrack({ spotifyId: 'sp-vivir' }),
}

let fetchMock: ReturnType<typeof vi.fn>
let patchStatus = 200

function patchCalls() {
  return fetchMock.mock.calls.filter((call) => (call[1] as RequestInit)?.method === 'PATCH')
}

beforeEach(() => {
  patchStatus = 200
  fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
    const target = String(url)
    if (target.includes('/metrics')) return Promise.resolve(jsonResponse(undefined, 204))
    if (init?.method === 'PATCH') {
      return patchStatus === 200
        ? Promise.resolve(jsonResponse({ ...entry, version: entry.version + 1 }))
        : Promise.resolve(
            jsonResponse(
              { errorCode: 'RESOURCE_MODIFIED', message: 'Wpis zmienił się w innym miejscu' },
              409,
            ),
          )
    }
    return Promise.resolve(jsonResponse(entry))
  })
  globalThis.fetch = fetchMock as unknown as typeof fetch
})

describe('TrackDetails — blokada optymistyczna (M5.2)', () => {

  it('odsyła wersję wpisu przy zapisie', async () => {

    const user = userEvent.setup()
    renderWithToasts(<TrackDetails spotifyId="sp-vivir" onClose={vi.fn()} onChanged={vi.fn()} />)
    await screen.findByTestId('dj-notes')

    await user.click(screen.getByTestId('dj-save'))

    await waitFor(() => expect(patchCalls()).toHaveLength(1))
    expect(JSON.parse(String((patchCalls()[0][1] as RequestInit).body))).toMatchObject({
      version: 3,
    })
  })

  it('przy konflikcie mówi o zmianie i nie kasuje tego, co DJ wpisał', async () => {

    const user = userEvent.setup()
    patchStatus = 409
    renderWithToasts(<TrackDetails spotifyId="sp-vivir" onClose={vi.fn()} onChanged={vi.fn()} />)
    const notes = await screen.findByTestId('dj-notes')

    await user.clear(notes)
    await user.type(notes, 'moja świeża notatka')
    await user.click(screen.getByTestId('dj-save'))

    expect(await screen.findByText(/zmienił się w innym miejscu/)).toBeInTheDocument()
    expect(screen.getByTestId('dj-notes')).toHaveValue('moja świeża notatka')
  })
})
