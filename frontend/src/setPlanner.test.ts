import { describe, expect, it } from 'vitest'
import { aPlaylistTrack } from './test/fixtures'
import {
  areKeysCompatible,
  arrangeBySlot,
  computeSetStats,
  findSetWarnings,
  insertLastAt,
} from './setPlanner'

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

describe('zgodność harmoniczna (D25)', () => {

  it('uznaje za zgodne tę samą tonację, sąsiadów na kole i tonację równoległą', () => {

    expect(areKeysCompatible('8A', '8A')).toBe(true)
    expect(areKeysCompatible('8A', '9A')).toBe(true)
    expect(areKeysCompatible('8A', '7A')).toBe(true)
    expect(areKeysCompatible('8A', '8B')).toBe(true)
  })

  it('zawija koło: 1A sąsiaduje z 12A', () => {

    expect(areKeysCompatible('1A', '12A')).toBe(true)
    expect(areKeysCompatible('12A', '1A')).toBe(true)
  })

  it('odrzuca odległe pozycje i skos przez środek koła', () => {

    expect(areKeysCompatible('8A', '10A')).toBe(false)
    expect(areKeysCompatible('8A', '2A')).toBe(false)
    expect(areKeysCompatible('8A', '9B')).toBe(false)
  })

  it('nie ostrzega, gdy któraś tonacja jest nieznana — to brak danych, nie zderzenie', () => {

    expect(areKeysCompatible(null, '8A')).toBe(true)
    expect(areKeysCompatible('8A', null)).toBe(true)
    expect(areKeysCompatible('nonsens', '8A')).toBe(true)
  })
})

describe('findSetWarnings — harmonia, głośność i metrum (M4.1)', () => {

  it('zgłasza zderzenie tonacji między sąsiadami', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'a', bpm: 120, camelot: '8A' }, 'MIDDLE'),
      aPlaylistTrack({ spotifyId: 'b', bpm: 122, camelot: '2A' }, 'MIDDLE', 2),
    ]

    const warnings = findSetWarnings(tracks)

    expect(warnings.map((warning) => warning.kind)).toContain('KEY_CLASH')
    expect(warnings.find((warning) => warning.kind === 'KEY_CLASH')?.position).toBe(2)
  })

  it('milczy o tonacji, gdy sąsiedzi są zgodni', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'a', bpm: 120, camelot: '8A' }, 'MIDDLE'),
      aPlaylistTrack({ spotifyId: 'b', bpm: 122, camelot: '9A' }, 'MIDDLE', 2),
    ]

    expect(findSetWarnings(tracks).map((warning) => warning.kind)).not.toContain('KEY_CLASH')
  })

  it('zgłasza skok głośności powyżej progu', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'a', bpm: 120 }, 'MIDDLE', 1, { loudnessDb: -12 }),
      aPlaylistTrack({ spotifyId: 'b', bpm: 122 }, 'MIDDLE', 2, { loudnessDb: -5 }),
    ]

    const warnings = findSetWarnings(tracks).filter((warning) => warning.kind === 'LOUDNESS_JUMP')

    expect(warnings).toHaveLength(1)
    expect(warnings[0].position).toBe(2)
  })

  it('nie liczy skoku głośności, gdy brakuje pomiaru po którejś stronie', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'a', bpm: 120 }, 'MIDDLE', 1, { loudnessDb: -12 }),
      aPlaylistTrack({ spotifyId: 'b', bpm: 122 }, 'MIDDLE', 2, { loudnessDb: null }),
    ]

    expect(findSetWarnings(tracks).map((warning) => warning.kind)).not.toContain('LOUDNESS_JUMP')
  })

  it('zgłasza metrum inne niż 4/4 i milczy przy 4/4', () => {

    const odd = [aPlaylistTrack({ spotifyId: 'a', bpm: 120 }, 'MIDDLE', 1, { timeSignature: 3 })]
    const even = [aPlaylistTrack({ spotifyId: 'b', bpm: 120 }, 'MIDDLE', 1, { timeSignature: 4 })]

    expect(findSetWarnings(odd).map((warning) => warning.kind)).toContain('ODD_METER')
    expect(findSetWarnings(even).map((warning) => warning.kind)).not.toContain('ODD_METER')
  })

  it('utwór bez BPM nadal potrafi zgłosić zderzenie tonacji', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'a', bpm: 120, camelot: '8A' }, 'MIDDLE'),
      aPlaylistTrack({ spotifyId: 'b', bpm: null, camelot: '2A' }, 'MIDDLE', 2),
    ]

    const kinds = findSetWarnings(tracks).map((warning) => warning.kind)

    expect(kinds).toContain('NO_BPM')
    expect(kinds).toContain('KEY_CLASH')
  })
})

describe('insertLastAt', () => {

  const tracks = ['a', 'b', 'c', 'nowy'].map((spotifyId, position) =>
    aPlaylistTrack({ spotifyId }, 'MIDDLE', position),
  )

  it('przesuwa dopisany utwór na wskazane miejsce', () => {

    expect(insertLastAt(tracks, 1)).toEqual(['a', 'nowy', 'b', 'c'])
  })

  it('miejsce zero wstawia na sam początek setu', () => {

    expect(insertLastAt(tracks, 0)).toEqual(['nowy', 'a', 'b', 'c'])
  })

  it('miejsce na końcu (albo poza setem) zostawia kolejność bez zmian', () => {

    expect(insertLastAt(tracks, 3)).toEqual(['a', 'b', 'c', 'nowy'])
    expect(insertLastAt(tracks, 9)).toEqual(['a', 'b', 'c', 'nowy'])
    expect(insertLastAt(tracks, -1)).toEqual(['a', 'b', 'c', 'nowy'])
  })
})
