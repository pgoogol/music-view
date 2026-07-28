import { describe, expect, it } from 'vitest'
import { applyParams, buildHash, parseHash } from './useHashRoute'

describe('parseHash', () => {

  it('czyta nazwę widoku i parametry z adresu', () => {

    const route = parseHash('#/library?q=salsa&sort=BPM')

    expect(route.name).toBe('library')
    expect(route.params.get('q')).toBe('salsa')
    expect(route.params.get('sort')).toBe('BPM')
  })

  it('pusty albo nieznany adres cofa do biblioteki', () => {

    expect(parseHash('').name).toBe('library')
    expect(parseHash('#/nieistniejacy').name).toBe('library')
  })

  it('rozpoznaje pozostałe widoki', () => {

    expect(parseHash('#/sets?set=7').name).toBe('sets')
    expect(parseHash('#/enrich').name).toBe('enrich')
    expect(parseHash('#/import').name).toBe('import')
  })
})

describe('buildHash', () => {

  it('pomija znak zapytania, gdy nie ma parametrów', () => {
    expect(buildHash('sets', new URLSearchParams())).toBe('#/sets')
  })

  it('dokleja parametry do nazwy widoku', () => {
    expect(buildHash('library', new URLSearchParams({ q: 'salsa' }))).toBe('#/library?q=salsa')
  })
})

describe('applyParams', () => {

  it('nadpisuje wskazane parametry, resztę zostawia', () => {

    const next = applyParams(new URLSearchParams({ q: 'salsa', page: '2' }), { page: 0 })

    expect(next.get('q')).toBe('salsa')
    expect(next.get('page')).toBe('0')
  })

  it('puste i niezdefiniowane wartości usuwa z adresu', () => {

    const next = applyParams(new URLSearchParams({ q: 'salsa', genre: 'LATIN' }), {
      q: '',
      genre: undefined,
    })

    expect(next.toString()).toBe('')
  })
})
