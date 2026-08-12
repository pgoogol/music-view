// Formatery i etykiety PL wspólne dla widoków (M3.1).

export const SLOT_ORDER = ['WARMUP', 'MIDDLE', 'PEAK', 'CLOSING', 'BREAK'] as const

export type SlotKey = (typeof SLOT_ORDER)[number]

export const SLOT_LABELS: Record<SlotKey, string> = {
  WARMUP: 'rozgrzewka',
  MIDDLE: 'środek',
  PEAK: 'szczyt',
  CLOSING: 'zamknięcie',
  BREAK: 'przerwa',
}

export const ENERGY_LABELS: Record<string, string> = {
  low: 'niska',
  medium: 'średnia',
  high: 'wysoka',
}

export const TEMPO_LABELS: Record<string, string> = {
  SLOW: 'wolne',
  MEDIUM: 'średnie',
  FAST: 'szybkie',
  VERY_FAST: 'bardzo szybkie',
}

export const SOURCE_LABELS: Record<string, string> = {
  FILE: 'plik CSV',
  PLAYLIST: 'własna playlista',
  FOREIGN_PLAYLIST: 'cudza playlista',
}

export const DASH = '—'

export function slotLabel(slot: string | null | undefined): string {
  if (!slot) return 'brak danych'
  return SLOT_LABELS[slot as SlotKey] ?? slot
}

export function energyLabel(energy: string | null | undefined): string {
  if (!energy) return DASH
  return ENERGY_LABELS[energy.toLowerCase()] ?? energy
}

export function tempoLabel(tempoClass: string | null | undefined): string {
  if (!tempoClass) return DASH
  return TEMPO_LABELS[tempoClass] ?? tempoClass
}

/** Czas utworu jako m:ss; brak danych → myślnik. */
export function formatDuration(durationMs: number | null | undefined): string {

  if (durationMs === null || durationMs === undefined || durationMs <= 0) return DASH
  const totalSeconds = Math.round(durationMs / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

/** Łączny czas setu — godziny i minuty, bo sety liczy się w kwadransach. */
export function formatTotalDuration(durationMs: number): string {

  if (durationMs <= 0) return '0 min'
  const totalMinutes = Math.round(durationMs / 60000)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  return hours > 0 ? `${hours} h ${minutes} min` : `${minutes} min`
}

/** Cecha audio 0..1 z metryk (D24) na skalę 0-100, w jakiej podaje ją eksport. */
export function formatScore(value: number | null | undefined): string {
  return value === null || value === undefined ? DASH : String(Math.round(value * 100))
}

export function formatDateTime(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString('pl') : DASH
}

/** Sama data — w tabeli godzina dodania utworu do biblioteki tylko szerzy kolumnę. */
export function formatDate(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleDateString('pl') : DASH
}

export function spotifyTrackUrl(spotifyId: string): string {
  return `https://open.spotify.com/track/${encodeURIComponent(spotifyId)}`
}

/** Utwór bez kompletu pól D5 — front oznacza go jako „do wzbogacenia". */
export function isEnriched(track: { genreFamily: string | null; bpm: number | null }): boolean {
  return track.genreFamily !== null && track.bpm !== null
}
