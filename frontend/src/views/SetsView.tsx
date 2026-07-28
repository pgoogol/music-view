// Planer setów (M2.3, rozbudowa M3.1): lista setów obok składu wieczoru,
// statystyki i ostrzeżenia, układanie wg slotów D9 oraz kolejność zmieniana
// przeciąganiem albo strzałkami (drag&drop nie działa z klawiatury).

import { useCallback, useEffect, useState } from 'react'
import {
  api,
  type PlaylistResponse,
  type PlaylistSummaryResponse,
} from '../api'
import SetStats from '../components/SetStats'
import { useToast } from '../components/Toasts'
import { useHashRoute } from '../hooks/useHashRoute'
import { DASH, formatDuration, slotLabel } from '../format'
import { arrangeBySlot } from '../setPlanner'

interface Props {
  selectedIds: ReadonlySet<string>
  onSelectionUsed: () => void
}

export default function SetsView({ selectedIds, onSelectionUsed }: Props) {

  const { params, setParams } = useHashRoute()
  const { notify, reportError } = useToast()

  const openId = params.get('set') ? Number(params.get('set')) : null
  const [playlists, setPlaylists] = useState<PlaylistSummaryResponse[]>([])
  const [playlist, setPlaylist] = useState<PlaylistResponse | null>(null)
  const [newName, setNewName] = useState('')
  const [dragFrom, setDragFrom] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)

  const refreshList = useCallback(() => {
    api.listPlaylists().then(setPlaylists).catch((error) => reportError(error, 'Nie udało się pobrać setów'))
  }, [reportError])

  useEffect(refreshList, [refreshList])

  useEffect(() => {
    if (openId === null) {
      setPlaylist(null)
      return
    }
    let current = true
    api
      .getPlaylist(openId)
      .then((loaded) => {
        if (current) setPlaylist(loaded)
      })
      .catch((error) => reportError(error, 'Nie udało się otworzyć setu'))
    return () => {
      current = false
    }
  }, [openId, reportError])

  const run = async (action: () => Promise<PlaylistResponse>, message?: string) => {
    setBusy(true)
    try {
      setPlaylist(await action())
      refreshList()
      if (message) notify(message)
    } catch (error) {
      reportError(error)
    } finally {
      setBusy(false)
    }
  }

  const create = async () => {
    if (!newName.trim()) return
    try {
      const created = await api.createPlaylist(newName.trim())
      setNewName('')
      refreshList()
      setParams({ set: created.id })
      notify(`Utworzono set „${created.name}"`)
    } catch (error) {
      reportError(error, 'Nie udało się utworzyć setu')
    }
  }

  const rename = async () => {
    if (!playlist) return
    const name = window.prompt('Nowa nazwa setu', playlist.name)
    if (!name || name.trim() === playlist.name) return
    try {
      await api.renamePlaylist(playlist.id, name.trim())
      setPlaylist(await api.getPlaylist(playlist.id))
      refreshList()
      notify('Zmieniono nazwę setu')
    } catch (error) {
      reportError(error, 'Nie udało się zmienić nazwy')
    }
  }

  const remove = async (id: number, name: string) => {
    if (!window.confirm(`Usunąć set „${name}"? Utwory zostaną w bibliotece.`)) return
    try {
      await api.deletePlaylist(id)
      if (openId === id) setParams({ set: undefined })
      refreshList()
      notify('Usunięto set')
    } catch (error) {
      reportError(error, 'Nie udało się usunąć setu')
    }
  }

  // dokładanie zaznaczonych z biblioteki: po jednym, utwory już w secie pomijamy
  const addSelected = async () => {
    if (openId === null || selectedIds.size === 0) return
    setBusy(true)
    const onSet = new Set((playlist?.tracks ?? []).map((entry) => entry.track.spotifyId))
    let added = 0
    try {
      for (const spotifyId of selectedIds) {
        if (onSet.has(spotifyId)) continue
        setPlaylist(await api.addPlaylistTrack(openId, spotifyId))
        added += 1
      }
      refreshList()
      onSelectionUsed()
      notify(added > 0 ? `Dodano ${added} utworów do setu` : 'Wszystkie zaznaczone są już w secie')
    } catch (error) {
      reportError(error, 'Nie udało się dodać utworów')
    } finally {
      setBusy(false)
    }
  }

  const move = (from: number, to: number) => {
    if (!playlist || from === to || to < 0 || to >= playlist.tracks.length) return
    const order = playlist.tracks.map((entry) => entry.track.spotifyId)
    const [moved] = order.splice(from, 1)
    order.splice(to, 0, moved)
    return run(() => api.reorderPlaylist(playlist.id, order))
  }

  const autoArrange = () => {
    if (!playlist || playlist.tracks.length < 2) return
    return run(
      () => api.reorderPlaylist(playlist.id, arrangeBySlot(playlist.tracks)),
      'Ułożono set wg faz wieczoru (D9)',
    )
  }

  const exportToSpotify = async () => {
    if (!playlist) return
    setBusy(true)
    try {
      const exported = await api.exportPlaylist(playlist.id)
      refreshList()
      notify(`Wyeksportowano ${exported.exportedTracks} utworów na Spotify`)
      window.open(exported.spotifyUrl, '_blank', 'noreferrer')
    } catch (error) {
      reportError(error, 'Eksport na Spotify nie powiódł się')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="sets-layout">
      <section className="panel" aria-label="Sety">
        <h2>Sety</h2>
        <div className="row">
          <input
            type="text"
            placeholder="nazwa nowego setu"
            value={newName}
            onChange={(event) => setNewName(event.target.value)}
            onKeyDown={(event) => event.key === 'Enter' && create()}
            data-testid="playlist-name"
          />
          <button onClick={create} data-testid="playlist-create">
            Utwórz
          </button>
        </div>

        <ul className="playlist-list">
          {playlists.map((entry) => (
            <li key={entry.id} className={entry.id === openId ? 'selected' : undefined}>
              <button
                className="link"
                onClick={() => setParams({ set: entry.id === openId ? undefined : entry.id })}
              >
                {entry.name}
              </button>
              <span className="muted"> {entry.trackCount} utw.</span>
              {entry.spotifyPlaylistId && (
                <span className="badge" title="set ma odpowiednik na Spotify">
                  Spotify
                </span>
              )}
              <button className="link danger-link" onClick={() => remove(entry.id, entry.name)}>
                usuń
              </button>
            </li>
          ))}
          {playlists.length === 0 && <li className="muted">Brak setów — utwórz pierwszy.</li>}
        </ul>
      </section>

      <section className="panel" aria-label="Skład setu">
        {!playlist && <p className="muted">Wybierz set z listy albo utwórz nowy.</p>}
        {playlist && (
          <>
            <div className="set-head">
              <h2>{playlist.name}</h2>
              <button className="link" onClick={rename}>
                zmień nazwę
              </button>
            </div>

            <div className="row">
              <button onClick={addSelected} disabled={busy || selectedIds.size === 0} data-testid="playlist-add-selected">
                Dodaj zaznaczone ({selectedIds.size})
              </button>
              <button onClick={autoArrange} disabled={busy || playlist.tracks.length < 2} data-testid="playlist-arrange">
                Ułóż wg faz wieczoru
              </button>
              <button
                onClick={exportToSpotify}
                disabled={busy || playlist.tracks.length === 0}
                data-testid="playlist-export"
              >
                {playlist.spotifyPlaylistId ? 'Nadpisz na Spotify' : 'Eksportuj na Spotify'}
              </button>
            </div>

            <SetStats tracks={playlist.tracks} />

            <ol className="set-list" data-testid="set-list">
              {playlist.tracks.map((entry, index) => (
                <li
                  key={entry.track.spotifyId}
                  draggable
                  onDragStart={() => setDragFrom(index)}
                  onDragEnd={() => setDragFrom(null)}
                  onDragOver={(event) => event.preventDefault()}
                  onDrop={() => dragFrom !== null && move(dragFrom, index)}
                  className={dragFrom === index ? 'dragging' : undefined}
                >
                  <span className="handle" aria-hidden="true">
                    ⠿
                  </span>
                  {/* pozycja jawnie, bo `display: flex` gasi numerację listy,
                      a ostrzeżenia o secie odwołują się właśnie do pozycji */}
                  <span className="muted set-position">{index + 1}.</span>
                  <span className="set-title">
                    {entry.track.title ?? entry.track.spotifyId}
                    <span className="muted"> — {entry.track.artist ?? DASH}</span>
                  </span>
                  <span className="muted set-time">{formatDuration(entry.track.durationMs)}</span>
                  <span className="muted set-bpm">{entry.track.bpm ?? DASH} BPM</span>
                  <span className="slot">
                    {/* kolor niesie kropka, etykieta zostaje w kolorze tekstu (czytelność) */}
                    <span
                      className={`slot-dot slot-bg-${entry.djSlot ?? 'UNKNOWN'}`}
                      aria-hidden="true"
                    />
                    {slotLabel(entry.djSlot)}
                    {entry.djSlotOverride && <span title="slot ustawiony ręcznie"> ✱</span>}
                  </span>
                  <span className="set-actions">
                    <button
                      className="link"
                      aria-label={`przesuń wyżej: ${entry.track.title ?? entry.track.spotifyId}`}
                      disabled={index === 0 || busy}
                      onClick={() => move(index, index - 1)}
                    >
                      ↑
                    </button>
                    <button
                      className="link"
                      aria-label={`przesuń niżej: ${entry.track.title ?? entry.track.spotifyId}`}
                      disabled={index === playlist.tracks.length - 1 || busy}
                      onClick={() => move(index, index + 1)}
                    >
                      ↓
                    </button>
                    <button
                      className="link danger-link"
                      onClick={() => run(() => api.removePlaylistTrack(playlist.id, entry.track.spotifyId))}
                    >
                      usuń
                    </button>
                  </span>
                </li>
              ))}
              {playlist.tracks.length === 0 && (
                <li className="muted">
                  Pusty set — zaznacz utwory w bibliotece i wróć tutaj, żeby je dodać.
                </li>
              )}
            </ol>
          </>
        )}
      </section>
    </div>
  )
}
