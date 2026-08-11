// Logika planowania setu (M3.1) — czyste funkcje liczone po stronie frontu na
// danych zwróconych przez /api/playlists/{id}. Sloty liczy backend (D9/D21);
// tutaj tylko podsumowania i ostrzeżenia dla DJ-a układającego kolejność.

import type { PlaylistTrackResponse } from './api'
import { SLOT_ORDER, type SlotKey } from './format'

/** Próg, powyżej którego skok tempa między sąsiadami trudno przemiksować. */
export const BPM_JUMP_THRESHOLD = 15

/** Skok głośności, powyżej którego przejście słychać jako „skok" (D25). */
export const LOUDNESS_JUMP_THRESHOLD_DB = 3

/** Metrum, którego parkiet się spodziewa; reszta to pułapka na przejściu (D25). */
const EXPECTED_TIME_SIGNATURE = 4

interface Wheel {
  number: number
  minor: boolean
}

/**
 * Etykieta koła Camelot („8A") → pozycja. Samo koło liczy backend z tonacji
 * (D25); tutaj zostaje wyłącznie porównanie dwóch etykiet, bo ostrzeżenia
 * o secie liczy front (D22).
 */
function parseWheel(camelot: string | null): Wheel | null {
  const match = /^(\d{1,2})([AB])$/.exec(camelot ?? '')
  if (!match) return null
  const number = Number(match[1])
  return number >= 1 && number <= 12 ? { number, minor: match[2] === 'A' } : null
}

/** Zgodne: ta sama tonacja, sąsiedzi na kole (±1) i tonacja równoległa. */
export function areKeysCompatible(left: string | null, right: string | null): boolean {
  const first = parseWheel(left)
  const second = parseWheel(right)
  if (!first || !second) return true
  if (first.minor === second.minor) {
    const distance = Math.abs(first.number - second.number)
    return distance <= 1 || distance === 11
  }
  return first.number === second.number
}

export type SlotCounts = Record<SlotKey | 'UNKNOWN', number>

export interface SetStats {
  trackCount: number
  /** Suma czasu znanych utworów — utwory bez `durationMs` jej nie zaniżają celowo. */
  totalDurationMs: number
  tracksWithDuration: number
  bpmMin: number | null
  bpmMax: number | null
  averageBpm: number | null
  slotCounts: SlotCounts
  missingBpm: number
  missingSlot: number
}

export type SetWarningKind =
  | 'BPM_JUMP'
  | 'NO_BPM'
  | 'SLOT_BACKWARDS'
  | 'KEY_CLASH'
  | 'LOUDNESS_JUMP'
  | 'ODD_METER'

export interface SetWarning {
  /** Pozycja w secie liczona od 1 — tak jak widzi ją DJ. */
  position: number
  kind: SetWarningKind
  message: string
}

function emptySlotCounts(): SlotCounts {
  return { WARMUP: 0, MIDDLE: 0, PEAK: 0, CLOSING: 0, BREAK: 0, UNKNOWN: 0 }
}

function slotKey(entry: PlaylistTrackResponse): SlotKey | 'UNKNOWN' {
  const slot = entry.djSlot
  return slot && SLOT_ORDER.includes(slot as SlotKey) ? (slot as SlotKey) : 'UNKNOWN'
}

export function computeSetStats(tracks: readonly PlaylistTrackResponse[]): SetStats {

  const stats: SetStats = {
    trackCount: tracks.length,
    totalDurationMs: 0,
    tracksWithDuration: 0,
    bpmMin: null,
    bpmMax: null,
    averageBpm: null,
    slotCounts: emptySlotCounts(),
    missingBpm: 0,
    missingSlot: 0,
  }

  let bpmSum = 0
  let bpmCount = 0
  tracks.forEach((entry) => {
    const { bpm, durationMs } = entry.track
    if (durationMs !== null && durationMs > 0) {
      stats.totalDurationMs += durationMs
      stats.tracksWithDuration += 1
    }
    if (bpm !== null) {
      bpmSum += bpm
      bpmCount += 1
      stats.bpmMin = stats.bpmMin === null ? bpm : Math.min(stats.bpmMin, bpm)
      stats.bpmMax = stats.bpmMax === null ? bpm : Math.max(stats.bpmMax, bpm)
    } else {
      stats.missingBpm += 1
    }
    const key = slotKey(entry)
    stats.slotCounts[key] += 1
    if (key === 'UNKNOWN') stats.missingSlot += 1
  })

  stats.averageBpm = bpmCount > 0 ? Math.round(bpmSum / bpmCount) : null
  return stats
}

/** Kolejność faz wieczoru; BREAK jest neutralna — wolny kawałek wolno wtrącić wszędzie. */
const PHASE_ORDER: readonly SlotKey[] = ['WARMUP', 'MIDDLE', 'PEAK', 'CLOSING']

