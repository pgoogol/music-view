// Widok biblioteki (M3.1, filtry biblioteczne w M3.2, uporządkowanie w M5.6):
// filtry, kolumny, sortowanie i stronicowanie zapisane w adresie, tabela
// z okładkami, szczegóły utworu w szufladzie. Odświeżenie strony wraca do tego
// samego widoku — front nie trzyma stanu ekranu nigdzie poza hashem.

import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  api,
  type CatalogRowResponse,
  type CatalogSort,
  type MetricsCoverageResponse,
  type PageResponse,
  type SearchParams,
} from '../api'
import ColumnPicker from '../components/ColumnPicker'
import LibraryFilters from '../components/LibraryFilters'
import LibraryTable from '../components/LibraryTable'
import Pager from '../components/Pager'
import TrackDetails from '../components/TrackDetails'
import { useToast } from '../components/Toasts'
import { useHashRoute } from '../hooks/useHashRoute'
import { parseColumns, serializeColumns, visibleColumns } from '../library/columns'
import {
  PAGE_SIZES,
  activeFilters,
  clearFiltersPatch,
  searchRequest,
} from '../library/query'

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

  const request = useMemo(() => searchRequest(params), [params])
  const requestKey = JSON.stringify(request)
  const columnKeys = useMemo(() => parseColumns(params.get('cols')), [params])
  const columns = useMemo(() => visibleColumns(columnKeys), [columnKeys])
  const filters = useMemo(() => activeFilters(params), [params])
  const detailsId = params.get('track')

  const [result, setResult] = useState<PageResponse<CatalogRowResponse> | null>(null)
  const [knownTags, setKnownTags] = useState<string[]>([])
  const [coverage, setCoverage] = useState<MetricsCoverageResponse | null>(null)
  const [loading, setLoading] = useState(true)

  // słownik tagów i pokrycie metrykami zmieniają się rzadko (edycja utworu,
  // wgranie pliku) — starczy odświeżanie razem z widokiem
  useEffect(() => {
    let current = true
    api
      .listTags()
      .then((tags) => {
        if (current) setKnownTags(tags)
      })
      .catch(() => setKnownTags([]))
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

  // adres *jest* zapytaniem, więc efekt zależy od jego serializacji, a nie od
  // listy kilkunastu filtrów przepisanej po raz drugi w tablicy zależności
  useEffect(() => {
    let current = true
    setLoading(true)
    api
      .searchTracks(JSON.parse(requestKey) as SearchParams)
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
  }, [requestKey, refreshKey, reportError])

  const rows = result?.content ?? []
  const metricFiltersActive =
    request.valenceMin !== undefined ||
    request.valenceMax !== undefined ||
    request.instrumentalMin !== undefined ||
    request.livenessMax !== undefined

  const toggleTrack = (spotifyId: string) => {
    const next = new Set(selectedIds)
    if (next.has(spotifyId)) next.delete(spotifyId)
    else next.add(spotifyId)
    onSelectionChange(next)
  }

  const togglePage = () => {
    const pageIds = rows.map((row) => row.track.spotifyId)
    const allSelected = pageIds.every((id) => selectedIds.has(id))
    const next = new Set(selectedIds)
    pageIds.forEach((id) => (allSelected ? next.delete(id) : next.add(id)))
    onSelectionChange(next)
  }

  // pierwsze kliknięcie kolumny sortuje rosnąco, kolejne odwraca kierunek
  const changeSort = useCallback(
    (nextSort: CatalogSort) =>
      setParams({
        sort: nextSort,
        dir: nextSort === request.sort && request.direction === 'ASC' ? 'DESC' : 'ASC',
        page: undefined,
      }),
    [setParams, request.sort, request.direction],
  )

  const { page, size } = request
  const firstOnPage = page * size + 1
  const lastOnPage = page * size + rows.length

  return (
    <section className="panel table-panel" aria-label="Biblioteka">
      <LibraryFilters
        params={params}
        setParams={setParams}
        knownTags={knownTags}
        onClear={() => setParams(clearFiltersPatch())}
      />

      <div className="table-toolbar">
        <p className="muted result-summary" data-testid="result-summary">
          {result
            ? `${result.totalElements} utworów${filters.length > 0 ? ' po filtrach' : ''}` +
              (rows.length > 0 ? ` · ${firstOnPage}–${lastOnPage} na ekranie` : '')
            : 'Ładowanie…'}
          {selectedIds.size > 0 && ` · zaznaczonych: ${selectedIds.size}`}
        </p>
        <span className="spacer" />
        <ColumnPicker
          selected={columnKeys}
          onChange={(keys) => setParams({ cols: serializeColumns(keys) })}
        />
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

      {/* filtry metryk odsiewają utwory bez metryk, więc pusty wynik trzeba umieć
          wytłumaczyć brakiem danych, a nie awarią (D24/D25) */}
      {metricFiltersActive && coverage && (
        <p className="muted metrics-coverage" data-testid="metrics-coverage">
          Filtry metryk działają na {coverage.withMetrics} z {coverage.total} utworów — resztę
          uzupełnisz plikiem CSV w zakładce Import.
        </p>
      )}

      <LibraryTable
        rows={rows}
        columns={columns}
        loading={loading}
        selectedIds={selectedIds}
        sort={request.sort}
        direction={request.direction}
        onSort={changeSort}
        onToggleTrack={toggleTrack}
        onTogglePage={togglePage}
        onOpenDetails={(spotifyId) => setParams({ track: spotifyId })}
        emptyMessage={
          filters.length > 0
            ? 'Brak utworów dla tych filtrów.'
            : 'Brak utworów — zaimportuj bibliotekę w zakładce Import.'
        }
      />

      {result && result.totalPages > 1 && (
        <Pager
          page={page}
          totalPages={result.totalPages}
          onPage={(next) => setParams({ page: next })}
        />
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
