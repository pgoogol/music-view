// Tabela katalogu (M1.8, rozbudowa M3.1): okładki, czas trwania, znacznik braków
// i sortowanie serwerowe — komponent jest prezentacyjny, stan trzyma widok.

import type { CatalogSort, SortDirection, TrackResponse } from '../api'
import { DASH, energyLabel, formatDuration, isEnriched, tempoLabel } from '../format'

interface Column {
  key: string
  label: string
  sort?: CatalogSort
}

const COLUMNS: Column[] = [
  { key: 'title', label: 'Tytuł', sort: 'TITLE' },
  { key: 'artist', label: 'Wykonawca', sort: 'ARTIST' },
  { key: 'year', label: 'Rok', sort: 'YEAR' },
  { key: 'duration', label: 'Czas', sort: 'DURATION' },
  { key: 'bpm', label: 'BPM', sort: 'BPM' },
  { key: 'tempo', label: 'Tempo' },
  { key: 'genre', label: 'Gatunek' },
  { key: 'style', label: 'Styl' },
  { key: 'energy', label: 'Energia', sort: 'ENERGY' },
]

interface Props {
  tracks: readonly TrackResponse[]
  loading: boolean
  selectedIds: ReadonlySet<string>
  sort: CatalogSort
  direction: SortDirection
  onSort: (sort: CatalogSort) => void
  onToggleTrack: (spotifyId: string) => void
  onTogglePage: () => void
  onOpenDetails: (spotifyId: string) => void
  emptyMessage: string
}

export default function LibraryTable({
  tracks,
  loading,
  selectedIds,
  sort,
  direction,
  onSort,
  onToggleTrack,
  onTogglePage,
  onOpenDetails,
  emptyMessage,
}: Props) {

  const pageSelected = tracks.length > 0 && tracks.every((track) => selectedIds.has(track.spotifyId))

  const sortMark = (column: Column) => {
    if (!column.sort || column.sort !== sort) return ''
    return direction === 'ASC' ? ' ▲' : ' ▼'
  }

  return (
    <div className={loading ? 'table-wrap loading' : 'table-wrap'}>
      <table data-testid="library-table">
        <thead>
          <tr>
            <th className="col-check">
              <input
                type="checkbox"
                checked={pageSelected}
                onChange={onTogglePage}
                aria-label="zaznacz stronę"
              />
            </th>
            <th className="col-cover" aria-label="okładka" />
            {COLUMNS.map((column) => (
              <th
                key={column.key}
                className={column.sort ? 'sortable' : undefined}
                aria-sort={
                  column.sort === sort ? (direction === 'ASC' ? 'ascending' : 'descending') : undefined
                }
                onClick={column.sort ? () => onSort(column.sort!) : undefined}
              >
                {column.label}
                {sortMark(column)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {tracks.map((track) => (
            <tr key={track.spotifyId} className={selectedIds.has(track.spotifyId) ? 'selected' : undefined}>
              <td className="col-check">
                <input
                  type="checkbox"
                  checked={selectedIds.has(track.spotifyId)}
                  onChange={() => onToggleTrack(track.spotifyId)}
                  aria-label={`zaznacz ${track.title ?? track.spotifyId}`}
                />
              </td>
              <td className="col-cover">
                {track.albumImageUrl ? (
                  <img src={track.albumImageUrl} alt="" loading="lazy" className="cover" />
                ) : (
                  <span className="cover cover-empty" aria-hidden="true" />
                )}
              </td>
              <td>
                <button className="link" onClick={() => onOpenDetails(track.spotifyId)}>
                  {track.title ?? track.spotifyId}
                </button>
                {!isEnriched(track) && (
                  <span className="badge badge-warn" title="brak BPM lub gatunku">
                    do wzbogacenia
                  </span>
                )}
              </td>
              <td>{track.artist ?? DASH}</td>
              <td>{track.year ?? DASH}</td>
              <td>{formatDuration(track.durationMs)}</td>
              <td title={track.bpmSource ? `źródło: ${track.bpmSource}` : undefined}>
                {track.bpm ?? DASH}
              </td>
              <td>{tempoLabel(track.tempoClass)}</td>
              <td>{track.genreFamily ?? DASH}</td>
              <td>{track.style ?? DASH}</td>
              <td>{energyLabel(track.energy)}</td>
            </tr>
          ))}
          {tracks.length === 0 && !loading && (
            <tr>
              <td colSpan={COLUMNS.length + 2} className="muted empty-row">
                {emptyMessage}
              </td>
            </tr>
          )}
          {tracks.length === 0 && loading && (
            <tr>
              <td colSpan={COLUMNS.length + 2} className="muted empty-row">
                Ładowanie…
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}
