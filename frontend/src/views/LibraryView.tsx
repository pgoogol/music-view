// Widok biblioteki (M3.1): filtry i sortowanie zapisane w adresie, tabela
// z okładkami, szczegóły utworu w szufladzie. Odświeżenie strony wraca do
// tego samego zestawu filtrów — front trzyma stan wyłącznie w hashu.

import { useEffect, useMemo, useRef, useState } from 'react'
import { api, type CatalogSort, type PageResponse, type SortDirection, type TrackResponse } from '../api'
import LibraryTable from '../components/LibraryTable'
import TrackDetails from '../components/TrackDetails'
import { useToast } from '../components/Toasts'
import { useHashRoute } from '../hooks/useHashRoute'
import { ENERGY_LABELS, TEMPO_LABELS } from '../format'

const GENRES = ['LATIN', 'ROCK', 'POP', 'DISCO', 'DISCO_POLO', 'ELECTRONIC', 'HIP_HOP', 'OTHER']
const TEMPO_CLASSES = Object.keys(TEMPO_LABELS)
const ENERGIES = Object.keys(ENERGY_LABELS)
const PAGE_SIZES = [20, 50, 100]
const DEFAULT_PAGE_SIZE = 20
const SEARCH_DEBOUNCE_MS = 300

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
  const sort = (params.get('sort') ?? 'RELEVANCE') as CatalogSort
  const direction = (params.get('dir') ?? 'ASC') as SortDirection
  const page = Number(params.get('page') ?? '0')
  const size = Number(params.get('size') ?? String(DEFAULT_PAGE_SIZE))
  const detailsId = params.get('track')

  const [searchDraft, setSearchDraft] = useState(search)
  const [result, setResult] = useState<PageResponse<TrackResponse> | null>(null)
  const [loading, setLoading] = useState(true)
  const lastPushedSearch = useRef(search)

  // adres jest źródłem prawdy; pole tekstowe dogania go tylko przy zmianie z zewnątrz
  useEffect(() => {
    if (search !== lastPushedSearch.current) {
      lastPushedSearch.current = search
      setSearchDraft(search)
    }
  }, [search])

  useEffect(() => {
    if (searchDraft === search) return
    const timer = window.setTimeout(() => {
      lastPushedSearch.current = searchDraft
      setParams({ q: searchDraft, page: undefined })
    }, SEARCH_DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [searchDraft, search, setParams])

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
    sort,
    direction,
    page,
    size,
    refreshKey,
    reportError,
  ])

  const tracks = result?.content ?? []
  const filtersActive = useMemo(
    () => [search, genreFamily, bpmMin, bpmMax, tempoClass, energy].some((value) => value !== ''),
    [search, genreFamily, bpmMin, bpmMax, tempoClass, energy],
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

  const clearFilters = () => {
    setSearchDraft('')
    lastPushedSearch.current = ''
    setParams({
      q: undefined,
      genre: undefined,
      bpmMin: undefined,
      bpmMax: undefined,
      tempo: undefined,
      energy: undefined,
      page: undefined,
    })
  }

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
