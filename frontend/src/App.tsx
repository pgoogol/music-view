// Powłoka aplikacji (M3.1, motyw „konsola" M3.2, przegląd M4.3): sześć zakładek zamiast jednej
// długiej strony, wspólne zaznaczenie utworów przechodzące między widokami
// i jeden host toastów.

import { useCallback, useState } from 'react'
import SelectionBar from './components/SelectionBar'
import { ToastProvider } from './components/Toasts'
import { ROUTES, ROUTE_LABELS, useHashRoute } from './hooks/useHashRoute'
import EnrichView from './views/EnrichView'
import ImportView from './views/ImportView'
import LibraryView from './views/LibraryView'
import OverviewView from './views/OverviewView'
import PlaylistsView from './views/PlaylistsView'
import SetsView from './views/SetsView'

export default function App() {

  return (
    <ToastProvider>
      <AppShell />
    </ToastProvider>
  )
}

/**
 * Poświata kineskopu pod krzywą tempa — rozmyta kopia kreski udaje jarzenie
 * luminoforu. Filtr musi żyć w dokumencie raz, dlatego siedzi w powłoce,
 * nie w wykresie.
 */
function CrtDefs() {

  return (
    <svg className="crt-defs" aria-hidden="true" focusable="false">
      <filter id="crt-glow" x="-20%" y="-40%" width="140%" height="180%">
        <feGaussianBlur stdDeviation="4" />
      </filter>
    </svg>
  )
}

function AppShell() {

  const { route, params, navigate } = useHashRoute()
  // licznik odświeżeń: import, zakończony job i edycja wymuszają refetch widoków
  const [refreshKey, setRefreshKey] = useState(0)
  const [selectedIds, setSelectedIds] = useState<ReadonlySet<string>>(new Set())

  const refresh = useCallback(() => setRefreshKey((key) => key + 1), [])
  const clearSelection = useCallback(() => setSelectedIds(new Set()), [])

  return (
    <div className="app">
      <CrtDefs />

      <header className="app-header">
        <div className="brand">
          <h1>music-view</h1>
          <span className="subtitle">konsola DJ-a — Sabor Latino</span>
        </div>
        <nav className="tabs" aria-label="widoki">
          {ROUTES.map((name) => (
            <button
              key={name}
              className={name === route ? 'tab active' : 'tab'}
              aria-current={name === route ? 'page' : undefined}
              // biblioteka zachowuje filtry z adresu przy powrocie na tę samą zakładkę
              onClick={() => navigate(name, name === route ? params : undefined)}
              data-testid={`tab-${name}`}
            >
              {ROUTE_LABELS[name]}
            </button>
          ))}
        </nav>
      </header>

      <main>
        {route === 'overview' && <OverviewView refreshKey={refreshKey} />}
        {route === 'library' && (
          <LibraryView
            refreshKey={refreshKey}
            selectedIds={selectedIds}
            onSelectionChange={setSelectedIds}
            onChanged={refresh}
          />
        )}
        {route === 'playlists' && <PlaylistsView refreshKey={refreshKey} />}
        {route === 'sets' && (
          <SetsView selectedIds={selectedIds} onSelectionUsed={clearSelection} />
        )}
        {route === 'import' && <ImportView onImported={refresh} />}
        {route === 'enrich' && (
          <EnrichView selectedIds={selectedIds} onJobFinished={refresh} />
        )}
      </main>

      <SelectionBar selectedIds={selectedIds} onClear={clearSelection} onChanged={refresh} />
    </div>
  )
}
