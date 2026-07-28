import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LibraryView from './LibraryView'
import { aPage, aTrack } from '../test/fixtures'
import { jsonResponse, renderWithToasts } from '../test/renderWithToasts'

const tracks = [
  aTrack({ spotifyId: 'sp-vivir', title: 'Vivir Mi Vida', artist: 'Marc Anthony', bpm: 92 }),
  aTrack({
    spotifyId: 'sp-nowy',
    title: 'Nowy Import',
    artist: 'Nieznany',
    bpm: null,
    genreFamily: null,
    durationMs: null,
  }),
]

let fetchMock: ReturnType<typeof vi.fn>

function lastRequestUrl(): string {
  return String(fetchMock.mock.calls.at(-1)?.[0])
}

function renderLibrary(overrides: Partial<Parameters<typeof LibraryView>[0]> = {}) {

  const props = {
    refreshKey: 0,
    selectedIds: new Set<string>(),
    onSelectionChange: vi.fn(),
    onChanged: vi.fn(),
    ...overrides,
  }
  renderWithToasts(<LibraryView {...props} />)
  return props
}

beforeEach(() => {
  fetchMock = vi.fn().mockResolvedValue(jsonResponse(aPage(tracks)))
  globalThis.fetch = fetchMock as unknown as typeof fetch
})

describe('LibraryView', () => {

  it('pokazuje utwory z czasem trwania i oznacza te bez kompletu pól', async () => {

    renderLibrary()

    expect(await screen.findByText('Vivir Mi Vida')).toBeInTheDocument()
    expect(screen.getByText('4:12')).toBeInTheDocument()

    const rowWithGaps = screen.getByText('Nowy Import').closest('tr')!
    expect(within(rowWithGaps).getByText('do wzbogacenia')).toBeInTheDocument()
  })

  it('pierwsze kliknięcie kolumny sortuje rosnąco, drugie odwraca kierunek', async () => {

    const user = userEvent.setup()
    renderLibrary()
    await screen.findByText('Vivir Mi Vida')

    await user.click(screen.getByRole('columnheader', { name: /BPM/ }))
    await waitFor(() => expect(lastRequestUrl()).toContain('sort=BPM'))
    expect(lastRequestUrl()).toContain('direction=ASC')

    await user.click(screen.getByRole('columnheader', { name: /BPM/ }))
    await waitFor(() => expect(lastRequestUrl()).toContain('direction=DESC'))
  })

  it('filtr gatunku trafia do zapytania i wraca po odświeżeniu w adresie', async () => {

    const user = userEvent.setup()
    renderLibrary()
    await screen.findByText('Vivir Mi Vida')

    await user.selectOptions(screen.getByLabelText('gatunek'), 'LATIN')

    await waitFor(() => expect(lastRequestUrl()).toContain('genreFamily=LATIN'))
    expect(window.location.hash).toContain('genre=LATIN')
  })

  it('wyszukiwarka trafia do zapytania po chwili bezczynności', async () => {

    const user = userEvent.setup()
    renderLibrary()
    await screen.findByText('Vivir Mi Vida')

    await user.type(screen.getByTestId('search-input'), 'salsa')

    await waitFor(() => expect(lastRequestUrl()).toContain('search=salsa'))
  })

  it('zaznaczenie wiersza zgłasza nowy zbiór identyfikatorów', async () => {

    const user = userEvent.setup()
    const props = renderLibrary()
    await screen.findByText('Vivir Mi Vida')

    await user.click(screen.getByLabelText('zaznacz Vivir Mi Vida'))

    expect(props.onSelectionChange).toHaveBeenCalledWith(new Set(['sp-vivir']))
  })

  it('gdy filtry nic nie zwracają, tłumaczy to filtrami zamiast pustą biblioteką', async () => {

    fetchMock.mockResolvedValue(jsonResponse(aPage([], { totalElements: 0 })))
    window.location.hash = '#/library?q=nieistnieje'
    renderLibrary()

    expect(await screen.findByText('Brak utworów dla tych filtrów.')).toBeInTheDocument()
  })
})