function phaseIndex(entry: PlaylistTrackResponse): number {
  const slot = entry.djSlot as SlotKey | null
  return slot ? PHASE_ORDER.indexOf(slot) : -1
}

/**
 * Ostrzeżenia dla ułożonego setu: skoki tempa i głośności między sąsiadami,
 * zderzenia tonacji, utwory bez BPM (nie da się ich zaplanować), metrum inne
 * niż 4/4 i cofnięcia fazy wieczoru (np. szczyt → rozgrzewka).
 */
export function findSetWarnings(tracks: readonly PlaylistTrackResponse[]): SetWarning[] {

  const warnings: SetWarning[] = []
  tracks.forEach((entry, index) => {
    const position = index + 1
    const bpm = entry.track.bpm
    const title = entry.track.title ?? entry.track.spotifyId
    const previous = tracks[index - 1]

    if (bpm === null) {
      warnings.push({
        position,
        kind: 'NO_BPM',
        message: `„${title}" bez BPM — wzbogać utwór, żeby wszedł w planowanie`,
      })
    } else if (previous && previous.track.bpm !== null) {
      const jump = Math.abs(bpm - previous.track.bpm)
      if (jump > BPM_JUMP_THRESHOLD) {
        warnings.push({
          position,
          kind: 'BPM_JUMP',
          message: `skok tempa ${previous.track.bpm} → ${bpm} BPM (${jump}) przed „${title}"`,
        })
      }
    }

    if (entry.timeSignature !== null && entry.timeSignature !== EXPECTED_TIME_SIGNATURE) {
      warnings.push({
        position,
        kind: 'ODD_METER',
        message: `„${title}" w metrum ${entry.timeSignature}/4 — przejście trzeba policzyć ręcznie`,
      })
    }

    if (!previous) return

    if (!areKeysCompatible(previous.track.camelot, entry.track.camelot)) {
      warnings.push({
        position,
        kind: 'KEY_CLASH',
        message:
          `zderzenie tonacji ${previous.track.camelot} → ${entry.track.camelot} przed „${title}"`,
      })
    }

    if (entry.loudnessDb !== null && previous.loudnessDb !== null) {
      const jump = Math.abs(entry.loudnessDb - previous.loudnessDb)
      if (jump > LOUDNESS_JUMP_THRESHOLD_DB) {
        warnings.push({
          position,
          kind: 'LOUDNESS_JUMP',
          message:
            `skok głośności ${previous.loudnessDb} → ${entry.loudnessDb} dB przed „${title}"`,
        })
      }
    }
  })

  tracks.forEach((entry, index) => {
    const previous = tracks[index - 1]
    if (!previous) return
    const current = phaseIndex(entry)
    const before = phaseIndex(previous)
    if (current >= 0 && before >= 0 && current < before) {
      warnings.push({
        position: index + 1,
        kind: 'SLOT_BACKWARDS',
        message: `cofnięcie fazy wieczoru na pozycji ${index + 1} (${previous.djSlot} → ${entry.djSlot})`,
      })
    }
  })

  return warnings.sort((left, right) => left.position - right.position)
}

/**
 * Tryby układania gotowego setu (M4.5, D33). Fazy wieczoru to domyślny sposób
 * i został z M3.1; reszta odpowiada na pytania, których fazy nie obsługują —
 * „chcę płynne przejścia", „chcę czyste miksy", „chcę narastającą energię".
 */
export type ArrangeMode = 'PHASES' | 'TEMPO' | 'HARMONY' | 'ENERGY'

export const ARRANGE_MODES: readonly ArrangeMode[] = ['PHASES', 'TEMPO', 'HARMONY', 'ENERGY']

export const ARRANGE_LABELS: Record<ArrangeMode, string> = {
  PHASES: 'wg faz wieczoru',
  TEMPO: 'wg narastającego tempa',
  HARMONY: 'wg zgodności tonacji',
  ENERGY: 'wg narastającej energii',
}

/** Kolejność energii na wieczór; utwór bez wartości ląduje na końcu grupy. */
const ENERGY_ORDER: readonly string[] = ['low', 'medium', 'high']

/** Utwór bez BPM idzie na koniec, a nie na początek — `null` to brak danych, nie zero. */
function bpmOrLast(entry: PlaylistTrackResponse): number {
  return entry.track.bpm ?? Number.POSITIVE_INFINITY
}

function energyRank(entry: PlaylistTrackResponse): number {
  const index = ENERGY_ORDER.indexOf(entry.track.energy ?? '')
  return index === -1 ? ENERGY_ORDER.length : index
}

