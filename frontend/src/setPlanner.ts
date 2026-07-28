// Logika planowania setu (M3.1) — czyste funkcje liczone po stronie frontu na
// danych zwróconych przez /api/playlists/{id}. Sloty liczy backend (D9/D21);
// tutaj tylko podsumowania i ostrzeżenia dla DJ-a układającego kolejność.

import type { PlaylistTrackResponse } from './api'
import { SLOT_ORDER, type SlotKey } from './format'

/** Próg, powyżej którego skok tempa między sąsiadami trudno przemiksować. */
export const BPM_JUMP_THRESHOLD = 15

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

export type SetWarningKind = 'BPM_JUMP' | 'NO_BPM' | 'SLOT_BACKWARDS'

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
 * Ostrzeżenia dla ułożonego setu: skoki tempa między sąsiadami, utwory bez BPM
 * (nie da się ich zaplanować) i cofnięcia fazy wieczoru (np. szczyt → rozgrzewka).
 */
export function findSetWarnings(tracks: readonly PlaylistTrackResponse[]): SetWarning[] {

  const warnings: SetWarning[] = []
  tracks.forEach((entry, index) => {
    const position = index + 1
    const bpm = entry.track.bpm
    const title = entry.track.title ?? entry.track.spotifyId

    if (bpm === null) {
      warnings.push({
        position,
        kind: 'NO_BPM',
        message: `„${title}" bez BPM — wzbogać utwór, żeby wszedł w planowanie`,
      })
      return
    }

    const previous = tracks[index - 1]
    if (previous && previous.track.bpm !== null) {
      const jump = Math.abs(bpm - previous.track.bpm)
      if (jump > BPM_JUMP_THRESHOLD) {
        warnings.push({
          position,
          kind: 'BPM_JUMP',
          message: `skok tempa ${previous.track.bpm} → ${bpm} BPM (${jump}) przed „${title}"`,
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
