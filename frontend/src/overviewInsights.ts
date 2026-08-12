// Wnioski z przeglądu biblioteki (M5.4) — czysta logika ekranu, bez JSX.
//
// Backend podaje surowe rozkłady (D27), a nie zdania po polsku. To, co da się
// policzyć z tego, co już przyszło — narastający przyrost, udział faktów, gęsty
// przedział tempa, tonacje zgodne harmonicznie (D25) — liczy się tutaj, żeby
// dało się to sprawdzić testem bez renderowania całego pulpitu.

import type {
  BucketResponse,
  LibraryOverviewResponse,
  MatrixCellResponse,
} from './api'

/** Źródła BPM, które są pomiarem, a nie zgadywanką modelu (kaskada D6/D24). */
export const MEASURED_BPM_SOURCES = ['MANUAL', 'ACOUSTICBRAINZ', 'DEEZER'] as const

export const NO_KEY = 'BEZ TONACJI'

export const NO_YEAR = 'BEZ ROKU'

/** Czas w ms jako „m:ss" — średnia długość utworu czyta się tak, a nie w minutach. */
export function formatMinutes(durationMs: number): string {

  const totalSeconds = Math.round(durationMs / 1000)
  return `${Math.floor(totalSeconds / 60)}:${String(totalSeconds % 60).padStart(2, '0')}`
}

export interface CoveragePart {
  label: string
  covered: number
  total: number
  ratio: number
}

export interface Insight {
  /** Klucz techniczny — testy i `key` Reacta; użytkownik widzi `label`. */
  id: string
  label: string
  value: string
  hint: string
}

export function sumBuckets(buckets: readonly BucketResponse[]): number {
  return buckets.reduce((sum, bucket) => sum + bucket.count, 0)
}

export function largestBucket(
  buckets: readonly BucketResponse[],
  skip: readonly string[] = [],
): BucketResponse | null {
  return buckets
    .filter((bucket) => !skip.includes(bucket.label))
    .reduce<BucketResponse | null>(
      (best, bucket) => (best === null || bucket.count > best.count ? bucket : best),
      null,
    )
}

export function share(part: number, total: number): number {
  return total <= 0 ? 0 : part / total
}

export function percentLabel(part: number, total: number): string {
  return `${Math.round(share(part, total) * 100)}%`
}

/**
 * Przyrost narastająco. Backend daje ostatnie 12 miesięcy, więc suma tych
 * słupków nie musi być całą biblioteką — kotwiczymy krzywą od końca: ostatni
 * punkt to stan dzisiejszy, wcześniejsze odejmują kolejne miesiące wstecz.
 */
export function cumulativeGrowth(
  monthly: readonly BucketResponse[],
  libraryTracks: number,
): BucketResponse[] {

  let running = libraryTracks
  const reversed = [...monthly].reverse().map((bucket) => {
    const point = { label: bucket.label, count: running }
    running -= bucket.count
    return point
  })
  return reversed.reverse()
}

/** Kompletność danych per grupa pól (D11) plus pokrycie metrykami z pliku (D24). */
export function coverageParts(overview: LibraryOverviewResponse): CoveragePart[] {

  const total = overview.scale.catalogTracks
  const { metadataMissing, audioMissing, aiMissing } = overview.quality
  return [
    part('metadane', total - metadataMissing, total),
    part('cechy audio', total - audioMissing, total),
    part('analiza AI', total - aiMissing, total),
    part('metryki z pliku', overview.scale.tracksWithMetrics, total),
  ]
}

/** Jedna liczba „na ile biblioteka jest gotowa" — średnia z pokryć. */
export function readinessScore(overview: LibraryOverviewResponse): number {

  const parts = coverageParts(overview)
  return parts.reduce((sum, item) => sum + item.ratio, 0) / parts.length
}

/**
 * Ile tempa stoi na pomiarze, a ile na estymacie LLM — wskaźnik, który D19
 * uczynił kryterium decyzji o `AudioAnalyzer`.
 */
