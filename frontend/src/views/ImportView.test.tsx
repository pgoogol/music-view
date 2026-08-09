import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ImportView from './ImportView'
import type { IngestMetricsResponse, IngestMyPlaylistsResponse } from '../api'
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

const metricsReport: IngestMetricsResponse = {
  applied: 2,
  matchedByIsrc: 1,
  skippedRows: 1,
  failedRows: 0,
  files: [
    {
      file: 'metryki.csv',
      applied: 2,
      matchedByIsrc: 1,
      skipped: [{ line: 3, reason: 'brak takiego utworu w katalogu' }],
      failed: [],
      errorCode: null,
      error: null,
    },
  ],
}

const myPlaylists: IngestMyPlaylistsResponse = {
  imported: [
    anIngestReport({ spotifyPlaylistId: 'sp-1', name: 'Wesela 2026', tracks: 30, imported: 12 }),
    anIngestReport({
      spotifyPlaylistId: 'sp-2',
      name: 'Salsa nocą',
      tracks: 20,
      imported: 5,
      skipped: [{ position: 3, reason: 'brak ISRC' }],
    }),
  ],
  failed: [],
}

let metricsResponse: IngestMetricsResponse = metricsReport
let myPlaylistsResponse: IngestMyPlaylistsResponse = myPlaylists

beforeEach(() => {
  metricsResponse = metricsReport
  myPlaylistsResponse = myPlaylists
  globalThis.fetch = vi.fn().mockImplementation((url: string) => {
    const address = String(url)
    if (address.includes('/api/auth/spotify/status')) return Promise.resolve(jsonResponse(connectedAccount))
    if (address.includes('/api/ingest/metrics')) return Promise.resolve(jsonResponse(metricsResponse))
    return Promise.resolve(jsonResponse(myPlaylistsResponse))
  }) as unknown as typeof fetch
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

  it('wgranie CSV z metrykami pokazuje raport i odświeża widok', async () => {

    const user = userEvent.setup()
    const onImported = vi.fn()
    renderWithToasts(<ImportView onImported={onImported} />)
    await screen.findByTestId('spotify-status')

    const file = new File(['Spotify Track Id,BPM\nsp-1,96\n'], 'metryki.csv', { type: 'text/csv' })
    await user.upload(screen.getByTestId('metrics-input'), file)
    await user.click(screen.getByTestId('metrics-upload'))

    const report = await screen.findByTestId('metrics-report')
    expect(within(report).getByText(/uzupełnione utwory/)).toBeInTheDocument()
    expect(within(report).getByText(/wiersz 3/)).toBeInTheDocument()
    expect(onImported).toHaveBeenCalled()
  })

  it('modal pokazuje playlisty, które padły, obok tych zaimportowanych', async () => {

    myPlaylistsResponse = {
      ...myPlaylists,
      failed: [
        {
          spotifyPlaylistId: 'sp-3',
          name: 'Bachata na później',
          errorCode: 'SPOTIFY_UNAVAILABLE',
          reason: 'Spotify nie odpowiedziało',
        },
      ],
    }
    const user = userEvent.setup()
    renderWithToasts(<ImportView onImported={vi.fn()} />)
    await waitFor(() => expect(screen.getByTestId('import-my-playlists')).toBeEnabled())

    await user.click(screen.getByTestId('import-my-playlists'))

    const failed = await screen.findByTestId('my-playlists-failed')
    expect(within(failed).getByText(/Bachata na później/)).toBeInTheDocument()
    expect(within(failed).getByText(/Spotify nie odpowiedziało/)).toBeInTheDocument()
    // reszta i tak weszła — po to raport
    expect(within(screen.getByTestId('my-playlists-report')).getByText(/Wesela 2026/))
      .toBeInTheDocument()
  })

  it('wgranie kilku plików pokazuje raport osobno dla każdego', async () => {

    metricsResponse = {
      applied: 2,
      matchedByIsrc: 0,
      skippedRows: 0,
      failedRows: 0,
      files: [
        { file: 'wesela.csv', applied: 2, matchedByIsrc: 0, skipped: [], failed: [], errorCode: null, error: null },
        {
          file: 'zepsuty.csv',
          applied: 0,
          matchedByIsrc: 0,
          skipped: [],
          failed: [],
          errorCode: 'CSV_MISSING_COLUMNS',
          error: 'Plik CSV nie zawiera kolumny identyfikującej utwór',
        },
      ],
    }
    const user = userEvent.setup()
    renderWithToasts(<ImportView onImported={vi.fn()} />)
    await screen.findByTestId('spotify-status')

    await user.upload(screen.getByTestId('metrics-input'), [
      new File(['Spotify Track Id,BPM\nsp-1,96\n'], 'wesela.csv', { type: 'text/csv' }),
      new File(['Song\nBez identyfikatora\n'], 'zepsuty.csv', { type: 'text/csv' }),
    ])
    await user.click(screen.getByTestId('metrics-upload'))

    const report = await screen.findByTestId('metrics-report')
    expect(within(report).getByText(/wesela\.csv/)).toBeInTheDocument()
    expect(within(report).getByText(/plik odrzucony/)).toBeInTheDocument()
    expect(within(report).getByText(/nie zawiera kolumny identyfikującej/)).toBeInTheDocument()
  })

  it('wgranie bez wybranego pliku kończy się komunikatem, nie żądaniem', async () => {

    const user = userEvent.setup()
    renderWithToasts(<ImportView onImported={vi.fn()} />)
    await screen.findByTestId('spotify-status')

    await user.click(screen.getByTestId('metrics-upload'))

    expect(await screen.findByText(/Wybierz plik CSV z metrykami/)).toBeInTheDocument()
    expect(screen.queryByTestId('metrics-report')).not.toBeInTheDocument()
  })
})
