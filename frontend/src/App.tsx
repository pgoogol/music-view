import { useCallback, useState } from 'react'
import ImportPanel from './components/ImportPanel'
import EnrichPanel from './components/EnrichPanel'
import SpotifyPanel from './components/SpotifyPanel'
import PlaylistPanel from './components/PlaylistPanel'
import LibraryTable from './components/LibraryTable'
import TrackDetails from './components/TrackDetails'

export default function App() {
  // licznik odświeżeń: import CSV / zakończony job / edycja wymuszają refetch tabeli
  const [refreshKey, setRefreshKey] = useState(0)
  const [selectedIds, setSelectedIds] = useState<ReadonlySet<string>>(new Set())
  const [detailsId, setDetailsId] = useState<string | null>(null)

  const refresh = useCallback(() => setRefreshKey((key) => key + 1), [])

  return (
    <div className="app">
      <header className="app-header">
        <h1>music-view</h1>
        <span className="subtitle">biblioteka DJ-a — Sabor Latino</span>
      </header>

      <div className="panels">
        <ImportPanel onImported={refresh} />
        <EnrichPanel selectedIds={selectedIds} onJobFinished={refresh} />
        <SpotifyPanel onImported={refresh} />
      </div>

      <LibraryTable
        refreshKey={refreshKey}
        selectedIds={selectedIds}
        onSelectionChange={setSelectedIds}
        onOpenDetails={setDetailsId}
      />

      <PlaylistPanel
        selectedIds={selectedIds}
        onSelectionUsed={() => setSelectedIds(new Set())}
      />

      {detailsId && (
        <TrackDetails
          spotifyId={detailsId}
          onClose={() => setDetailsId(null)}
          onChanged={refresh}
        />
      )}
    </div>
  )
}
