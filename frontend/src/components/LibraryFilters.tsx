// Panel filtrów biblioteki (M3.1–M4.1, uporządkowany w M5.6, przycięty w M5.7).
//
// Trzy piętra zamiast jednego rzędu kilkunastu kontrolek: na wierzchu trzy
// filtry, których DJ używa za każdym razem (szukaj, gatunek, ocena); pod nimi
// chipsy z tym, co realnie jest włączone — bo filtr schowany w zwiniętym panelu
// wciąż odsiewa wyniki i musi być widać, że to on; a pod spodem panel z resztą,
// podzielony na grupy odpowiadające temu, o co pytają: utwór i brzmienie.
//
// Grupy „biblioteka DJ-a", „metryki z pliku" i „kompletność danych" znikają
// razem z filtrami, które je wypełniały (D39) — braki danych zbiera zakładka
// Wzbogacanie, a metryki z pliku widać w szufladzie utworu.

import { useCallback, useState, type ReactNode } from 'react'
import { ENERGY_LABELS, TEMPO_LABELS } from '../format'
import { useDebouncedParam } from '../hooks/useDebouncedParam'
import { activeFilters, advancedFiltersActive } from '../library/query'

const GENRES = ['LATIN', 'ROCK', 'POP', 'DISCO', 'DISCO_POLO', 'ELECTRONIC', 'HIP_HOP', 'OTHER']
const TEMPO_CLASSES = Object.keys(TEMPO_LABELS)
const ENERGIES = Object.keys(ENERGY_LABELS)
const RATINGS = [1, 2, 3, 4, 5]
/** Wszystkie 24 pozycje koła Camelot (D25) — 1A–12A moll, 1B–12B dur. */
const CAMELOT_KEYS = Array.from({ length: 12 }, (_, index) => index + 1).flatMap((number) => [
  `${number}A`,
  `${number}B`,
])

type Patch = Record<string, string | number | undefined | null>

interface Props {
  params: URLSearchParams
  setParams: (patch: Patch) => void
  onClear: () => void
}

export default function LibraryFilters({ params, setParams, onClear }: Props) {

  const [open, setOpen] = useState(() => advancedFiltersActive(params))

  const search = params.get('q') ?? ''
  const camelot = params.get('key') ?? ''

  // każda zmiana filtra wraca na pierwszą stronę — inaczej węższy wynik potrafi
  // wylądować „na stronie 7 z 3", czyli w pustce
  const set = useCallback(
    (patch: Patch) => setParams({ ...patch, page: undefined }),
    [setParams],
  )
  const pushSearch = useCallback((next: string) => set({ q: next }), [set])
  const [searchDraft, setSearchDraft] = useDebouncedParam(search, pushSearch)

  const active = activeFilters(params)

  const numberField = (
    key: string,
    label: string,
    input: { min?: string; max?: string; step?: string; placeholder?: string } = {},
  ) => (
    <label className="filter-group">
      <span>{label}</span>
      <input
        type="number"
        value={params.get(key) ?? ''}
        onChange={(event) => set({ [key]: event.target.value })}
        aria-label={label}
        {...input}
      />
    </label>
  )

  const selectField = (
    key: string,
    label: string,
    empty: string,
    options: readonly (readonly [string, string])[],
  ) => (
    <label className="filter-group">
      <span>{label}</span>
      <select
        value={params.get(key) ?? ''}
        onChange={(event) => set({ [key]: event.target.value })}
        aria-label={label}
      >
        <option value="">{empty}</option>
        {options.map(([value, optionLabel]) => (
          <option key={value} value={value}>
            {optionLabel}
          </option>
        ))}
      </select>
    </label>
  )

  const group = (testId: string, title: string, children: ReactNode) => (
    <fieldset className="filter-box" data-testid={testId}>
      <legend>{title}</legend>
      {children}
    </fieldset>
  )

  return (
    <div className="filters">
      <div className="filters-basic">
        <input
          type="search"
          placeholder="Szukaj: tytuł / wykonawca…"
          value={searchDraft}
          onChange={(event) => setSearchDraft(event.target.value)}
          data-testid="search-input"
        />
        {selectField(
          'genre',
          'gatunek',
          'wszystkie',
          GENRES.map((genre) => [genre, genre] as const),
        )}
        {selectField(
          'rating',
          'ocena co najmniej',
          'dowolna',
          RATINGS.map((value) => [String(value), `${'★'.repeat(value)} i wyżej`] as const),
        )}
        <span className="spacer" />
        <button
          type="button"
          className="link"
          aria-expanded={open}
          onClick={() => setOpen((current) => !current)}
          data-testid="toggle-advanced-filters"
        >
          {open ? 'mniej filtrów ▴' : 'więcej filtrów ▾'}
        </button>
      </div>

      {active.length > 0 && (
        <div className="filter-chips" data-testid="active-filters">
          {active.map((filter) => (
            <span key={filter.keys.join('-')} className="chip">
              {filter.label}
              <button
                type="button"
                className="link chip-remove"
                aria-label={`usuń filtr ${filter.label}`}
                onClick={() =>
                  set(Object.fromEntries(filter.keys.map((key) => [key, undefined])))
                }
              >
                ×
              </button>
            </span>
          ))}
          <button type="button" className="link" onClick={onClear} data-testid="clear-filters">
            wyczyść filtry
          </button>
        </div>
      )}

      {open && (
        <div className="filters-advanced" data-testid="advanced-filters">
          {group(
            'track-filters',
            'utwór',
            <>
              {numberField('durMin', 'czas od (sek)', { min: '0', step: '15' })}
              {numberField('durMax', 'czas do (sek)', { min: '0', step: '15' })}
            </>,
          )}

          {/* brzmienie: tempo, energia i harmonia (D25) — koło Camelot liczy backend
              z tonacji, więc filtr obejmuje też utwory z dumpa AB */}
          {group(
            'harmonic-filters',
            'brzmienie',
            <>
              {numberField('bpmMin', 'BPM od')}
              {numberField('bpmMax', 'BPM do')}
              {selectField(
                'tempo',
                'tempo',
                'wszystkie',
                TEMPO_CLASSES.map((value) => [value, TEMPO_LABELS[value]] as const),
              )}
              {selectField(
                'energy',
                'energia',
                'wszystkie',
                ENERGIES.map((value) => [value, ENERGY_LABELS[value]] as const),
              )}
              {selectField(
                'key',
                'tonacja (Camelot)',
                'dowolna',
                CAMELOT_KEYS.map((value) => [value, value] as const),
              )}
              {camelot !== '' && (
                <label className="filter-check">
                  <input
                    type="checkbox"
                    checked={params.get('keyExact') === '1'}
                    onChange={(event) =>
                      set({ keyExact: event.target.checked ? '1' : undefined })
                    }
                  />
                  <span>tylko dokładna tonacja</span>
                </label>
              )}
            </>,
          )}
        </div>
      )}
    </div>
  )
}
