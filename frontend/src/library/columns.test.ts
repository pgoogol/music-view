import { describe, expect, it } from 'vitest'
import {
  DEFAULT_COLUMN_KEYS,
  LIBRARY_COLUMNS,
  parseColumns,
  serializeColumns,
  visibleColumns,
} from './columns'
import { aRow } from '../test/fixtures'

describe('kolumny biblioteki', () => {

  it('brak parametru znaczy zestaw domyślny', () => {
    expect(parseColumns(null)).toEqual(DEFAULT_COLUMN_KEYS)
    expect(parseColumns('')).toEqual(DEFAULT_COLUMN_KEYS)
  })

  it('nieznane klucze ze starego linku odpadają, a kolumny stałe wracają zawsze', () => {

    const keys = parseColumns('popularity,nieistnieje')

    expect(keys).toContain('popularity')
    expect(keys).toContain('title')
    expect(keys).toContain('artist')
    expect(keys).not.toContain('nieistnieje')
  })

  it('kolumny zdjęte z tabeli odpadają jak każdy nieznany klucz (D39)', () => {
    expect(parseColumns('rating,tags,added,explicit,bpmSource')).toEqual(DEFAULT_COLUMN_KEYS)
  })

  it('kolejność kolumn jest kanoniczna, nie taka jak w adresie', () => {
    expect(parseColumns('year,album')).toEqual(['title', 'artist', 'album', 'year'])
  })

  it('zestaw domyślny nie zaśmieca adresu', () => {
    expect(serializeColumns(DEFAULT_COLUMN_KEYS)).toBeUndefined()
    expect(serializeColumns(['title', 'artist'])).toBe('title,artist')
  })

  it('każda kolumna umie narysować komórkę utworu spoza biblioteki i bez danych', () => {

    const emptyRow = aRow(
      {
        title: null,
        artist: null,
        album: null,
        year: null,
        durationMs: null,
        popularity: null,
        explicit: null,
        genreFamily: null,
        style: null,
        bpm: null,
        bpmSource: null,
        danceability: null,
        musicalKey: null,
        camelot: null,
        tempoClass: null,
        energy: null,
      },
      null,
    )

    LIBRARY_COLUMNS.forEach((column) => {
      expect(() => column.cell(emptyRow, { onOpenDetails: () => {} })).not.toThrow()
    })
  })

  it('widoczne kolumny to przekrój definicji przez wybór', () => {

    const labels = visibleColumns(['title', 'bpm']).map((column) => column.label)

    expect(labels).toEqual(['Tytuł', 'BPM'])
  })
})
