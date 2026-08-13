// Tabela katalogu (M1.8, rozbudowa M3.1, kolumny wybierane w M5.6): okładki,
// sortowanie serwerowe i zestaw kolumn podany z zewnątrz — komponent jest
// prezentacyjny, stan trzyma widok, a definicje kolumn żyją w library/columns.

import type { CatalogRowResponse, CatalogSort, SortDirection } from '../api'
import type { LibraryColumn } from '../library/columns'

interface Props {
  rows: readonly CatalogRowResponse[]
  columns: readonly LibraryColumn[]
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
  rows,
  columns,
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

  const pageSelected =
    rows.length > 0 && rows.every((row) => selectedIds.has(row.track.spotifyId))

  const sortMark = (column: LibraryColumn) => {
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
            {columns.map((column) => (
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
          {rows.map((row) => (
            <tr
              key={row.track.spotifyId}
              className={selectedIds.has(row.track.spotifyId) ? 'selected' : undefined}
            >
              <td className="col-check">
                <input
                  type="checkbox"
                  checked={selectedIds.has(row.track.spotifyId)}
                  onChange={() => onToggleTrack(row.track.spotifyId)}
                  aria-label={`zaznacz ${row.track.title ?? row.track.spotifyId}`}
                />
              </td>
              <td className="col-cover">
                {row.track.albumImageUrl ? (
                  <img src={row.track.albumImageUrl} alt="" loading="lazy" className="cover" />
                ) : (
                  <span className="cover cover-empty" aria-hidden="true" />
                )}
              </td>
              {columns.map((column) => (
                <td key={column.key} className={`col-${column.key}`}>
                  {column.cell(row, { onOpenDetails })}
                </td>
              ))}
            </tr>
          ))}
          {rows.length === 0 && (
            <tr>
              <td colSpan={columns.length + 2} className="muted empty-row">
                {loading ? 'Ładowanie…' : emptyMessage}
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}
