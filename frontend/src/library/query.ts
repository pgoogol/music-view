// Adres jako źródło prawdy ekranu Biblioteka (M3.1, uporządkowane w M5.6):
// jedno miejsce, które wie, jak parametr w hashu nazywa się w URL-u, jak nazywa
// się w API i jak brzmi po polsku na chipsie aktywnych filtrów. Rozjazd między
// tymi trzema listami był głównym kosztem dokładania kolejnego filtra.

import type { CatalogSort, SearchParams, SortDirection } from '../api'
import { ENERGY_LABELS, TEMPO_LABELS, formatDuration } from '../format'

export const DEFAULT_PAGE_SIZE = 20

/** Wielkości strony — od „przeglądam" po „chcę mieć całą półkę pod ręką". */
export const PAGE_SIZES = [20, 50, 100, 200, 500] as const

export const MISSING_LABELS: Record<string, string> = {
  ANY: 'do wzbogacenia',
  METADATA: 'braki: metadane',
  AUDIO: 'braki: cechy audio',
  AI: 'braki: opis AI',
}

/**
 * Filtry „pierwszego rzutu" zostają zawsze na wierzchu; reszta chowa się
 * w panelu, który otwiera się sam, gdy któryś z jego filtrów jest aktywny.
 */
const BASIC_KEYS = ['q', 'lib', 'genre', 'rating']

/** Wszystkie parametry filtrów — kolejność bez znaczenia, komplet ma znaczenie. */
export const FILTER_KEYS = [
  'q',
  'lib',
  'genre',
  'rating',
  'tag',
  'yearMin',
  'yearMax',
  'durMin',
  'durMax',
  'popMin',
  'explicit',
  'bpmMin',
  'bpmMax',
  'tempo',
  'energy',
  'key',
  'keyExact',
  'valMin',
  'valMax',
  'instr',
  'live',
  'bpmSrc',
  'missing',
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

/** `1`/`0` w adresie; cokolwiek innego = bez filtra. */
function flag(params: URLSearchParams, key: string): boolean | undefined {
  const value = text(params, key)
  if (value === '1') return true
  if (value === '0') return false
  return undefined
}

/** `lib` w adresie: yes = tylko z biblioteki, no = tylko spoza; brak = bez filtra. */
export function parseInLibrary(value: string | undefined): boolean | undefined {
  if (value === 'yes') return true
  if (value === 'no') return false
  return undefined
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
    yearMin: num(params, 'yearMin'),
    yearMax: num(params, 'yearMax'),
    durationMinSec: num(params, 'durMin'),
    durationMaxSec: num(params, 'durMax'),
    popularityMin: num(params, 'popMin'),
    explicit: flag(params, 'explicit'),
    bpmMin: num(params, 'bpmMin'),
    bpmMax: num(params, 'bpmMax'),
    tempoClass: text(params, 'tempo'),
    energy: text(params, 'energy'),
    inLibrary: parseInLibrary(text(params, 'lib')),
    ratingMin: num(params, 'rating'),
    tag: text(params, 'tag'),
    camelot,
    camelotCompatible: camelot ? params.get('keyExact') !== '1' : undefined,
    valenceMin: num(params, 'valMin'),
    valenceMax: num(params, 'valMax'),
    instrumentalMin: num(params, 'instr'),
    livenessMax: num(params, 'live'),
    bpmSource: text(params, 'bpmSrc'),
    missing: text(params, 'missing') as SearchParams['missing'],
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

const LIBRARY_LABELS: Record<string, string> = {
  yes: 'tylko w bibliotece',
  no: 'tylko spoza biblioteki',
}

const EXPLICIT_LABELS: Record<string, string> = { '1': 'tylko explicit', '0': 'bez explicit' }

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
    { keys: ['lib'], label: LIBRARY_LABELS[value('lib') ?? ''] },
    { keys: ['genre'], label: value('genre') && `gatunek: ${value('genre')}` },
    { keys: ['rating'], label: value('rating') && `ocena ≥ ${value('rating')}★` },
    { keys: ['tag'], label: value('tag') && `tag: ${value('tag')}` },
    range(['yearMin', 'yearMax'], 'rok'),
    range(['durMin', 'durMax'], 'czas', seconds),
    { keys: ['popMin'], label: value('popMin') && `popularność ≥ ${value('popMin')}` },
    { keys: ['explicit'], label: EXPLICIT_LABELS[value('explicit') ?? ''] },
    range(['bpmMin', 'bpmMax'], 'BPM'),
    { keys: ['tempo'], label: tempo && `tempo: ${TEMPO_LABELS[tempo] ?? tempo}` },
    { keys: ['energy'], label: energy && `energia: ${ENERGY_LABELS[energy] ?? energy}` },
    {
      keys: ['key', 'keyExact'],
      label: camelot && `tonacja ${camelot}${params.get('keyExact') === '1' ? '' : ' + zgodne'}`,
    },
    range(['valMin', 'valMax'], 'nastrój'),
    { keys: ['instr'], label: value('instr') && `instrumentalność ≥ ${value('instr')}` },
    { keys: ['live'], label: value('live') && `koncertowość ≤ ${value('live')}` },
    { keys: ['bpmSrc'], label: value('bpmSrc') && `źródło BPM: ${value('bpmSrc')}` },
    { keys: ['missing'], label: MISSING_LABELS[value('missing') ?? ''] },
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
