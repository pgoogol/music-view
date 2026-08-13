import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LibraryView from './LibraryView'
import { aPage, aRow } from '../test/fixtures'
import { jsonResponse, renderWithToasts } from '../test/renderWithToasts'

const rows = [
  aRow(
    { spotifyId: 'sp-vivir', title: 'Vivir Mi Vida', artist: 'Marc Anthony', bpm: 92 },
    { rating: 5, customTags: ['parkiet'] },
  ),
  aRow(
    {
      spotifyId: 'sp-nowy',
      title: 'Nowy Import',
      artist: 'Nieznany',
      bpm: null,
      genreFamily: null,
      durationMs: null,
    },
    null,
  ),
]

let fetchMock: ReturnType<typeof vi.fn>
let searchResponse = aPage(rows)

/** Liczy się ostatnie zapytanie o utwory — po nim widać komplet filtrów z adresu. */
function lastRequestUrl(): string {

  const catalogCalls = fetchMock.mock.calls.filter((call) =>
    String(call[0]).includes('/api/catalog/tracks'),
  )
  return String(catalogCalls.at(-1)?.[0])
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

/** Filtry poza pierwszym rzutem siedzą w panelu — test otwiera go tak jak DJ. */
async function openAdvanced(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByTestId('toggle-advanced-filters'))
}

