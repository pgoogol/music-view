// Pasek zaznaczenia (M3.1) — zaznaczenie z tabeli przeżywa zmianę zakładki,
// więc akcje na nim muszą być dostępne z każdego widoku, nie tylko z biblioteki.

import { useEffect, useState } from 'react'
import { api, type PlaylistSummaryResponse } from '../api'
import { useToast } from './Toasts'
import { useHashRoute } from '../hooks/useHashRoute'

interface Props {
  selectedIds: ReadonlySet<string>
  onClear: () => void
  onChanged: () => void
}

export default function SelectionBar({ selectedIds, onClear, onChanged }: Props) {

  const { notify, reportError } = useToast()
  const { navigate } = useHashRoute()
  const [playlists, setPlaylists] = useState<PlaylistSummaryResponse[]>([])
  const [targetId, setTargetId] = useState('')
  const [busy, setBusy] = useState(false)

  const visible = selectedIds.size > 0

  useEffect(() => {
    if (!visible) return
    api.listPlaylists().then(setPlaylists).catch(() => setPlaylists([]))
  }, [visible])

  if (!visible) return null

  const addToSet = async () => {
    if (!targetId) return
    setBusy(true)
    const playlistId = Number(targetId)
    let added = 0
    try {
      const playlist = await api.getPlaylist(playlistId)
      const onSet = new Set(playlist.tracks.map((entry) => entry.track.spotifyId))
      for (const spotifyId of selectedIds) {
        if (onSet.has(spotifyId)) continue
        await api.addPlaylistTrack(playlistId, spotifyId)
        added += 1
      }
      notify(added > 0 ? `Dodano ${added} utworów do setu` : 'Wszystkie zaznaczone są już w secie')
      onClear()
      onChanged()
    } catch (error) {
      reportError(error, 'Nie udało się dodać do setu')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="selection-bar" data-testid="selection-bar">
      <strong>zaznaczonych: {selectedIds.size}</strong>
      <select
        value={targetId}
        onChange={(event) => setTargetId(event.target.value)}
        aria-label="set docelowy"
      >
        <option value="">wybierz set…</option>
        {playlists.map((playlist) => (
          <option key={playlist.id} value={playlist.id}>
            {playlist.name}
          </option>
        ))}
      </select>
      <button onClick={addToSet} disabled={busy || targetId === ''} data-testid="selection-add">
        Dodaj do setu
      </button>
      <button onClick={() => navigate('enrich')} data-testid="selection-enrich">
        Wzbogać zaznaczone
      </button>
      <button className="link" onClick={onClear}>
        wyczyść zaznaczenie
      </button>
    </div>
  )
}