/**
 * Koszt przejścia między sąsiadami przy układaniu harmonicznym: zderzenie
 * tonacji przeważa nad każdym skokiem tempa, bo tego nie da się przemiksować.
 * Nieznane BPM po którejś stronie wyceniamy jak spory skok — nie wiemy, czy
 * przejście zagra, więc nie stawiamy go przed przejściem, o którym wiemy.
 */
function transitionCost(from: PlaylistTrackResponse, to: PlaylistTrackResponse): number {
  const clash = areKeysCompatible(from.track.camelot, to.track.camelot) ? 0 : 1000
  const before = from.track.bpm
  const after = to.track.bpm
  const jump = before === null || after === null ? 40 : Math.abs(after - before)
  return clash + jump
}

/**
 * Łańcuch harmoniczny: pierwszy utwór zostaje na miejscu (DJ wybrał otwarcie),
 * a każdy kolejny to najtańsze przejście z tego, co zostało. Zachłannie, bo
 * przy kilkudziesięciu utworach optymalna trasa to problem komiwojażera,
 * a DJ i tak poprawia wynik ręcznie.
 */
function arrangeByHarmony(tracks: readonly PlaylistTrackResponse[]): string[] {

  const remaining = [...tracks]
  const chain: PlaylistTrackResponse[] = [remaining.shift()!]
  while (remaining.length > 0) {
    const previous = chain[chain.length - 1]
    let best = 0
    remaining.forEach((entry, index) => {
      if (transitionCost(previous, entry) < transitionCost(previous, remaining[best])) best = index
    })
    chain.push(remaining.splice(best, 1)[0])
  }
  return chain.map((entry) => entry.track.spotifyId)
}

/**
 * Propozycja kolejności setu w wybranym trybie. Zwraca listę `spotifyId`
 * gotową do `PUT /api/playlists/{id}/tracks` — układanie liczy front (D22),
 * backend dostaje gotową permutację składu (D21).
 */
export function arrangeBy(
  tracks: readonly PlaylistTrackResponse[],
  mode: ArrangeMode,
): string[] {

  if (tracks.length < 2) return tracks.map((entry) => entry.track.spotifyId)
  switch (mode) {
    case 'PHASES':
      return arrangeBySlot(tracks)
    case 'TEMPO':
      return [...tracks]
        .sort((left, right) => bpmOrLast(left) - bpmOrLast(right))
        .map((entry) => entry.track.spotifyId)
    case 'ENERGY':
      return [...tracks]
        .sort((left, right) => energyRank(left) - energyRank(right) || bpmOrLast(left) - bpmOrLast(right))
        .map((entry) => entry.track.spotifyId)
    case 'HARMONY':
      return arrangeByHarmony(tracks)
  }
}

/**
 * Kolejność setu po wstawieniu dobranego utworu (M4.4) na wskazane miejsce.
 * API dokłada utwór wyłącznie na koniec (`POST /{id}/tracks`), więc wstawienie
 * w środek to dopisanie i zaraz po nim zmiana kolejności — nowego endpointu
 * do zapisu nie ma i nie potrzeba (D32).
 *
 * @param tracks   skład setu **po** dopisaniu utworu (dobrany jest ostatni)
 * @param position docelowe miejsce; poza zakresem zostawia utwór na końcu
 */
export function insertLastAt(
  tracks: readonly PlaylistTrackResponse[],
  position: number,
): string[] {

  const order = tracks.map((entry) => entry.track.spotifyId)
  if (position < 0 || position >= order.length) return order
  const [added] = order.splice(order.length - 1, 1)
  order.splice(position, 0, added)
  return order
}

/**
 * Propozycja kolejności setu wg slotów D9: rozgrzewka → środek → szczyt →
 * zamknięcie, wewnątrz fazy rosnąco po BPM. Przerwy trafiają przed zamknięcie,
 * a utwory bez slotu (niewzbogacone) na sam koniec — DJ decyduje, co z nimi.
 * Zwraca listę `spotifyId` gotową do PUT /api/playlists/{id}/tracks.
 */
export function arrangeBySlot(tracks: readonly PlaylistTrackResponse[]): string[] {

  const groupOrder: (SlotKey | 'UNKNOWN')[] = [
    'WARMUP',
    'MIDDLE',
    'PEAK',
    'BREAK',
    'CLOSING',
    'UNKNOWN',
  ]
  const byGroup = new Map<SlotKey | 'UNKNOWN', PlaylistTrackResponse[]>(
    groupOrder.map((group) => [group, []]),
  )
  tracks.forEach((entry) => byGroup.get(slotKey(entry))!.push(entry))

  return groupOrder.flatMap((group) =>
    [...byGroup.get(group)!]
      .sort((left, right) => (left.track.bpm ?? 0) - (right.track.bpm ?? 0))
      .map((entry) => entry.track.spotifyId),
  )
}
