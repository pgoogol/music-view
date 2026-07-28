// Powłoka aplikacji (M3.1, motyw „szkicownik" M3.2): pięć zakładek zamiast jednej
// długiej strony, wspólne zaznaczenie utworów przechodzące między widokami
// i jeden host toastów.

import { useCallback, useState } from 'react'
import SelectionBar from './components/SelectionBar'
import { ToastProvider } from './components/Toasts'
import { ROUTES, ROUTE_LABELS, useHashRoute } from './hooks/useHashRoute'
import EnrichView from './views/EnrichView'
import ImportView from './views/ImportView'
import LibraryView from './views/LibraryView'
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
 * Chropowatość kreski wykresów — feTurbulence rozjeżdża linię o ułamek piksela,
 * więc krzywa tempa wygląda jak narysowana ołówkiem, a nie wyliczona.
 * Filtr musi żyć w dokumencie raz, dlatego siedzi w powłoce, nie w wykresie.
 */
function SketchDefs() {

  return (
    <svg className="sketch-defs" aria-hidden="true" focusable="false">
      <filter id="sketch-rough">
        <feTurbulence type="fractalNoise" baseFrequency="0.04" numOctaves="2" seed="7" result="noise" />
        <feDisplacementMap in="SourceGraphic" in2="noise" scale="2.2" xChannelSelector="R" yChannelSelector="G" />
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
      <SketchDefs />

      <header className="app-header">
        <div className="brand">
          <h1>music-view</h1>
          <span className="subtitle">szkicownik DJ-a — Sabor Latino</span>
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
