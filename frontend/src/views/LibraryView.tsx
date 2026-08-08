// Widok biblioteki (M3.1, filtry biblioteczne w M3.2): filtry i sortowanie
// zapisane w adresie, tabela z okładkami, szczegóły utworu w szufladzie.
// Odświeżenie strony wraca do tego samego zestawu filtrów — front trzyma stan
// wyłącznie w hashu.

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  api,
  type CatalogSort,
  type MetricsCoverageResponse,
  type PageResponse,
  type SortDirection,
  type TrackResponse,
} from '../api'
import LibraryTable from '../components/LibraryTable'
import TrackDetails from '../components/TrackDetails'
import { useToast } from '../components/Toasts'
import { useHashRoute } from '../hooks/useHashRoute'
import { ENERGY_LABELS, TEMPO_LABELS } from '../format'

const GENRES = ['LATIN', 'ROCK', 'POP', 'DISCO', 'DISCO_POLO', 'ELECTRONIC', 'HIP_HOP', 'OTHER']
const TEMPO_CLASSES = Object.keys(TEMPO_LABELS)
const ENERGIES = Object.keys(ENERGY_LABELS)
const PAGE_SIZES = [20, 50, 100]
const RATINGS = [1, 2, 3, 4, 5]
/** Wszystkie 24 pozycje koła Camelot (D25) — 1A–12A moll, 1B–12B dur. */
const CAMELOT_KEYS = Array.from({ length: 12 }, (_, index) => index + 1).flatMap((number) => [
  `${number}A`,
  `${number}B`,
])
const DEFAULT_PAGE_SIZE = 20
const SEARCH_DEBOUNCE_MS = 300

/** `lib` w adresie: yes = tylko z biblioteki, no = tylko spoza; brak = bez filtra. */
function parseInLibrary(value: string): boolean | undefined {
  if (value === 'yes') return true
  if (value === 'no') return false
  return undefined
}

/**
 * Pole tekstowe filtra: adres jest źródłem prawdy, ale wpisywanie trafia tam
 * dopiero po chwili bezczynności — inaczej każda litera to nowe zapytanie
 * i nowy wpis w historii przeglądarki.
 */
