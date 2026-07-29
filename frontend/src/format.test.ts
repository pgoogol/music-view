import { describe, expect, it } from 'vitest'
import {
  DASH,
  energyLabel,
  formatDuration,
  formatScore,
  formatTotalDuration,
  isEnriched,
  slotLabel,
  spotifyTrackUrl,
} from './format'

describe('formatDuration', () => {

  it('pokazuje czas utworu jako minuty i sekundy z zerem wiodącym', () => {
    expect(formatDuration(252_306)).toBe('4:12')
    expect(formatDuration(65_000)).toBe('1:05')
  })

  it('brak lub zerowy czas pokazuje jako myślnik', () => {
    expect(formatDuration(null)).toBe(DASH)
    expect(formatDuration(0)).toBe(DASH)
  })
})

describe('formatTotalDuration', () => {

  it('długość setu podaje w godzinach i minutach', () => {
    expect(formatTotalDuration(4_320_000)).toBe('1 h 12 min')
  })

  it('krótki set zostaje w minutach', () => {
    expect(formatTotalDuration(600_000)).toBe('10 min')
  })
})

describe('etykiety', () => {

  it('tłumaczy energię i slot na polskie nazwy', () => {
    expect(energyLabel('high')).toBe('wysoka')
    expect(slotLabel('PEAK')).toBe('szczyt')
  })

  it('nieznaną wartość pokazuje bez zmian, a brak slotu opisuje słownie', () => {
    expect(energyLabel('extreme')).toBe('extreme')
    expect(slotLabel(null)).toBe('brak danych')
  })
})

describe('isEnriched', () => {

  it('utwór bez BPM albo bez gatunku wymaga wzbogacenia', () => {
    expect(isEnriched({ genreFamily: 'LATIN', bpm: 92 })).toBe(true)
    expect(isEnriched({ genreFamily: null, bpm: 92 })).toBe(false)
    expect(isEnriched({ genreFamily: 'LATIN', bpm: null })).toBe(false)
  })
})

describe('formatScore', () => {

  it('cechę 0..1 pokazuje w skali 0-100, brak danych jako myślnik', () => {
    expect(formatScore(0.89)).toBe('89')
    expect(formatScore(0)).toBe('0')
    expect(formatScore(null)).toBe('—')
  })
})

describe('spotifyTrackUrl', () => {

  it('buduje link do utworu na Spotify', () => {
    expect(spotifyTrackUrl('4Xnjfr2q6d3b1KvpFDF9tZ')).toBe(
      'https://open.spotify.com/track/4Xnjfr2q6d3b1KvpFDF9tZ',
    )
  })
})
