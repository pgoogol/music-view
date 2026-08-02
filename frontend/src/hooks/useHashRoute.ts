// Routing na hashu (M3.1, piąty widok w M3.2) — widoki plus stan filtrów
// w adresie, bez dodatkowej biblioteki routera: aplikacja jest serwowana
// statycznie, a odświeżenie strony ma wracać do tego samego widoku i filtrów.

import { useCallback, useMemo, useSyncExternalStore } from 'react'

export const ROUTES = ['overview', 'library', 'playlists', 'sets', 'import', 'enrich'] as const

export type RouteName = (typeof ROUTES)[number]

export const DEFAULT_ROUTE: RouteName = 'library'

export const ROUTE_LABELS: Record<RouteName, string> = {
  overview: 'Przegląd',
  library: 'Biblioteka',
  playlists: 'Playlisty',
  sets: 'Sety',
  import: 'Import',
  enrich: 'Wzbogacanie',
}

export interface ParsedRoute {
  name: RouteName
  params: URLSearchParams
}

/** Nieznana nazwa widoku (stary link, literówka) cofa się do biblioteki. */
export function parseHash(hash: string): ParsedRoute {

  const withoutPrefix = hash.replace(/^#\/?/, '')
  const [path, query = ''] = withoutPrefix.split('?')
  const name = ROUTES.find((route) => route === path) ?? DEFAULT_ROUTE
  return { name, params: new URLSearchParams(query) }
}

export function buildHash(name: RouteName, params: URLSearchParams): string {
  const query = params.toString()
  return query ? `#/${name}?${query}` : `#/${name}`
}

/** Puste wartości usuwają parametr, żeby adres nie puchł od domyślnych filtrów. */
export function applyParams(
  params: URLSearchParams,
  patch: Record<string, string | number | undefined | null>,
): URLSearchParams {

  const next = new URLSearchParams(params)
  Object.entries(patch).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') next.delete(key)
    else next.set(key, String(value))
  })
  return next
}

const subscribe = (onChange: () => void) => {
  window.addEventListener('hashchange', onChange)
  return () => window.removeEventListener('hashchange', onChange)
}

export function useHashRoute() {

  const hash = useSyncExternalStore(
    subscribe,
    () => window.location.hash,
    () => '',
  )
  const route = useMemo(() => parseHash(hash), [hash])

  const navigate = useCallback((name: RouteName, params?: URLSearchParams) => {
    window.location.hash = buildHash(name, params ?? new URLSearchParams())
  }, [])

  const setParams = useCallback(
    (patch: Record<string, string | number | undefined | null>) => {
      const { name, params } = parseHash(window.location.hash)
      window.location.hash = buildHash(name, applyParams(params, patch))
    },
    [],
  )

  return { route: route.name, params: route.params, navigate, setParams }
}
