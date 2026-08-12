// Kolumny tabeli biblioteki (M5.6, przycięte w M5.7) w jednym miejscu: nagłówek,
// porządek sortowania i sposób narysowania komórki. Wcześniej lista kolumn była
// w tabeli, a wartości w JSX-ie pod nią — dołożenie kolumny znaczyło trafić
// w dwa miejsca naraz i nie pomylić kolejności.
//
// Tabela opisuje wyłącznie utwór (D39): tożsamość, to, czym się go miksuje
// (BPM, tonacja, energia, gatunek), a pod wybierakiem reszta danych katalogu.
// Dane prywatne DJ-a (ocena, tagi, data dodania) i pochodzenie BPM mieszkają
// w szufladzie utworu, gdzie da się je od razu zmienić, zamiast zajmować
// kolumnę powtarzającą to, co widać po kliknięciu.

import type { ReactNode } from 'react'
import type { CatalogRowResponse, CatalogSort } from '../api'
import {
  DASH,
  energyLabel,
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
