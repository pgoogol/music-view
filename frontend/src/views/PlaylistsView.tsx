// Widok playlist (M3.2) — przegląd tego, co przyszło ze Spotify i co powstało
// w planerze: kafle playlist, a po wejściu do środka szukanie po utworach,
// krzywa tempa i zwijane sekcje faz wieczoru. Widok jest do czytania
// (układanie kolejności zostaje w zakładce Sety).

import { useEffect, useMemo, useState } from 'react'
import { api, type PlaylistResponse, type PlaylistSummaryResponse, type PlaylistTrackResponse } from '../api'
import BpmCurve from '../components/BpmCurve'
import Collapsible from '../components/Collapsible'
import { useToast } from '../components/Toasts'
import { useHashRoute } from '../hooks/useHashRoute'
import {
  DASH,
  SLOT_LABELS,
  SLOT_ORDER,
  formatDateTime,
  formatDuration,
  formatTotalDuration,
  spotifyTrackUrl,
  type SlotKey,
} from '../format'
import { computeSetStats } from '../setPlanner'

const SECTIONS = [...SLOT_ORDER, 'UNKNOWN'] as const

type SectionKey = (typeof SECTIONS)[number]

const SECTION_LABELS: Record<SectionKey, string> = {
  ...SLOT_LABELS,
  UNKNOWN: 'bez slotu',
}

interface Props {
  refreshKey: number
}

function matches(entry: PlaylistTrackResponse, needle: string): boolean {

  if (!needle) return true
  const haystack = `${entry.track.title ?? ''} ${entry.track.artist ?? ''} ${entry.track.album ?? ''}`
  return haystack.toLowerCase().includes(needle)
}

function sectionOf(entry: PlaylistTrackResponse): SectionKey {

  const slot = entry.djSlot
  return slot && SLOT_ORDER.includes(slot as SlotKey) ? (slot as SectionKey) : 'UNKNOWN'
}

export default function PlaylistsView({ refreshKey }: Props) {

  const { params, setParams, navigate } = useHashRoute()
  const { reportError } = useToast()

  const openId = params.get('pl') ? Number(params.get('pl')) : null
  const [playlists, setPlaylists] = useState<PlaylistSummaryResponse[]>([])
  const [playlist, setPlaylist] = useState<PlaylistResponse | null>(null)
  const [listFilter, setListFilter] = useState('')
  const [trackFilter, setTrackFilter] = useState('')

  useEffect(() => {
    let current = true
    api
      .listPlaylists()
      .then((loaded) => {
        if (current) setPlaylists(loaded)
      })
      .catch((error) => reportError(error, 'Nie udało się pobrać playlist'))
    return () => {
      current = false
    }
  }, [refreshKey, reportError])

  useEffect(() => {
    if (openId === null) {
      setPlaylist(null)
      return
    }
    let current = true
    setTrackFilter('')
    api
      .getPlaylist(openId)
      .then((loaded) => {
        if (current) setPlaylist(loaded)
      })
      .catch((error) => reportError(error, 'Nie udało się otworzyć playlisty'))
    return () => {
      current = false
    }
  }, [openId, refreshKey, reportError])

  const visiblePlaylists = useMemo(() => {
    const needle = listFilter.trim().toLowerCase()
    return needle ? playlists.filter((entry) => entry.name.toLowerCase().includes(needle)) : playlists
  }, [playlists, listFilter])

  if (openId !== null) {
    return (
      <PlaylistDetails
        playlist={playlist}
        filter={trackFilter}
        onFilterChange={setTrackFilter}
        onBack={() => setParams({ pl: undefined })}
        // playlista i set to ta sama encja — z podglądu wchodzi się prosto w planer
        onPlan={(id) => navigate('sets', new URLSearchParams({ set: String(id) }))}
      />
    )
  }

  return (
    <section className="panel" aria-label="Playlisty">
      <h2>Playlisty ({playlists.length})</h2>
      <p className="muted">
        Wszystko, co trafiło do music-view: playlisty zaciągnięte ze Spotify i sety ułożone
        w planerze. Wejdź do środka, żeby przejrzeć skład i tempo.
      </p>

      <div className="row">
        <input
          type="search"
          placeholder="Szukaj playlisty po nazwie…"
          value={listFilter}
          onChange={(event) => setListFilter(event.target.value)}
          data-testid="playlist-search"
        />
        {listFilter && <span className="muted">znaleziono: {visiblePlaylists.length}</span>}
      </div>

      <div className="playlist-grid" data-testid="playlist-grid">
        {visiblePlaylists.map((entry) => (
          <button
            key={entry.id}
            className="playlist-card"
            onClick={() => setParams({ pl: entry.id })}
            data-testid={`playlist-card-${entry.id}`}
          >
            <span className="playlist-card-name">{entry.name}</span>
            <span className="playlist-card-meta">
              <span>{entry.trackCount} utw.</span>
              <span>dodano {formatDateTime(entry.createdAt)}</span>
              {entry.spotifyPlaylistId && <span className="badge">Spotify</span>}
            </span>
          </button>
        ))}
      </div>

      {playlists.length === 0 && (
        <p className="muted">
          Brak playlist — zaimportuj je w zakładce Import albo ułóż set w zakładce Sety.
        </p>
      )}
      {playlists.length > 0 && visiblePlaylists.length === 0 && (
        <p className="muted">Żadna playlista nie pasuje do „{listFilter}".</p>
      )}
    </section>
  )
}

