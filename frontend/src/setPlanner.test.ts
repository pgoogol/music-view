import { describe, expect, it } from 'vitest'
import { aMetrics, aPlaylistTrack } from './test/fixtures'
import {
  areKeysCompatible,
  arrangeBy,
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
      aPlaylistTrack({ spotifyId: 'a', bpm: 120 }, 'MIDDLE', 1, { metrics: aMetrics({ loudnessDb: -12 }) }),
      aPlaylistTrack({ spotifyId: 'b', bpm: 122 }, 'MIDDLE', 2, { metrics: aMetrics({ loudnessDb: -5 }) }),
    ]

    const warnings = findSetWarnings(tracks).filter((warning) => warning.kind === 'LOUDNESS_JUMP')

    expect(warnings).toHaveLength(1)
    expect(warnings[0].position).toBe(2)
  })

  it('nie liczy skoku głośności, gdy brakuje pomiaru po którejś stronie', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'a', bpm: 120 }, 'MIDDLE', 1, { metrics: aMetrics({ loudnessDb: -12 }) }),
      aPlaylistTrack({ spotifyId: 'b', bpm: 122 }, 'MIDDLE', 2, { metrics: null }),
    ]

    expect(findSetWarnings(tracks).map((warning) => warning.kind)).not.toContain('LOUDNESS_JUMP')
  })

  it('zgłasza metrum inne niż 4/4 i milczy przy 4/4', () => {

    const odd = [aPlaylistTrack({ spotifyId: 'a', bpm: 120 }, 'MIDDLE', 1, { metrics: aMetrics({ timeSignature: 3 }) })]
    const even = [aPlaylistTrack({ spotifyId: 'b', bpm: 120 }, 'MIDDLE', 1, { metrics: aMetrics({ timeSignature: 4 }) })]

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

describe('arrangeBy', () => {

  it('tryb tempa ustawia utwory rosnąco po BPM, a te bez BPM na końcu', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'szybki', bpm: 140 }, 'PEAK', 0),
      aPlaylistTrack({ spotifyId: 'brak', bpm: null }, null, 1),
      aPlaylistTrack({ spotifyId: 'wolny', bpm: 90 }, 'WARMUP', 2),
    ]

    expect(arrangeBy(tracks, 'TEMPO')).toEqual(['wolny', 'szybki', 'brak'])
  })

  it('tryb energii prowadzi od niskiej do wysokiej, a w grupie po tempie', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'wysoka', bpm: 130, energy: 'high' }, 'PEAK', 0),
      aPlaylistTrack({ spotifyId: 'niska', bpm: 95, energy: 'low' }, 'WARMUP', 1),
      aPlaylistTrack({ spotifyId: 'srednia-szybsza', bpm: 120, energy: 'medium' }, 'MIDDLE', 2),
      aPlaylistTrack({ spotifyId: 'srednia-wolniejsza', bpm: 110, energy: 'medium' }, 'MIDDLE', 3),
    ]

    expect(arrangeBy(tracks, 'ENERGY')).toEqual([
      'niska',
      'srednia-wolniejsza',
      'srednia-szybsza',
      'wysoka',
    ])
  })

  it('tryb harmoniczny zostawia otwarcie DJ-a i unika zderzeń tonacji', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'start', bpm: 120, camelot: '8A' }, 'MIDDLE', 0),
      aPlaylistTrack({ spotifyId: 'zderzenie', bpm: 121, camelot: '2A' }, 'MIDDLE', 1),
      aPlaylistTrack({ spotifyId: 'zgodny', bpm: 124, camelot: '9A' }, 'MIDDLE', 2),
    ]

    // 9A jest zgodny z 8A, więc wchodzi przed 2A mimo większego skoku tempa
    expect(arrangeBy(tracks, 'HARMONY')).toEqual(['start', 'zgodny', 'zderzenie'])
  })

  it('tryb faz robi to samo co układanie wg slotów D9 z M3.1', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'szczyt', bpm: 130 }, 'PEAK', 0),
      aPlaylistTrack({ spotifyId: 'rozgrzewka', bpm: 95 }, 'WARMUP', 1),
    ]

    expect(arrangeBy(tracks, 'PHASES')).toEqual(arrangeBySlot(tracks))
  })

  it('każdy tryb zwraca permutację składu — kontrakt PUT /tracks (D21)', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'a', bpm: 120, camelot: '8A', energy: 'high' }, 'PEAK', 0),
      aPlaylistTrack({ spotifyId: 'b', bpm: null, camelot: null, energy: null }, null, 1),
      aPlaylistTrack({ spotifyId: 'c', bpm: 100, camelot: '2A', energy: 'low' }, 'WARMUP', 2),
    ]

    ;(['PHASES', 'TEMPO', 'HARMONY', 'ENERGY'] as const).forEach((mode) => {
      expect([...arrangeBy(tracks, mode)].sort()).toEqual(['a', 'b', 'c'])
    })
  })

  it('set krótszy niż dwa utwory zostaje bez zmian', () => {

    const single = [aPlaylistTrack({ spotifyId: 'jedyny' }, 'PEAK', 0)]

    expect(arrangeBy(single, 'HARMONY')).toEqual(['jedyny'])
    expect(arrangeBy([], 'TEMPO')).toEqual([])
  })
})

