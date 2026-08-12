import { describe, expect, it } from 'vitest'
import {
  bpmFacts,
  compatibleCamelot,
  coverageParts,
  cumulativeGrowth,
  insights,
  largestBucket,
  matrixGrid,
  percentLabel,
  readinessScore,
} from './overviewInsights'
import { overviewFixture } from './test/fixtures'

describe('cumulativeGrowth', () => {

  it('kotwiczy krzywą dzisiejszym stanem biblioteki, nie sumą 12 miesięcy', () => {

    const monthly = [
      { label: '2026-05', count: 3 },
      { label: '2026-06', count: 5 },
    ]

    const cumulative = cumulativeGrowth(monthly, 20)

    // 20 dzisiaj, więc przed czerwcem było 15, a przed majem 12
    expect(cumulative).toEqual([
      { label: '2026-05', count: 15 },
      { label: '2026-06', count: 20 },
    ])
  })

  it('pusta historia nie wywraca kotwiczenia', () => {
    expect(cumulativeGrowth([], 7)).toEqual([])
  })
})

describe('bpmFacts', () => {

  it('liczy pomiar i estymatę osobno, pomijając utwory bez tempa (D19)', () => {

    const facts = bpmFacts([
      { label: 'MANUAL', count: 120 },
      { label: 'DEEZER', count: 80 },
      { label: 'LLM', count: 300 },
      { label: 'BRAK BPM', count: 500 },
    ])

    expect(facts.measured).toBe(200)
    expect(facts.estimated).toBe(300)
    expect(facts.known).toBe(500)
    expect(facts.ratio).toBeCloseTo(0.4)
  })

  it('brak jakiegokolwiek tempa daje zero, a nie dzielenie przez zero', () => {

    const facts = bpmFacts([{ label: 'BRAK BPM', count: 10 }])

    expect(facts.known).toBe(0)
    expect(facts.ratio).toBe(0)
  })
})

describe('compatibleCamelot', () => {

  it('podaje sąsiadów koła i tonację równoległą (D25)', () => {
    expect(compatibleCamelot('8A')).toEqual(['9A', '7A', '8B'])
  })

  it('zawija koło na krańcach — po 12 idzie 1', () => {
    expect(compatibleCamelot('12B')).toEqual(['1B', '11B', '12A'])
    expect(compatibleCamelot('1A')).toEqual(['2A', '12A', '1B'])
  })

  it('etykieta spoza koła nie udaje tonacji', () => {
    expect(compatibleCamelot('BEZ TONACJI')).toEqual([])
    expect(compatibleCamelot('13A')).toEqual([])
  })
})

describe('matrixGrid', () => {

  it('uzupełnia brakujące pary zerami, żeby siatka nie miała dziur', () => {

    const grid = matrixGrid(
      [{ tempoClass: 'FAST', energy: 'HIGH', count: 4 }],
      ['SLOW', 'FAST'],
      ['LOW', 'HIGH'],
    )

    expect(grid).toEqual([
      [0, 0],
      [0, 4],
    ])
  })
})

describe('largestBucket', () => {

  it('pomija koszyki wykluczone z rankingu', () => {

    const buckets = [
      { label: 'BEZ TONACJI', count: 900 },
      { label: '8A', count: 40 },
    ]

    expect(largestBucket(buckets, ['BEZ TONACJI'])?.label).toBe('8A')
  })

  it('pusty rozkład nie ma zwycięzcy', () => {
    expect(largestBucket([])).toBeNull()
  })
})

describe('coverageParts i readinessScore', () => {

  it('liczą pokrycie jako dopełnienie braków z grup pól (D11)', () => {

    const parts = coverageParts(overviewFixture)

    expect(parts.map((part) => part.label)).toEqual([
      'metadane', 'cechy audio', 'analiza AI', 'metryki z pliku',
    ])
    // katalog 2500, braków metadanych 4
    expect(parts[0].covered).toBe(2496)
    expect(parts[0].ratio).toBeCloseTo(0.9984)
  })

  it('gotowość to średnia z czterech pokryć', () => {

    const parts = coverageParts(overviewFixture)
    const average = parts.reduce((sum, part) => sum + part.ratio, 0) / parts.length

    expect(readinessScore(overviewFixture)).toBeCloseTo(average)
  })
})

describe('percentLabel', () => {

  it('zero jako całość daje 0%, a nie NaN', () => {
    expect(percentLabel(0, 0)).toBe('0%')
  })
})

describe('insights', () => {

  it('nazywa najgęstsze tempo, dominującą tonację i udział pomiaru', () => {

    const found = insights(overviewFixture)
    const byId = new Map(found.map((insight) => [insight.id, insight]))

    expect(byId.get('bpm')?.value).toBe('100–109 BPM')
    expect(byId.get('key')?.value).toBe('8A')
    // pomiar 120 + 900 z 2120 znanych temp
    expect(byId.get('facts')?.value).toBe('48%')
    expect(byId.get('artist')?.value).toBe('Marc Anthony')
  })

  it('opisuje sam zbiór utworów — rocznik i długość, bez playlist i setów', () => {

    const found = insights(overviewFixture)
    const byId = new Map(found.map((insight) => [insight.id, insight]))

    expect(byId.get('decade')?.value).toBe('2000s')
    // 231 000 ms to 3:51
    expect(byId.get('length')?.value).toBe('3:51')
    expect(found.map((insight) => insight.id)).not.toContain('unused')
  })

  it('podpowiada tonacje wchodzące zgodnie z dominującą (D25)', () => {

    const key = insights(overviewFixture).find((insight) => insight.id === 'key')

    expect(key?.hint).toContain('9A, 7A, 8B')
  })

  it('pusta biblioteka nie produkuje wniosków o niczym', () => {

    const empty = {
      ...overviewFixture,
      scale: { ...overviewFixture.scale, libraryTracks: 0, averageDurationMs: null },
      sound: { ...overviewFixture.sound, bpmHistogram: [], camelotKeys: [] },
      quality: { ...overviewFixture.quality, bpmSources: [] },
      timeline: { ...overviewFixture.timeline, monthlyGrowth: [], decades: [] },
      taste: { ...overviewFixture.taste, topArtists: [] },
    }

    expect(insights(empty)).toEqual([])
  })
})
