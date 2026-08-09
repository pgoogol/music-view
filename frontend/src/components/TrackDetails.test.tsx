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

/** Treści wymyślone na potrzeby testu — repozytorium nie trzyma cudzych tekstów. */
const lyrics = {
  spotifyId: 'sp-vivir',
  status: 'TRANSLATED',
  sourceLanguage: 'hiszpański',
  originalLyrics: 'Pierwszy wers testowego tekstu\nDrugi wers testowego tekstu',
  translationPl: 'Pierwszy wers po polsku\nDrugi wers po polsku',
  interpretationPl: 'Utwór o świętowaniu mimo trudności.',
  lrclibId: 42,
  fetchedAt: '2026-08-09T10:00:00Z',
  translatedAt: '2026-08-09T10:00:05Z',
  modelUsed: 'test-model',
  promptVersion: 1,
}

let fetchMock: ReturnType<typeof vi.fn>
let patchStatus = 200
let lyricsResponse: unknown = undefined
let jobStatus = 'COMPLETED'

function patchCalls() {
  return fetchMock.mock.calls.filter((call) => (call[1] as RequestInit)?.method === 'PATCH')
}

function enrichCalls() {
  return fetchMock.mock.calls.filter(
    (call) => String(call[0]) === '/api/enrich' && (call[1] as RequestInit)?.method === 'POST',
  )
}

beforeEach(() => {
  patchStatus = 200
  lyricsResponse = undefined
  jobStatus = 'COMPLETED'
  fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
    const target = String(url)
    if (target.includes('/metrics')) return Promise.resolve(jsonResponse(undefined, 204))
    if (target.includes('/lyrics')) {
      return Promise.resolve(
        lyricsResponse === undefined
          ? jsonResponse(undefined, 204)
          : jsonResponse(lyricsResponse),
      )
    }
    if (target === '/api/enrich') return Promise.resolve(jsonResponse({ executionId: 7 }))
    if (target.startsWith('/api/enrich/jobs/')) {
      return Promise.resolve(jsonResponse({ executionId: 7, status: jobStatus }))
    }
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

describe('TrackDetails — tekst i tłumaczenie (M6.1)', () => {

  it('pokazuje interpretację, tłumaczenie i język oryginału', async () => {

    lyricsResponse = lyrics
    renderWithToasts(<TrackDetails spotifyId="sp-vivir" onClose={vi.fn()} onChanged={vi.fn()} />)

    expect(await screen.findByTestId('lyrics-interpretation')).toHaveTextContent(
      'Utwór o świętowaniu mimo trudności.',
    )
    expect(screen.getByTestId('lyrics-translation')).toHaveTextContent('Pierwszy wers po polsku')
    expect(screen.getByTestId('track-lyrics')).toHaveTextContent('hiszpański')
  })

  it('gdy tekstu nie było, zleca job SINGLE na grupę LYRICS i wczytuje wynik', async () => {

    const user = userEvent.setup()
    renderWithToasts(<TrackDetails spotifyId="sp-vivir" onClose={vi.fn()} onChanged={vi.fn()} />)
    const button = await screen.findByTestId('lyrics-fetch')

    lyricsResponse = lyrics
    await user.click(button)

    await waitFor(() => expect(enrichCalls()).toHaveLength(1))
    expect(JSON.parse(String((enrichCalls()[0][1] as RequestInit).body))).toMatchObject({
      scope: 'SINGLE',
      fields: ['LYRICS'],
      spotifyIds: ['sp-vivir'],
    })
    expect(await screen.findByTestId('lyrics-interpretation')).toBeInTheDocument()
  })

  it('brak tekstu w LRCLIB pokazuje jako odpowiedź, nie jako awarię', async () => {

    lyricsResponse = { ...lyrics, status: 'NOT_FOUND', translationPl: null, interpretationPl: null }
    renderWithToasts(<TrackDetails spotifyId="sp-vivir" onClose={vi.fn()} onChanged={vi.fn()} />)

    expect(await screen.findByTestId('track-lyrics')).toHaveTextContent('nie zna tekstu')
    expect(screen.queryByTestId('lyrics-interpretation')).not.toBeInTheDocument()
  })
})