export function bpmFacts(bpmSources: readonly BucketResponse[]) {

  const measured = bpmSources
    .filter((bucket) => (MEASURED_BPM_SOURCES as readonly string[]).includes(bucket.label))
    .reduce((sum, bucket) => sum + bucket.count, 0)
  const estimated = bpmSources
    .filter((bucket) => bucket.label === 'LLM')
    .reduce((sum, bucket) => sum + bucket.count, 0)
  const known = measured + estimated
  return { measured, estimated, known, ratio: share(measured, known) }
}

/** Zgodne pozycje koła (D25): ta sama, sąsiedzi ±1 i tonacja równoległa. */
export function compatibleCamelot(label: string): string[] {

  const match = /^(\d{1,2})([AB])$/.exec(label)
  if (match === null) return []
  const number = Number(match[1])
  const letter = match[2]
  if (number < 1 || number > 12) return []
  const other = letter === 'A' ? 'B' : 'A'
  return [
    `${(number % 12) + 1}${letter}`,
    `${((number + 10) % 12) + 1}${letter}`,
    `${number}${other}`,
  ]
}

/** Macierz tempo × energia jako gęsta siatka — brakujące pary to zera, nie dziury. */
export function matrixGrid(
  cells: readonly MatrixCellResponse[],
  rows: readonly string[],
  columns: readonly string[],
): number[][] {

  const byPair = new Map(cells.map((cell) => [`${cell.tempoClass}|${cell.energy}`, cell.count]))
  return rows.map((row) => columns.map((column) => byPair.get(`${row}|${column}`) ?? 0))
}

/**
 * Zdania, które DJ przeczyta zamiast wpatrywać się w słupki. Wniosek bez danych
 * nie wchodzi na ekran — puste miejsce jest uczciwsze niż „—" pod nagłówkiem.
 */
export function insights(overview: LibraryOverviewResponse): Insight[] {

  const found: Insight[] = []
  const densest = largestBucket(overview.sound.bpmHistogram)
  if (densest !== null) {
    found.push({
      id: 'bpm',
      label: 'najgęstsze tempo',
      value: `${densest.label} BPM`,
      hint: `${densest.count} utworów siedzi w tym przedziale — to trzon biblioteki`,
    })
  }

  const key = largestBucket(overview.sound.camelotKeys, [NO_KEY])
  if (key !== null) {
    found.push({
      id: 'key',
      label: 'dominująca tonacja',
      value: key.label,
      hint: `${key.count} utworów; wchodzą z niej ${compatibleCamelot(key.label).join(', ')}`,
    })
  }

  const facts = bpmFacts(overview.quality.bpmSources)
  if (facts.known > 0) {
    found.push({
      id: 'facts',
      label: 'tempo z pomiaru',
      value: percentLabel(facts.measured, facts.known),
      hint: `${facts.estimated} utworów ma BPM wyłącznie z estymaty modelu (D19)`,
    })
  }

  const decade = largestBucket(overview.timeline.decades, [NO_YEAR])
  if (decade !== null) {
    found.push({
      id: 'decade',
      label: 'dominujący rocznik',
      value: decade.label,
      hint: `${decade.count} utworów, czyli ${percentLabel(decade.count, overview.scale.catalogTracks)} katalogu`,
    })
  }

  const averageDuration = overview.scale.averageDurationMs
  if (averageDuration !== null) {
    found.push({
      id: 'length',
      label: 'średnia długość',
      value: formatMinutes(averageDuration),
      hint: 'przeciętny utwór w katalogu, licząc po polu z metadanych',
    })
  }

  const bestMonth = largestBucket(overview.timeline.monthlyGrowth)
  if (bestMonth !== null && bestMonth.count > 0) {
    found.push({
      id: 'month',
      label: 'najlepszy miesiąc',
      value: bestMonth.label,
      hint: `${bestMonth.count} nowych utworów w bibliotece`,
    })
  }

  const artist = overview.taste.topArtists[0]
  if (artist !== undefined) {
    found.push({
      id: 'artist',
      label: 'najczęstszy wykonawca',
      value: artist.label,
      hint: `${artist.count} utworów, czyli ${percentLabel(artist.count, overview.scale.libraryTracks)} biblioteki`,
    })
  }

  return found
}

function part(label: string, covered: number, total: number): CoveragePart {
  return { label, covered, total, ratio: share(covered, total) }
}