describe('arrangeBy — tryby falowe (M4.6)', () => {

  /** Utwór z intensywnością podaną zmierzoną energią z pliku (D24). */
  function measured(spotifyId: string, energy: number) {
    return aPlaylistTrack({ spotifyId, bpm: 120 }, 'MIDDLE', 0, {
      metrics: aMetrics({ energy }),
    })
  }

  function energiesOf(order: string[], tracks: ReturnType<typeof measured>[]): number[] {
    const byId = new Map(tracks.map((entry) => [entry.track.spotifyId, entry]))
    return order.map((id) => byId.get(id)!.metrics!.energy!)
  }

  const eight = [0.1, 0.2, 0.3, 0.4, 0.6, 0.7, 0.8, 0.9].map((energy, index) =>
    measured(`t${index}`, energy),
  )

  it('tryb falowy rozbija set na kilka narastań zamiast jednego', () => {

    const energies = energiesOf(arrangeBy(eight, 'WAVE'), eight)

    // co najmniej jedno zejście w środku — tego właśnie nie daje tryb tempa
    const drops = energies.filter((value, index) => index > 0 && value < energies[index - 1])
    expect(drops.length).toBeGreaterThan(0)
  })

  it('każda kolejna fala sięga wyżej niż poprzednia', () => {

    // 8 utworów → 2 fale po 4; druga zaczyna się i kończy wyżej niż pierwsza
    const energies = energiesOf(arrangeBy(eight, 'WAVE'), eight)
    const first = energies.slice(0, 4)
    const second = energies.slice(4)

    expect(second[0]).toBeGreaterThan(first[0])
    expect(Math.max(...second)).toBeGreaterThan(Math.max(...first))
    expect(first).toEqual([...first].sort((left, right) => left - right))
    expect(second).toEqual([...second].sort((left, right) => left - right))
  })

  it('tryb łukowy stawia najmocniejszy utwór w środku, nie na końcu', () => {

    const energies = energiesOf(arrangeBy(eight, 'ARC'), eight)
    const peak = energies.indexOf(Math.max(...energies))

    expect(peak).toBeGreaterThan(0)
    expect(peak).toBeLessThan(energies.length - 1)
    expect(energies.at(-1)).toBeLessThan(Math.max(...energies))
  })

  it('zmierzona energia z pliku bije BPM przy liczeniu intensywności (D34)', () => {

    // BPM sugeruje odwrotną kolejność niż zmierzona energia — wygrywa plik
    const tracks = [
      aPlaylistTrack({ spotifyId: 'wolny-ale-mocny', bpm: 90 }, 'PEAK', 0, {
        metrics: aMetrics({ energy: 0.9 }),
      }),
      aPlaylistTrack({ spotifyId: 'szybki-ale-slaby', bpm: 175 }, 'WARMUP', 1, {
        metrics: aMetrics({ energy: 0.1 }),
      }),
    ]

    expect(arrangeBy(tracks, 'ARC')).toEqual(['szybki-ale-slaby', 'wolny-ale-mocny'])
  })

  it('bez metryk intensywność bierze się z tempa, a potem ze zgrubnej energii', () => {

    const tracks = [
      aPlaylistTrack({ spotifyId: 'szybki', bpm: 160, energy: null }, 'PEAK', 0),
      aPlaylistTrack({ spotifyId: 'wolny', bpm: 80, energy: null }, 'WARMUP', 1),
      aPlaylistTrack({ spotifyId: 'bez-bpm-mocny', bpm: null, energy: 'high' }, null, 2),
    ]

    // 80 BPM → 0.14, 'high' → 0.8, 160 BPM → 0.71
    expect(arrangeBy(tracks, 'TEMPO').at(0)).toBe('wolny')
    expect(arrangeBy(tracks, 'ARC').at(0)).toBe('wolny')
  })

  it('tryby falowe też zwracają permutację składu', () => {

    ;(['WAVE', 'ARC'] as const).forEach((mode) => {
      expect([...arrangeBy(eight, mode)].sort()).toEqual(
        eight.map((entry) => entry.track.spotifyId).sort(),
      )
    })
  })
})