interface DetailsProps {
  playlist: PlaylistResponse | null
  filter: string
  onFilterChange: (value: string) => void
  onBack: () => void
  onPlan: (id: number) => void
}

function PlaylistDetails({ playlist, filter, onFilterChange, onBack, onPlan }: DetailsProps) {

  const needle = filter.trim().toLowerCase()
  const tracks = playlist?.tracks ?? []
  const found = useMemo(() => tracks.filter((entry) => matches(entry, needle)), [tracks, needle])
  const stats = useMemo(() => computeSetStats(tracks), [tracks])

  const points = tracks.map((entry, index) => ({
    position: index + 1,
    bpm: entry.track.bpm,
    label: entry.track.title ?? entry.track.spotifyId,
  }))

  return (
    <section className="panel" aria-label="Playlista">
      <div className="crate-head">
        <button className="link" onClick={onBack} data-testid="playlist-back">
          ‹ wszystkie playlisty
        </button>
        {playlist && <h2>{playlist.name}</h2>}
        {playlist && (
          <span className="muted">
            {stats.trackCount} utw. · {formatTotalDuration(stats.totalDurationMs)}
            {playlist.spotifyPlaylistId && <span className="badge">Spotify</span>}
          </span>
        )}
      </div>

      {!playlist && <p className="muted">Ładowanie…</p>}

      {playlist && (
        <>
          <div className="row">
            <input
              type="search"
              placeholder="Szukaj w playliście: tytuł / wykonawca / album…"
              value={filter}
              onChange={(event) => onFilterChange(event.target.value)}
              data-testid="track-search"
            />
            {needle && (
              <span className="muted" data-testid="track-search-summary">
                pasuje {found.length} z {tracks.length}
              </span>
            )}
            <span className="spacer" />
            <button onClick={() => onPlan(playlist.id)} data-testid="playlist-plan">
              Otwórz w planerze setów
            </button>
          </div>

          <Collapsible
            title="Krzywa tempa"
            count={
              stats.bpmMin === null
                ? 'brak BPM'
                : `${stats.bpmMin}–${stats.bpmMax} BPM, średnio ${stats.averageBpm}`
            }
            testId="section-tempo"
          >
            <BpmCurve points={points} caption="tempo kolejnych pozycji playlisty" />
            {stats.missingBpm > 0 && (
              <p className="muted">
                utwory bez BPM: {stats.missingBpm} — wzbogać je, żeby krzywa była pełna.
              </p>
            )}
          </Collapsible>

          {SECTIONS.map((section) => {
            const inSection = found.filter((entry) => sectionOf(entry) === section)
            if (inSection.length === 0) return null
            return (
              <Collapsible
                // szukanie resetuje zwinięcie — inaczej trafienia chowałyby się w zamkniętej sekcji
                key={`${section}-${needle ? 'search' : 'all'}`}
                title={
                  <span className="slot">
                    <span className={`slot-dot slot-bg-${section}`} aria-hidden="true" />
                    {SECTION_LABELS[section]}
                  </span>
                }
                count={`${inSection.length} utw.`}
                testId={`section-${section}`}
              >
                <ul className="crate-tracks">
                  {inSection.map((entry) => (
                    <li
                      key={entry.track.spotifyId}
                      className={needle ? 'crate-hit' : undefined}
                    >
                      <span className="muted set-position">{entry.position + 1}.</span>
                      <span className="set-title">
                        <a
                          href={spotifyTrackUrl(entry.track.spotifyId)}
                          target="_blank"
                          rel="noreferrer"
                        >
                          {entry.track.title ?? entry.track.spotifyId}
                        </a>
                        <span className="muted"> — {entry.track.artist ?? DASH}</span>
                      </span>
                      <span className="muted set-time">{formatDuration(entry.track.durationMs)}</span>
                      <span className="muted set-bpm">{entry.track.bpm ?? DASH} BPM</span>
                    </li>
                  ))}
                </ul>
              </Collapsible>
            )
          })}

          {tracks.length === 0 && <p className="muted">Ta playlista jest pusta.</p>}
          {tracks.length > 0 && found.length === 0 && (
            <p className="muted" data-testid="no-track-hits">
              Żaden utwór w tej playliście nie pasuje do „{filter}".
            </p>
          )}
        </>
      )}
    </section>
  )
}
