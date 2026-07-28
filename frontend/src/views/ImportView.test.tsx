import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ImportView from './ImportView'
import { anIngestReport } from '../test/fixtures'
import { jsonResponse, renderWithToasts } from '../test/renderWithToasts'

const connectedAccount = {
  connected: true,
  spotifyUserId: 'dj-pg',
  displayName: 'DJ PG',
  scopes: 'playlist-read-private',
  expiresAt: '2026-07-28T22:00:00Z',
  connectedAt: '2026-07-01T10:00:00Z',
}

const reports = [
  anIngestReport({ spotifyPlaylistId: 'sp-1', name: 'Wesela 2026', tracks: 30, imported: 12 }),
  anIngestReport({
    spotifyPlaylistId: 'sp-2',
    name: 'Salsa nocą',
    tracks: 20,
    imported: 5,
    skipped: [{ position: 3, reason: 'brak ISRC' }],
  }),
]

beforeEach(() => {
  globalThis.fetch = vi.fn().mockImplementation((url: string) =>
    Promise.resolve(
      jsonResponse(String(url).includes('/api/auth/spotify/status') ? connectedAccount : reports),
    ),
  ) as unknown as typeof fetch
})

describe('ImportView', () => {

  it('nie pokazuje już importu z pliku CSV', async () => {

    renderWithToasts(<ImportView onImported={vi.fn()} />)
    await screen.findByTestId('spotify-status')

    expect(screen.queryByText(/Import z pliku/)).not.toBeInTheDocument()
    expect(screen.queryByTestId('csv-input')).not.toBeInTheDocument()
    expect(screen.getByTestId('playlist-url')).toBeInTheDocument()
  })

  it('import własnych playlist kończy się popupem z podsumowaniem', async () => {

    const user = userEvent.setup()
    const onImported = vi.fn()
    renderWithToasts(<ImportView onImported={onImported} />)
    await waitFor(() => expect(screen.getByTestId('import-my-playlists')).toBeEnabled())

    await user.click(screen.getByTestId('import-my-playlists'))

    const modal = await screen.findByTestId('my-playlists-modal')
    expect(within(modal).getByText(/Wesela 2026/)).toBeInTheDocument()
    expect(within(modal).getByText(/Salsa nocą/)).toBeInTheDocument()
    expect(onImported).toHaveBeenCalled()
  })

  it('popup zamyka się przyciskiem', async () => {

    const user = userEvent.setup()
    renderWithToasts(<ImportView onImported={vi.fn()} />)
    await waitFor(() => expect(screen.getByTestId('import-my-playlists')).toBeEnabled())
    await user.click(screen.getByTestId('import-my-playlists'))
    await screen.findByTestId('my-playlists-modal')

    await user.click(screen.getByTestId('modal-close'))

    expect(screen.queryByTestId('my-playlists-modal')).not.toBeInTheDocument()
  })
})
