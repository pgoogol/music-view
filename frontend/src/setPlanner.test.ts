import { describe, expect, it } from 'vitest'
import { aPlaylistTrack } from './test/fixtures'
import { arrangeBySlot, computeSetStats, findSetWarnings } from './setPlanner'

describe('computeSetStats', () => {

  it('sumuje czas i zakres BPM tylko po utworach, które mają te dane', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'a', durationMs: 200_000, bpm: 90 }, 'WARMUP'),
      aPlaylistTrack({ spotifyId: 'b', durationMs: 100_000, bpm: 130 }, 'PEAK'),
      aPlaylistTrack({ spotifyId: 'c', durationMs: null, bpm: null }, null),
    ]

    const stats = computeSetStats(tracks)

    expect(stats.trackCount).toBe(3)
    expect(stats.totalDurationMs).toBe(300_000)
    expect(stats.tracksWithDuration).toBe(2)
    expect(stats.bpmMin).toBe(90)
    expect(stats.bpmMax).toBe(130)
    expect(stats.averageBpm).toBe(110)
    expect(stats.missingBpm).toBe(1)
  })

  it('liczy rozkład faz wieczoru, a utwory bez slotu wrzuca do UNKNOWN', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'a' }, 'WARMUP'),
      aPlaylistTrack({ spotifyId: 'b' }, 'PEAK'),
      aPlaylistTrack({ spotifyId: 'c' }, 'PEAK'),
      aPlaylistTrack({ spotifyId: 'd' }, null),
    ]

    const stats = computeSetStats(tracks)

    expect(stats.slotCounts.WARMUP).toBe(1)
    expect(stats.slotCounts.PEAK).toBe(2)
    expect(stats.slotCounts.UNKNOWN).toBe(1)
    expect(stats.missingSlot).toBe(1)
  })

  it('dla pustego setu nie wylicza średniej ani zakresu', () => {

    const stats = computeSetStats([])

    expect(stats.trackCount).toBe(0)
    expect(stats.averageBpm).toBeNull()
    expect(stats.bpmMin).toBeNull()
  })
})

describe('findSetWarnings', () => {

  it('zgłasza skok tempa powyżej progu między sąsiadami', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'a', bpm: 95 }, 'WARMUP'),
      aPlaylistTrack({ spotifyId: 'b', bpm: 130 }, 'PEAK'),
    ]

    const warnings = findSetWarnings(tracks)

    expect(warnings).toHaveLength(1)
    expect(warnings[0]).toMatchObject({ kind: 'BPM_JUMP', position: 2 })
    expect(warnings[0].message).toContain('95 → 130')
  })

  it('nie zgłasza łagodnego przejścia tempa', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'a', bpm: 100 }, 'MIDDLE'),
      aPlaylistTrack({ spotifyId: 'b', bpm: 110 }, 'MIDDLE'),
    ]

    expect(findSetWarnings(tracks)).toHaveLength(0)
  })

  it('zgłasza utwór bez BPM zamiast liczyć od niego skok', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'a', bpm: 100 }, 'MIDDLE'),
      aPlaylistTrack({ spotifyId: 'b', title: 'Nowość', bpm: null }, null),
    ]

    const warnings = findSetWarnings(tracks)

    expect(warnings.map((warning) => warning.kind)).toEqual(['NO_BPM'])
    expect(warnings[0].message).toContain('Nowość')
  })

  it('zgłasza cofnięcie fazy wieczoru, ale przerwy nie traktuje jak cofnięcia', () => {

    const backwards = [
      aPlaylistTrack({ spotifyId: 'a', bpm: 130 }, 'PEAK'),
      aPlaylistTrack({ spotifyId: 'b', bpm: 128 }, 'WARMUP'),
    ]
    const withBreak = [
      aPlaylistTrack({ spotifyId: 'a', bpm: 130 }, 'PEAK'),
      aPlaylistTrack({ spotifyId: 'b', bpm: 128 }, 'BREAK'),
    ]

    expect(findSetWarnings(backwards).map((warning) => warning.kind)).toContain('SLOT_BACKWARDS')
    expect(findSetWarnings(withBreak).map((warning) => warning.kind)).not.toContain('SLOT_BACKWARDS')
  })
})

describe('arrangeBySlot', () => {

  it('układa set fazami wieczoru, a wewnątrz fazy rosnąco po BPM', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'peak-fast', bpm: 140 }, 'PEAK'),
      aPlaylistTrack({ spotifyId: 'warmup', bpm: 90 }, 'WARMUP'),
      aPlaylistTrack({ spotifyId: 'peak-slow', bpm: 125 }, 'PEAK'),
      aPlaylistTrack({ spotifyId: 'closing', bpm: 118 }, 'CLOSING'),
      aPlaylistTrack({ spotifyId: 'middle', bpm: 105 }, 'MIDDLE'),
    ]

    expect(arrangeBySlot(tracks)).toEqual([
      'warmup',
      'middle',
      'peak-slow',
      'peak-fast',
      'closing',
    ])
  })

  it('przerwę wstawia przed zamknięcie, a utwory bez slotu na koniec', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'nieznany', bpm: null }, null),
      aPlaylistTrack({ spotifyId: 'closing', bpm: 118 }, 'CLOSING'),
      aPlaylistTrack({ spotifyId: 'break', bpm: 70 }, 'BREAK'),
      aPlaylistTrack({ spotifyId: 'peak', bpm: 130 }, 'PEAK'),
    ]

    expect(arrangeBySlot(tracks)).toEqual(['peak', 'break', 'closing', 'nieznany'])
  })

  it('zwraca komplet utworów — kolejność jest permutacją składu setu', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'a', bpm: 130 }, 'PEAK'),
      aPlaylistTrack({ spotifyId: 'b', bpm: null }, null),
      aPlaylistTrack({ spotifyId: 'c', bpm: 90 }, 'WARMUP'),
    ]

    expect(arrangeBySlot(tracks).sort()).toEqual(['a', 'b', 'c'])
  })
})
