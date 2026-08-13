// Adres jako źródło prawdy ekranu Biblioteka (M3.1, uporządkowane w M5.6,
// przycięte w M5.7): jedno miejsce, które wie, jak parametr w hashu nazywa się
// w URL-u, jak nazywa się w API i jak brzmi po polsku na chipsie aktywnych
// filtrów. Rozjazd między tymi trzema listami był głównym kosztem dokładania
// kolejnego filtra.
//
// Lista jest krótsza niż w M5.6 (D39): parametry, których ekran już nie stawia
// (`lib`, `tag`, `yearMin/yearMax`, `popMin`, `explicit`, metryki z pliku,
// `bpmSrc`, `missing`), nie są też czytane — stary link nie może filtrować
// czymś, czego nie da się na ekranie zobaczyć ani zdjąć.

import type { CatalogSort, SearchParams, SortDirection } from '../api'
import { ENERGY_LABELS, TEMPO_LABELS, formatDuration } from '../format'

export const DEFAULT_PAGE_SIZE = 20

/** Wielkości strony — od „przeglądam" po „chcę mieć całą półkę pod ręką". */
export const PAGE_SIZES = [20, 50, 100, 200, 500] as const

/**
 * Filtry „pierwszego rzutu" zostają zawsze na wierzchu; reszta chowa się
 * w panelu, który otwiera się sam, gdy któryś z jego filtrów jest aktywny.
 */
const BASIC_KEYS = ['q', 'genre', 'rating']

/** Wszystkie parametry filtrów — kolejność bez znaczenia, komplet ma znaczenie. */
export const FILTER_KEYS = [
  'q',
  'genre',
  'rating',
  'durMin',
  'durMax',
  'bpmMin',
  'bpmMax',
  'tempo',
  'energy',
  'key',
  'keyExact',
]

function text(params: URLSearchParams, key: string): string | undefined {
  const value = params.get(key)?.trim()
  return value ? value : undefined
}

/** Śmieci w adresie (ręcznie wklejone „abc") znaczą brak filtra, nie NaN w zapytaniu. */
function num(params: URLSearchParams, key: string): number | undefined {
  const value = text(params, key)
  if (value === undefined) return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

/**
 * Request z adresu ma zawsze komplet porządku i strony — dopiero to pozwala
 * widokowi czytać sortowanie i numer strony z jednego miejsca zamiast z hasha
 * po raz drugi.
 */
export interface LibraryRequest extends SearchParams {
  sort: CatalogSort
  direction: SortDirection
  page: number
  size: number
}

/**
 * Cały request wyszukiwarki wyliczony z adresu — łącznie z sortowaniem
 * i stronicowaniem. Widok nie trzyma żadnego stanu filtrów obok tego.
 */
export function searchRequest(params: URLSearchParams): LibraryRequest {

  const camelot = text(params, 'key')
  return {
    search: text(params, 'q'),
    genreFamily: text(params, 'genre'),
    durationMinSec: num(params, 'durMin'),
    durationMaxSec: num(params, 'durMax'),
    bpmMin: num(params, 'bpmMin'),
    bpmMax: num(params, 'bpmMax'),
    tempoClass: text(params, 'tempo'),
    energy: text(params, 'energy'),
    ratingMin: num(params, 'rating'),
    camelot,
    camelotCompatible: camelot ? params.get('keyExact') !== '1' : undefined,
    sort: (text(params, 'sort') ?? 'RELEVANCE') as CatalogSort,
    direction: (text(params, 'dir') ?? 'ASC') as SortDirection,
    page: num(params, 'page') ?? 0,
    size: num(params, 'size') ?? DEFAULT_PAGE_SIZE,
  }
}

/** Jeden chips nad tabelą: co jest włączone i czym to wyłączyć. */
export interface ActiveFilter {
  /** Parametry adresu, które kasuje kliknięcie w krzyżyk. */
  keys: string[]
  label: string
}

/** „od–do" po ludzku: jedna granica opisuje się inaczej niż zakres. */
function rangeLabel(
  name: string,
  min: string | undefined,
  max: string | undefined,
  format: (value: string) => string = (value) => value,
): string | undefined {

  if (min !== undefined && max !== undefined) return `${name} ${format(min)}–${format(max)}`
  if (min !== undefined) return `${name} od ${format(min)}`
  if (max !== undefined) return `${name} do ${format(max)}`
  return undefined
}

const seconds = (value: string) => formatDuration(Number(value) * 1000)

export function activeFilters(params: URLSearchParams): ActiveFilter[] {

  const value = (key: string) => text(params, key)
  const range = (keys: [string, string], name: string, format?: (raw: string) => string) => ({
    keys,
    label: rangeLabel(name, value(keys[0]), value(keys[1]), format),
  })
  const camelot = value('key')
  const tempo = value('tempo')
  const energy = value('energy')
  const candidates: { keys: string[]; label: string | undefined }[] = [
    { keys: ['q'], label: value('q') && `„${value('q')}"` },
    { keys: ['genre'], label: value('genre') && `gatunek: ${value('genre')}` },
    { keys: ['rating'], label: value('rating') && `ocena ≥ ${value('rating')}★` },
    range(['durMin', 'durMax'], 'czas', seconds),
    range(['bpmMin', 'bpmMax'], 'BPM'),
    { keys: ['tempo'], label: tempo && `tempo: ${TEMPO_LABELS[tempo] ?? tempo}` },
    { keys: ['energy'], label: energy && `energia: ${ENERGY_LABELS[energy] ?? energy}` },
    {
      keys: ['key', 'keyExact'],
      label: camelot && `tonacja ${camelot}${params.get('keyExact') === '1' ? '' : ' + zgodne'}`,
    },
  ]
  return candidates.filter((filter): filter is ActiveFilter => Boolean(filter.label))
}

/** Czy panel „więcej filtrów" ma się otworzyć sam — bo coś w nim siedzi. */
export function advancedFiltersActive(params: URLSearchParams): boolean {
  return activeFilters(params).some((filter) =>
    filter.keys.some((key) => !BASIC_KEYS.includes(key)),
  )
}

/** Patch kasujący komplet filtrów; strona wraca na pierwszą. */
export function clearFiltersPatch(): Record<string, undefined> {
  return Object.fromEntries([...FILTER_KEYS, 'page'].map((key) => [key, undefined]))
}