function useDebouncedParam(value: string, push: (next: string) => void) {

  const [draft, setDraft] = useState(value)
  const lastPushed = useRef(value)

  // zmiana z zewnątrz (wyczyszczenie filtrów, wklejony link) dogania pole
  useEffect(() => {
    if (value !== lastPushed.current) {
      lastPushed.current = value
      setDraft(value)
    }
  }, [value])

  useEffect(() => {
    if (draft === value) return
    const timer = window.setTimeout(() => {
      lastPushed.current = draft
      push(draft)
    }, SEARCH_DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [draft, value, push])

  return [draft, setDraft] as const
}

interface Props {
  refreshKey: number
  selectedIds: ReadonlySet<string>
  onSelectionChange: (ids: ReadonlySet<string>) => void
  onChanged: () => void
}

export default function LibraryView({
  refreshKey,
  selectedIds,
  onSelectionChange,
  onChanged,
}: Props) {

  const { params, setParams } = useHashRoute()
  const { reportError } = useToast()

  const search = params.get('q') ?? ''
  const genreFamily = params.get('genre') ?? ''
  const bpmMin = params.get('bpmMin') ?? ''
  const bpmMax = params.get('bpmMax') ?? ''
  const tempoClass = params.get('tempo') ?? ''
  const energy = params.get('energy') ?? ''
  const inLibrary = params.get('lib') ?? ''
  const ratingMin = params.get('rating') ?? ''
  const tag = params.get('tag') ?? ''
  const camelot = params.get('key') ?? ''
  const keyExact = params.get('keyExact') === '1'
  const valenceMin = params.get('valMin') ?? ''
  const valenceMax = params.get('valMax') ?? ''
  const instrumentalMin = params.get('instr') ?? ''
  const livenessMax = params.get('live') ?? ''
  const sort = (params.get('sort') ?? 'RELEVANCE') as CatalogSort
  const direction = (params.get('dir') ?? 'ASC') as SortDirection
  const page = Number(params.get('page') ?? '0')
  const size = Number(params.get('size') ?? String(DEFAULT_PAGE_SIZE))
  const detailsId = params.get('track')

  const [result, setResult] = useState<PageResponse<TrackResponse> | null>(null)
  const [knownTags, setKnownTags] = useState<string[]>([])
  const [coverage, setCoverage] = useState<MetricsCoverageResponse | null>(null)
  const [loading, setLoading] = useState(true)

  const pushSearch = useCallback(
    (next: string) => setParams({ q: next, page: undefined }),
    [setParams],
  )
  const pushTag = useCallback(
    (next: string) => setParams({ tag: next, page: undefined }),
    [setParams],
  )
  const [searchDraft, setSearchDraft] = useDebouncedParam(search, pushSearch)
  const [tagDraft, setTagDraft] = useDebouncedParam(tag, pushTag)

  // słownik tagów zmienia się rzadko (edycja utworu) — starczy odświeżanie z widokiem
  useEffect(() => {
    let current = true
    api
      .listTags()
      .then((tags) => {
        if (current) setKnownTags(tags)
      })
      .catch(() => setKnownTags([]))
    return () => {
      current = false
    }
  }, [refreshKey])

  // pokrycie metrykami: filtry metryk odsiewają utwory bez nich, więc pusty
  // wynik trzeba umieć wytłumaczyć brakiem danych, a nie awarią (D25)
  useEffect(() => {
    let current = true
    api
      .metricsCoverage()
      .then((loaded) => {
        if (current) setCoverage(loaded)
      })
      .catch(() => setCoverage(null))
    return () => {
      current = false
    }
  }, [refreshKey])

  useEffect(() => {
    let current = true
    setLoading(true)
    api
      .searchTracks({
        search: search || undefined,
        genreFamily: genreFamily || undefined,
        bpmMin: bpmMin ? Number(bpmMin) : undefined,
        bpmMax: bpmMax ? Number(bpmMax) : undefined,
        tempoClass: tempoClass || undefined,
        energy: energy || undefined,
        inLibrary: parseInLibrary(inLibrary),
        ratingMin: ratingMin ? Number(ratingMin) : undefined,
        tag: tag || undefined,
        camelot: camelot || undefined,
        camelotCompatible: camelot ? !keyExact : undefined,
        valenceMin: valenceMin ? Number(valenceMin) : undefined,
        valenceMax: valenceMax ? Number(valenceMax) : undefined,
        instrumentalMin: instrumentalMin ? Number(instrumentalMin) : undefined,
        livenessMax: livenessMax ? Number(livenessMax) : undefined,
        sort,
        direction,
        page,
        size,
      })
      .then((loaded) => {
        if (current) setResult(loaded)
      })
      .catch((error) => {
        if (current) reportError(error, 'Nie udało się pobrać biblioteki')
      })
      .finally(() => {
        if (current) setLoading(false)
      })
    return () => {
      current = false
    }
  }, [
    search,
    genreFamily,
    bpmMin,
    bpmMax,
    tempoClass,
    energy,
    inLibrary,
    ratingMin,
    tag,
    camelot,
    keyExact,
    valenceMin,
    valenceMax,
    instrumentalMin,
    livenessMax,
    sort,
    direction,
    page,
    size,
    refreshKey,
    reportError,
  ])

  const tracks = result?.content ?? []
  const metricFiltersActive =
    valenceMin !== '' || valenceMax !== '' || instrumentalMin !== '' || livenessMax !== ''
  const activeValues = [
    search,
    genreFamily,
    bpmMin,
    bpmMax,
    tempoClass,
    energy,
    inLibrary,
    ratingMin,
    tag,
    camelot,
    valenceMin,
    valenceMax,
    instrumentalMin,
    livenessMax,
  ]
  const filtersActive = useMemo(
    () => activeValues.some((value) => value !== ''),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    activeValues,
  )

  const toggleTrack = (spotifyId: string) => {
    const next = new Set(selectedIds)
    if (next.has(spotifyId)) next.delete(spotifyId)
    else next.add(spotifyId)
    onSelectionChange(next)
  }

  const togglePage = () => {
    const pageIds = tracks.map((track) => track.spotifyId)
    const allSelected = pageIds.every((id) => selectedIds.has(id))
    const next = new Set(selectedIds)
    pageIds.forEach((id) => (allSelected ? next.delete(id) : next.add(id)))
    onSelectionChange(next)
  }

  // pierwsze kliknięcie kolumny sortuje rosnąco, kolejne odwraca kierunek
  const changeSort = (nextSort: CatalogSort) =>
    setParams({
      sort: nextSort,
      dir: nextSort === sort && direction === 'ASC' ? 'DESC' : 'ASC',
      page: undefined,
    })

  // pola tekstowe doganiają puste parametry same (useDebouncedParam)
  const clearFilters = () =>
    setParams({
      q: undefined,
      genre: undefined,
      bpmMin: undefined,
      bpmMax: undefined,
      tempo: undefined,
      energy: undefined,
      lib: undefined,
      rating: undefined,
      tag: undefined,
      key: undefined,
      keyExact: undefined,
      valMin: undefined,
      valMax: undefined,
      instr: undefined,
      live: undefined,
      page: undefined,
    })

  return (
    <section className="panel table-panel" aria-label="Biblioteka">
      <div className="filters">
        <input
          type="search"
          placeholder="Szukaj: tytuł / wykonawca…"
          value={searchDraft}
          onChange={(event) => setSearchDraft(event.target.value)}
          data-testid="search-input"
        />
        <select
          value={genreFamily}
          onChange={(event) => setParams({ genre: event.target.value, page: undefined })}
          aria-label="gatunek"
        >
          <option value="">gatunek: wszystkie</option>
          {GENRES.map((genre) => (
            <option key={genre} value={genre}>
              {genre}
            </option>
          ))}
        </select>
        <input
          type="number"
          placeholder="BPM od"
          value={bpmMin}
          onChange={(event) => setParams({ bpmMin: event.target.value, page: undefined })}
          aria-label="BPM od"
        />
        <input
          type="number"
          placeholder="BPM do"
          value={bpmMax}
          onChange={(event) => setParams({ bpmMax: event.target.value, page: undefined })}
          aria-label="BPM do"
        />
        <select
          value={tempoClass}
          onChange={(event) => setParams({ tempo: event.target.value, page: undefined })}
          aria-label="tempo"
        >
          <option value="">tempo: wszystkie</option>
          {TEMPO_CLASSES.map((value) => (
            <option key={value} value={value}>
              {TEMPO_LABELS[value]}
            </option>
          ))}
        </select>
        <select
          value={energy}
          onChange={(event) => setParams({ energy: event.target.value, page: undefined })}
          aria-label="energia"
        >
          <option value="">energia: wszystkie</option>
          {ENERGIES.map((value) => (
            <option key={value} value={value}>
              {ENERGY_LABELS[value]}
            </option>
          ))}
        </select>

        {/* filtry po danych prywatnych DJ-a (D3) — wyszukiwarka chodzi po katalogu,
            ale potrafi zawęzić go do tego, co jest (albo nie jest) w bibliotece */}
        <div className="filters-library" data-testid="library-filters">
          <label className="filter-group">
            <span>biblioteka</span>
            <select
              value={inLibrary}
              onChange={(event) => setParams({ lib: event.target.value, page: undefined })}
              aria-label="biblioteka"
            >
              <option value="">cały katalog</option>
              <option value="yes">tylko w bibliotece</option>
              <option value="no">tylko spoza biblioteki</option>
            </select>
          </label>
          <label className="filter-group">
            <span>ocena</span>
            <select
              value={ratingMin}
              onChange={(event) => setParams({ rating: event.target.value, page: undefined })}
              aria-label="ocena co najmniej"
            >
              <option value="">dowolna</option>
              {RATINGS.map((value) => (
                <option key={value} value={value}>
                  {'★'.repeat(value)} i wyżej
                </option>
              ))}
            </select>
          </label>
          <label className="filter-group">
            <span>tag DJ-a</span>
            <input
              type="text"
              list="dj-tags"
              placeholder="np. wesele"
              value={tagDraft}
              onChange={(event) => setTagDraft(event.target.value)}
              aria-label="tag DJ-a"
              data-testid="tag-input"
            />
            <datalist id="dj-tags">
              {knownTags.map((value) => (
                <option key={value} value={value} />
              ))}
            </datalist>
          </label>
        </div>

        {/* harmonia (D25): koło Camelot liczy backend z tonacji, więc filtr
            obejmuje też utwory z dumpa AB, nie tylko te z wgranego pliku */}
        <div className="filters-harmonic" data-testid="harmonic-filters">
          <label className="filter-group">
            <span>tonacja</span>
            <select
              value={camelot}
              onChange={(event) => setParams({ key: event.target.value, page: undefined })}
              aria-label="tonacja (Camelot)"
            >
              <option value="">dowolna</option>
              {CAMELOT_KEYS.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
          </label>
          {camelot !== '' && (
            <label className="filter-check">
              <input
                type="checkbox"
                checked={keyExact}
                onChange={(event) =>
                  setParams({ keyExact: event.target.checked ? '1' : undefined, page: undefined })
                }
              />
              <span>tylko dokładna tonacja</span>
            </label>
          )}
        </div>

        {/* metryki z pliku (D24) — filtr działa wyłącznie na utworach, które je mają */}
        <div className="filters-metrics" data-testid="metric-filters">
          <label className="filter-group">
            <span>nastrój od</span>
            <input
              type="number"
              min="0"
              max="1"
              step="0.05"
              placeholder="0.00"
              value={valenceMin}
              onChange={(event) => setParams({ valMin: event.target.value, page: undefined })}
              aria-label="nastrój od"
            />
          </label>
          <label className="filter-group">
            <span>nastrój do</span>
            <input
              type="number"
              min="0"
              max="1"
              step="0.05"
              placeholder="1.00"
              value={valenceMax}
              onChange={(event) => setParams({ valMax: event.target.value, page: undefined })}
              aria-label="nastrój do"
            />
          </label>
          <label className="filter-group">
            <span>instrumentalność od</span>
            <input
              type="number"
              min="0"
              max="1"
              step="0.05"
              placeholder="0.00"
              value={instrumentalMin}
              onChange={(event) => setParams({ instr: event.target.value, page: undefined })}
              aria-label="instrumentalność od"
            />
          </label>
          <label className="filter-group">
            <span>koncertowość do</span>
            <input
              type="number"
              min="0"
              max="1"
              step="0.05"
              placeholder="1.00"
              value={livenessMax}
              onChange={(event) => setParams({ live: event.target.value, page: undefined })}
              aria-label="koncertowość do"
            />
          </label>
        </div>

        {filtersActive && (
          <button className="link" onClick={clearFilters} data-testid="clear-filters">
            wyczyść filtry
          </button>
        )}
        <span className="spacer" />
        <select
          value={size}
          onChange={(event) => setParams({ size: event.target.value, page: undefined })}
          aria-label="utworów na stronie"
        >
          {PAGE_SIZES.map((value) => (
            <option key={value} value={value}>
              {value} / stronę
            </option>
          ))}
        </select>
      </div>

      <p className="muted result-summary" data-testid="result-summary">
        {result
          ? `${result.totalElements} utworów${filtersActive ? ' po filtrach' : ''}`
          : 'Ładowanie…'}
        {selectedIds.size > 0 && ` · zaznaczonych: ${selectedIds.size}`}
      </p>

      {metricFiltersActive && coverage && (
        <p className="muted metrics-coverage" data-testid="metrics-coverage">
          Filtry metryk działają na {coverage.withMetrics} z {coverage.total} utworów — resztę
          uzupełnisz plikiem CSV w zakładce Import.
        </p>
      )}

      <LibraryTable
        tracks={tracks}
        loading={loading}
        selectedIds={selectedIds}
        sort={sort}
        direction={direction}
        onSort={changeSort}
        onToggleTrack={toggleTrack}
        onTogglePage={togglePage}
        onOpenDetails={(spotifyId) => setParams({ track: spotifyId })}
        emptyMessage={
          filtersActive
            ? 'Brak utworów dla tych filtrów.'
            : 'Brak utworów — zaimportuj bibliotekę w zakładce Import.'
        }
      />

      {result && result.totalPages > 1 && (
        <div className="pager">
          <button disabled={page === 0} onClick={() => setParams({ page: page - 1 })}>
            ‹ poprzednia
          </button>
          <span>
            strona {result.page + 1} / {result.totalPages}
          </span>
          <button
            disabled={page + 1 >= result.totalPages}
            onClick={() => setParams({ page: page + 1 })}
          >
            następna ›
          </button>
        </div>
      )}

      {detailsId && (
        <TrackDetails
          spotifyId={detailsId}
          onClose={() => setParams({ track: undefined })}
          onChanged={onChanged}
        />
      )}
    </section>
  )
}