beforeEach(() => {
  searchResponse = aPage(rows)
  fetchMock = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(searchResponse)))
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

  it('tabela opisuje utwór, a dane prywatne DJ-a zostawia szufladzie (D39)', async () => {

    renderLibrary()
    await screen.findByText('Vivir Mi Vida')

    expect(screen.queryByRole('columnheader', { name: /Ocena/ })).toBeNull()
    expect(screen.queryByRole('columnheader', { name: /Tagi DJ-a/ })).toBeNull()
    expect(screen.queryByRole('columnheader', { name: /Dodano/ })).toBeNull()

    const rated = screen.getByText('Vivir Mi Vida').closest('tr')!
    expect(within(rated).queryByLabelText(/ocena:/)).toBeNull()
    expect(within(rated).queryByText('parkiet')).toBeNull()
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

  it('filtr oceny trafia do zapytania i wraca po odświeżeniu w adresie', async () => {

    const user = userEvent.setup()
    renderLibrary()
    await screen.findByText('Vivir Mi Vida')

    await user.selectOptions(screen.getByLabelText('ocena co najmniej'), '4')

    await waitFor(() => expect(lastRequestUrl()).toContain('ratingMin=4'))
    expect(window.location.hash).toContain('rating=4')
  })

  it('filtr tonacji wysyła pozycję koła i domyślnie rozszerza go do zgodnych', async () => {

    const user = userEvent.setup()
    renderLibrary()
    await screen.findByText('Vivir Mi Vida')
    await openAdvanced(user)

    await user.selectOptions(screen.getByLabelText('tonacja (Camelot)'), '8A')

    await waitFor(() => expect(lastRequestUrl()).toContain('camelot=8A'))
    expect(lastRequestUrl()).toContain('camelotCompatible=true')

    await user.click(screen.getByLabelText('tylko dokładna tonacja'))

    await waitFor(() => expect(lastRequestUrl()).toContain('camelotCompatible=false'))
    expect(window.location.hash).toContain('key=8A')
  })

  it('filtr długości trafia do zapytania', async () => {

    const user = userEvent.setup()
    renderLibrary()
    await screen.findByText('Vivir Mi Vida')
    await openAdvanced(user)

    await user.type(screen.getByLabelText('czas do (sek)'), '240')

    await waitFor(() => expect(lastRequestUrl()).toContain('durationMaxSec=240'))
    expect(window.location.hash).toContain('durMax=240')
  })

  it('panel filtrów ma już tylko utwór i brzmienie (D39)', async () => {

    const user = userEvent.setup()
    renderLibrary()
    await screen.findByText('Vivir Mi Vida')
    await openAdvanced(user)

    expect(screen.getByTestId('track-filters')).toBeInTheDocument()
    expect(screen.getByTestId('harmonic-filters')).toBeInTheDocument()
    expect(screen.queryByTestId('library-filters')).toBeNull()
    expect(screen.queryByTestId('metric-filters')).toBeNull()
    expect(screen.queryByTestId('quality-filters')).toBeNull()
    expect(screen.queryByLabelText('biblioteka')).toBeNull()
    expect(screen.queryByLabelText('rok od')).toBeNull()
    expect(screen.queryByLabelText('wulgaryzmy')).toBeNull()
  })

  it('panel filtrów otwiera się sam, gdy filtr spoza pierwszego rzutu jest w adresie', async () => {

    window.location.hash = '#/library?bpmMin=120'
    renderLibrary()

    expect(await screen.findByTestId('advanced-filters')).toBeInTheDocument()
    expect(screen.getByLabelText('BPM od')).toHaveValue(120)
  })

  it('aktywne filtry są widoczne jako chipsy i dają się zdejmować pojedynczo', async () => {

    const user = userEvent.setup()
    window.location.hash = '#/library?genre=LATIN&bpmMin=100&bpmMax=130'
    renderLibrary()
    await screen.findByText('Vivir Mi Vida')

    const chips = screen.getByTestId('active-filters')
    expect(within(chips).getByText('gatunek: LATIN')).toBeInTheDocument()
    expect(within(chips).getByText('BPM 100–130')).toBeInTheDocument()

    await user.click(screen.getByLabelText('usuń filtr BPM 100–130'))

    await waitFor(() => expect(window.location.hash).not.toContain('bpmMin=100'))
    expect(window.location.hash).not.toContain('bpmMax=130')
    expect(window.location.hash).toContain('genre=LATIN')
  })

  it('wyczyszczenie filtrów kasuje także te z panelu', async () => {

    const user = userEvent.setup()
    window.location.hash = '#/library?genre=LATIN&rating=3&key=8A&durMax=240'
    renderLibrary()
    await screen.findByText('Vivir Mi Vida')

    await user.click(screen.getByTestId('clear-filters'))

    await waitFor(() => expect(window.location.hash).not.toContain('genre=LATIN'))
    expect(window.location.hash).not.toContain('rating=3')
    expect(window.location.hash).not.toContain('key=8A')
    expect(window.location.hash).not.toContain('durMax=240')
  })

  it('zdjęty filtr ze starego linku nie zawęża wyniku ani nie wraca na ekran (D39)', async () => {

    window.location.hash = '#/library?lib=yes&missing=ANY&instr=0.5'
    renderLibrary()
    await screen.findByText('Vivir Mi Vida')

    expect(lastRequestUrl()).not.toContain('inLibrary')
    expect(lastRequestUrl()).not.toContain('missing')
    expect(lastRequestUrl()).not.toContain('instrumentalMin')
    expect(screen.queryByTestId('active-filters')).toBeNull()
  })

  it('gdy filtry nic nie zwracają, tłumaczy to filtrami zamiast pustą biblioteką', async () => {

    searchResponse = aPage([], { totalElements: 0 })
    window.location.hash = '#/library?q=nieistnieje'
    renderLibrary()

    expect(await screen.findByText('Brak utworów dla tych filtrów.')).toBeInTheDocument()
  })

  it('wybrane kolumny wracają w adresie i zmieniają zestaw nagłówków', async () => {

    const user = userEvent.setup()
    renderLibrary()
    await screen.findByText('Vivir Mi Vida')

    expect(screen.queryByRole('columnheader', { name: /Popularność/ })).toBeNull()

    await user.click(screen.getByTestId('column-picker-toggle'))
    await user.click(within(screen.getByTestId('column-picker-panel')).getByText('Popularność'))

    expect(await screen.findByRole('columnheader', { name: /Popularność/ })).toBeInTheDocument()
    expect(window.location.hash).toContain('cols=')
    expect(decodeURIComponent(window.location.hash)).toContain('popularity')
  })

  it('kolumny z adresu wygrywają z zestawem domyślnym', async () => {

    window.location.hash = '#/library?cols=title,artist,style'

    renderLibrary()

    expect(await screen.findByRole('columnheader', { name: /Styl/ })).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: /Gatunek/ })).toBeNull()

    const row = screen.getByText('Vivir Mi Vida').closest('tr')!
    expect(within(row).getByText('salsa')).toBeInTheDocument()
  })

  it('większa strona i skok na ostatnią stronę idą do zapytania', async () => {

    const user = userEvent.setup()
    searchResponse = aPage(rows, { totalElements: 2500, totalPages: 13, size: 200 })
    renderLibrary()
    await screen.findByText('Vivir Mi Vida')

    await user.selectOptions(screen.getByLabelText('utworów na stronie'), '200')
    await waitFor(() => expect(lastRequestUrl()).toContain('size=200'))

    await user.click(screen.getByLabelText('ostatnia strona'))

    await waitFor(() => expect(lastRequestUrl()).toContain('page=12'))
    expect(screen.getByTestId('result-summary')).toHaveTextContent('2500 utworów')
  })
})
