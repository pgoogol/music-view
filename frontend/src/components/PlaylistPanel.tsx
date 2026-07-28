import { useCallback, useEffect, useState } from 'react'
import {
  api,
  ApiError,
  type PlaylistExportResponse,
  type PlaylistResponse,
  type PlaylistSummaryResponse,
} from '../api'

const SLOT_LABELS: Record<string, string> = {
  WARMUP: 'rozgrzewka',
  MIDDLE: 'środek',
  PEAK: 'szczyt',
  CLOSING: 'zamknięcie',
  BREAK: 'przerwa',
}

interface Props {
  selectedIds: ReadonlySet<string>
  onSelectionUsed: () => void
}

export default function PlaylistPanel({ selectedIds, onSelectionUsed }: Props) {
  const [playlists, setPlaylists] = useState<PlaylistSummaryResponse[]>([])
  const [openId, setOpenId] = useState<number | null>(null)
  const [playlist, setPlaylist] = useState<PlaylistResponse | null>(null)
  const [newName, setNewName] = useState('')
  const [dragFrom, setDragFrom] = useState<number | null>(null)
  const [exported, setExported] = useState<PlaylistExportResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  const refreshList = useCallback(() => {
    api.listPlaylists().then(setPlaylists).catch((ex) => setError(String(ex)))
  }, [])

  useEffect(refreshList, [refreshList])

  useEffect(() => {
    if (openId === null) {
      setPlaylist(null)
      return
    }
    api.getPlaylist(openId).then(setPlaylist).catch((ex) => setError(String(ex)))
  }, [openId])

  const run = async (action: () => Promise<PlaylistResponse>) => {
    setError(null)
    try {
      setPlaylist(await action())
      refreshList()
    } catch (ex) {
      setError(ex instanceof ApiError ? `${ex.errorCode}: ${ex.message}` : String(ex))
    }
  }

  const create = async () => {
    if (!newName.trim()) return
    setError(null)
    try {
      const created = await api.createPlaylist(newName)
      setNewName('')
      refreshList()
      setOpenId(created.id)
    } catch (ex) {
      setError(ex instanceof ApiError ? `${ex.errorCode}: ${ex.message}` : String(ex))
    }
  }

  const remove = async (id: number) => {
    setError(null)
    try {
      await api.deletePlaylist(id)
      if (openId === id) setOpenId(null)
      refreshList()
    } catch (ex) {
      setError(ex instanceof ApiError ? `${ex.errorCode}: ${ex.message}` : String(ex))
    }
  }

  // dokładanie zaznaczonych z tabeli biblioteki: po jednym, duplikaty pomijamy
  const addSelected = async () => {
    if (openId === null) return
    setError(null)
    const onPlaylist = new Set((playlist?.tracks ?? []).map((entry) => entry.track.spotifyId))
    for (const spotifyId of selectedIds) {
      if (onPlaylist.has(spotifyId)) continue
      try {
        setPlaylist(await api.addPlaylistTrack(openId, spotifyId))
      } catch (ex) {
        setError(ex instanceof ApiError ? `${ex.errorCode}: ${ex.message}` : String(ex))
      }
    }
    refreshList()
    onSelectionUsed()
  }

  const exportToSpotify = async () => {
    if (openId === null) return
    setError(null)
    try {
      setExported(await api.exportPlaylist(openId))
      refreshList()
    } catch (ex) {
      setError(ex instanceof ApiError ? `${ex.errorCode}: ${ex.message}` : String(ex))
    }
  }

  const drop = (to: number) => {
    if (openId === null || playlist === null || dragFrom === null || dragFrom === to) return
    const order = playlist.tracks.map((entry) => entry.track.spotifyId)
    const [moved] = order.splice(dragFrom, 1)
    order.splice(to, 0, moved)
    setDragFrom(null)
    return run(() => api.reorderPlaylist(openId, order))
  }

  return (
    <section className="panel" aria-label="Playlisty">
      <h2>Playlisty</h2>

      <div className="row">
        <input
          type="text"
          placeholder="nazwa nowego setu"
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          data-testid="playlist-name"
        />
        <button onClick={create} data-testid="playlist-create">
          Utwórz
        </button>
      </div>

      <ul className="playlist-list">
        {playlists.map((entry) => (
          <li key={entry.id} className={entry.id === openId ? 'selected' : undefined}>
            <button className="link" onClick={() => setOpenId(entry.id === openId ? null : entry.id)}>
              {entry.name}
            </button>
            <span className="muted"> — {entry.trackCount} utw.</span>
            {entry.spotifyPlaylistId && <span className="muted"> · na Spotify</span>}
            <button className="link danger-link" onClick={() => remove(entry.id)}>
              usuń
            </button>
          </li>
        ))}
        {playlists.length === 0 && <li className="muted">Brak playlist — utwórz pierwszy set.</li>}
      </ul>

      {playlist && (
        <>
          <h3>
            {playlist.name}{' '}
            <span className="muted">({playlist.tracks.length} utworów, przeciągnij, by zmienić kolejność)</span>
          </h3>
          <div className="row">
            <button onClick={addSelected} disabled={selectedIds.size === 0} data-testid="playlist-add-selected">
              Dodaj zaznaczone ({selectedIds.size})
            </button>
            <button
              onClick={exportToSpotify}
              disabled={playlist.tracks.length === 0}
              data-testid="playlist-export"
            >
              {playlist.spotifyPlaylistId ? 'Nadpisz na Spotify' : 'Eksportuj na Spotify'}
            </button>
          </div>
          {exported && exported.playlistId === playlist.id && (
            <p className="report" data-testid="export-report">
              wyeksportowano <strong>{exported.exportedTracks}</strong> utworów —{' '}
              <a href={exported.spotifyUrl} target="_blank" rel="noreferrer">
                otwórz na Spotify
              </a>
            </p>
          )}
          <ol className="set-list" data-testid="set-list">
            {playlist.tracks.map((entry, index) => (
              <li
                key={entry.track.spotifyId}
                draggable
                onDragStart={() => setDragFrom(index)}
                onDragOver={(e) => e.preventDefault()}
                onDrop={() => drop(index)}
              >
                <span className="handle">⠿</span>
                <span className="set-title">
                  {entry.track.title ?? entry.track.spotifyId}
                  <span className="muted"> — {entry.track.artist ?? '—'}</span>
                </span>
                <span className="muted">{entry.track.bpm ?? '—'} BPM</span>
                <span className={`slot slot-${entry.djSlot ?? 'NONE'}`}>
                  {entry.djSlot ? SLOT_LABELS[entry.djSlot] ?? entry.djSlot : 'brak danych'}
                  {entry.djSlotOverride && ' ✱'}
                </span>
                <button
                  className="link danger-link"
                  onClick={() => run(() => api.removePlaylistTrack(playlist.id, entry.track.spotifyId))}
                >
                  usuń
                </button>
              </li>
            ))}
            {playlist.tracks.length === 0 && (
              <li className="muted">Pusty set — zaznacz utwory w tabeli i dodaj je tutaj.</li>
            )}
          </ol>
        </>
      )}

      {error && <p className="error">{error}</p>}
    </section>
  )
}
