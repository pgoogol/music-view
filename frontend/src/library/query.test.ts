import { describe, expect, it } from 'vitest'
import {
  DEFAULT_PAGE_SIZE,
  FILTER_KEYS,
  activeFilters,
  advancedFiltersActive,
  clearFiltersPatch,
  searchRequest,
} from './query'

/** Adres z kompletem filtrów — jeden fixture dla testów opisu i czyszczenia. */
const EVERY_FILTER =
  'q=salsa&lib=yes&genre=LATIN&rating=4&tag=wesele' +
  '&yearMin=1990&yearMax=1999&durMin=120&durMax=270&popMin=60&explicit=0' +
  '&bpmMin=100&bpmMax=130&tempo=FAST&energy=high&key=8A&keyExact=1' +
  '&valMin=0.3&valMax=0.8&instr=0.5&live=0.4&bpmSrc=DEEZER&missing=ANY'

describe('searchRequest', () => {

  it('bez parametrów pyta o pierwszą stronę w porządku trafności', () => {

    const request = searchRequest(new URLSearchParams())

    expect(request.sort).toBe('RELEVANCE')
    expect(request.direction).toBe('ASC')
    expect(request.page).toBe(0)
    expect(request.size).toBe(DEFAULT_PAGE_SIZE)
    expect(request.search).toBeUndefined()
  })

  it('przekłada parametry adresu na nazwy z API', () => {

    const request = searchRequest(new URLSearchParams(EVERY_FILTER))

    expect(request.genreFamily).toBe('LATIN')
    expect(request.yearMin).toBe(1990)
    expect(request.durationMaxSec).toBe(270)
    expect(request.popularityMin).toBe(60)
    expect(request.explicit).toBe(false)
    expect(request.inLibrary).toBe(true)
    expect(request.missing).toBe('ANY')
    expect(request.bpmSource).toBe('DEEZER')
  })

  it('tonacja domyślnie obejmuje zgodne, a „dokładna" zawęża ją do jednej pozycji', () => {

    expect(searchRequest(new URLSearchParams('key=8A')).camelotCompatible).toBe(true)
    expect(searchRequest(new URLSearchParams('key=8A&keyExact=1')).camelotCompatible).toBe(false)
    expect(searchRequest(new URLSearchParams()).camelotCompatible).toBeUndefined()
  })

  it('nieliczbowa wartość z ręcznie sklejonego adresu znaczy brak filtra, nie NaN', () => {

    const request = searchRequest(new URLSearchParams('bpmMin=abc&page=&size=xyz'))

    expect(request.bpmMin).toBeUndefined()
    expect(request.page).toBe(0)
    expect(request.size).toBe(DEFAULT_PAGE_SIZE)
  })
})

describe('activeFilters', () => {

  it('opisuje zakres jednym chipsem, a pojedynczą granicę słowem od/do', () => {

    const labels = (query: string) => activeFilters(new URLSearchParams(query)).map((f) => f.label)

    expect(labels('bpmMin=100&bpmMax=130')).toEqual(['BPM 100–130'])
    expect(labels('bpmMin=100')).toEqual(['BPM od 100'])
    expect(labels('bpmMax=130')).toEqual(['BPM do 130'])
  })

  it('tłumaczy wartości słownikowe na polskie etykiety ekranu', () => {

    const labels = activeFilters(new URLSearchParams('tempo=FAST&energy=high&missing=AI'))
      .map((filter) => filter.label)

    expect(labels).toContain('tempo: szybkie')
    expect(labels).toContain('energia: wysoka')
    expect(labels).toContain('braki: opis AI')
  })

  it('czas pokazuje w minutach i sekundach, bo tak się go czyta na secie', () => {

    const [filter] = activeFilters(new URLSearchParams('durMin=120&durMax=270'))

    expect(filter.label).toBe('czas 2:00–4:30')
  })

  it('zdejmuje tonację razem ze znacznikiem dokładności', () => {

    const [filter] = activeFilters(new URLSearchParams('key=8A&keyExact=1'))

    expect(filter.label).toBe('tonacja 8A')
    expect(filter.keys).toEqual(['key', 'keyExact'])
  })

  it('każdy filtr, który potrafi się pokazać, potrafi się też wyczyścić', () => {

    const reported = activeFilters(new URLSearchParams(EVERY_FILTER)).flatMap((f) => f.keys)
    const cleared = clearFiltersPatch()

    expect(reported.length).toBeGreaterThan(0)
    reported.forEach((key) => {
      expect(FILTER_KEYS).toContain(key)
      expect(cleared).toHaveProperty(key, undefined)
    })
    expect(cleared).toHaveProperty('page', undefined)
  })
})

describe('advancedFiltersActive', () => {

  it('filtry pierwszego rzutu nie otwierają panelu', () => {
    expect(advancedFiltersActive(new URLSearchParams('q=salsa&genre=LATIN&rating=4'))).toBe(false)
  })

  it('filtr spoza pierwszego rzutu otwiera panel, żeby nie działał w ukryciu', () => {
    expect(advancedFiltersActive(new URLSearchParams('instr=0.5'))).toBe(true)
  })
})
