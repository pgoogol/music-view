// Kolumny tabeli biblioteki (M5.6) w jednym miejscu: nagłówek, porządek
// sortowania i sposób narysowania komórki. Wcześniej lista kolumn była w tabeli,
// a wartości w JSX-ie pod nią — dołożenie kolumny znaczyło trafić w dwa miejsca
// naraz i nie pomylić kolejności.
//
// Zestaw domyślny to odpowiedź na „co widać bez klikania": tożsamość utworu,
// to, czym się go miksuje (BPM, tonacja, energia) i to, co DJ o nim sam
// powiedział (ocena). Reszta czeka pod wybierakiem — razem z tym, co dotąd
// stało w tabeli na stałe, choć powtarzało sąsiednią kolumnę (tempo to
// przedział BPM) albo bywało akapitem, nie komórką (styl).

import type { ReactNode } from 'react'
import type { CatalogRowResponse, CatalogSort } from '../api'
import StarRating from '../components/StarRating'
import {
  DASH,
  energyLabel,
  formatDate,
  formatDuration,
  formatScore,
  isEnriched,
  tempoLabel,
} from '../format'

/** Tabela jest prezentacyjna — komórka tytułu dostaje otwieranie szuflady stąd. */
export interface CellContext {
  onOpenDetails: (spotifyId: string) => void
}

export interface LibraryColumn {
  key: string
  label: string
  /** Kolumny bez porządku w białej liście API nie są klikalne (M3.1). */
  sort?: CatalogSort
  /** Kolumny tożsamości utworu — bez nich wiersz przestaje cokolwiek znaczyć. */
  fixed?: boolean
  cell: (row: CatalogRowResponse, context: CellContext) => ReactNode
}

/** Tytuł otwiera szufladę utworu i niesie znacznik braków (D5). */
function titleCell(row: CatalogRowResponse, { onOpenDetails }: CellContext): ReactNode {

  const { spotifyId, title } = row.track
  return (
    <>
      <button className="link" onClick={() => onOpenDetails(spotifyId)}>
        {title ?? spotifyId}
      </button>
      {!isEnriched(row.track) && (
        <span className="badge badge-warn" title="brak BPM lub gatunku">
          do wzbogacenia
        </span>
      )}
    </>
  )
}

/** Tonacja jako pozycja koła (D25); pełny zapis w dymku, bo koło jest krótsze. */
function keyCell(row: CatalogRowResponse): ReactNode {

  const { camelot, musicalKey } = row.track
  if (!camelot) return musicalKey ?? DASH
  return <span title={musicalKey ?? undefined}>{camelot}</span>
}

function ratingCell(row: CatalogRowResponse): ReactNode {

  const rating = row.library?.rating
  if (!rating) return DASH
  return <StarRating value={rating} />
}

function tagsCell(row: CatalogRowResponse): ReactNode {

  const tags = row.library?.customTags ?? []
  if (tags.length === 0) return DASH
  return (
    <span className="chips chips-inline">
      {tags.map((tag) => (
        <span key={tag} className="chip">
          {tag}
        </span>
      ))}
    </span>
  )
}

export const LIBRARY_COLUMNS: readonly LibraryColumn[] = [
  { key: 'title', label: 'Tytuł', sort: 'TITLE', fixed: true, cell: titleCell },
  {
    key: 'artist',
    label: 'Wykonawca',
    sort: 'ARTIST',
    fixed: true,
    cell: (row) => row.track.artist ?? DASH,
  },
  { key: 'album', label: 'Album', sort: 'ALBUM', cell: (row) => row.track.album ?? DASH },
  { key: 'year', label: 'Rok', sort: 'YEAR', cell: (row) => row.track.year ?? DASH },
  { key: 'duration', label: 'Czas', sort: 'DURATION', cell: (row) => formatDuration(row.track.durationMs) },
  {
    key: 'bpm',
    label: 'BPM',
    sort: 'BPM',
    cell: (row) => (
      <span title={row.track.bpmSource ? `źródło: ${row.track.bpmSource}` : undefined}>
        {row.track.bpm ?? DASH}
      </span>
    ),
  },
  { key: 'key', label: 'Tonacja', cell: keyCell },
  { key: 'energy', label: 'Energia', sort: 'ENERGY', cell: (row) => energyLabel(row.track.energy) },
  { key: 'genre', label: 'Gatunek', cell: (row) => row.track.genreFamily ?? DASH },
  { key: 'rating', label: 'Ocena', sort: 'RATING', cell: ratingCell },
  { key: 'tempo', label: 'Tempo', cell: (row) => tempoLabel(row.track.tempoClass) },
  { key: 'style', label: 'Styl', cell: (row) => row.track.style ?? DASH },
  {
    key: 'danceability',
    label: 'Taneczność',
    sort: 'DANCEABILITY',
    cell: (row) => formatScore(row.track.danceability),
  },
  {
    key: 'popularity',
    label: 'Popularność',
    sort: 'POPULARITY',
    cell: (row) => row.track.popularity ?? DASH,
  },
  {
    key: 'explicit',
    label: 'Explicit',
    cell: (row) => (row.track.explicit === null ? DASH : row.track.explicit ? 'tak' : 'nie'),
  },
  { key: 'bpmSource', label: 'Źródło BPM', cell: (row) => row.track.bpmSource ?? DASH },
  { key: 'tags', label: 'Tagi DJ-a', cell: tagsCell },
  {
    key: 'added',
    label: 'Dodano',
    sort: 'ADDED_AT',
    cell: (row) => formatDate(row.library?.addedAt),
  },
]

export const DEFAULT_COLUMN_KEYS = [
  'title',
  'artist',
  'year',
  'duration',
  'bpm',
  'key',
  'energy',
  'genre',
  'rating',
]

const KNOWN_KEYS = LIBRARY_COLUMNS.map((column) => column.key)
const FIXED_KEYS = LIBRARY_COLUMNS.filter((column) => column.fixed).map((column) => column.key)

/**
 * Kolumny z adresu (`cols`) — nieznane klucze (stary link, literówka) milcząco
 * odpadają, a kolumny stałe wracają zawsze. Pusty i brakujący parametr znaczy
 * zestaw domyślny, więc adres bez `cols` daje ten sam widok co świeże wejście.
 */
export function parseColumns(raw: string | null): string[] {

  const requested = (raw ?? '').split(',').filter((key) => KNOWN_KEYS.includes(key))
  if (requested.length === 0) return DEFAULT_COLUMN_KEYS
  const selected = new Set([...FIXED_KEYS, ...requested])
  return KNOWN_KEYS.filter((key) => selected.has(key))
}

/** Zestaw domyślny nie zaśmieca adresu — parametr znika, gdy nic nie zmieniono. */
export function serializeColumns(keys: readonly string[]): string | undefined {

  const same =
    keys.length === DEFAULT_COLUMN_KEYS.length &&
    DEFAULT_COLUMN_KEYS.every((key) => keys.includes(key))
  return same ? undefined : keys.join(',')
}

export function visibleColumns(keys: readonly string[]): LibraryColumn[] {
  return LIBRARY_COLUMNS.filter((column) => keys.includes(column.key))
}
