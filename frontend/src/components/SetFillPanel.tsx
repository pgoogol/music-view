// Uzupełnianie gotowego setu (M4.4/D32): dalszy ciąg wieczoru do obejrzenia,
// nie zapis. Utwory dopisuje dopiero „Dopisz do setu" — istniejącą drogą przez
// POST /api/playlists/{id}/tracks, więc domykanie zostaje bezstanowe.

import { useEffect, useState } from 'react'
import { api, type SetFillResponse } from '../api'
import { useToast } from './Toasts'
import { DASH, formatDuration, slotLabel } from '../format'

const GENRES = ['LATIN', 'ROCK', 'POP', 'DISCO', 'DISCO_POLO', 'ELECTRONIC', 'HIP_HOP', 'OTHER']
const TARGETS = [60, 90, 120, 180, 240, 300]
const RATINGS = [1, 2, 3, 4, 5]

interface Props {
  playlistId: number
  disabled: boolean
  onAppend: (spotifyIds: string[]) => Promise<void>
}

export default function SetFillPanel({ playlistId, disabled, onAppend }: Props) {

  const { reportError } = useToast()
  const [targetMinutes, setTargetMinutes] = useState(180)
  const [genreFamily, setGenreFamily] = useState('')
  const [ratingMin, setRatingMin] = useState('')
  const [inLibrary, setInLibrary] = useState(true)
  const [fill, setFill] = useState<SetFillResponse | null>(null)
  const [busy, setBusy] = useState(false)

  // podgląd dotyczy konkretnego setu — po przejściu do innego przestaje pasować
  useEffect(() => setFill(null), [playlistId])

  const preview = async (seed?: number) => {
    setBusy(true)
    try {
      setFill(
        await api.fillSet(playlistId, {
          targetMinutes,
          seed,
          genreFamily: genreFamily || undefined,
          ratingMin: ratingMin ? Number(ratingMin) : undefined,
          inLibrary: inLibrary ? true : undefined,
        }),
      )
    } catch (error) {
      setFill(null)
      reportError(error, 'Nie udało się uzupełnić setu')
    } finally {
      setBusy(false)
    }
  }

  const append = async () => {
    if (!fill || fill.tracks.length === 0) return
    setBusy(true)
    try {
      await onAppend(fill.tracks.map((entry) => entry.track.spotifyId))
      setFill(null)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel-inset" aria-label="Uzupełnianie setu" data-testid="set-fill">
      <h3>Uzupełnij set</h3>
      <p className="muted">
        Dokłada dalszy ciąg wieczoru do tego, co już masz — z tymi samymi zasadami co generator.
        Długość dotyczy całego setu, nie tego, co dochodzi.
      </p>

      <div className="filters">
        <label className="filter-group">
          <span>do</span>
          <select
            value={targetMinutes}
            onChange={(event) => setTargetMinutes(Number(event.target.value))}
            aria-label="docelowa długość setu"
          >
            {TARGETS.map((value) => (
              <option key={value} value={value}>
                {value} min
              </option>
            ))}
          </select>
        </label>
        <label className="filter-group">
          <span>gatunek</span>
          <select
            value={genreFamily}
            onChange={(event) => setGenreFamily(event.target.value)}
            aria-label="gatunek dobieranych utworów"
          >
            <option value="">wszystkie</option>
            {GENRES.map((genre) => (
              <option key={genre} value={genre}>
                {genre}
              </option>
            ))}
          </select>
        </label>
        <label className="filter-group">
          <span>ocena</span>
          <select
            value={ratingMin}
            onChange={(event) => setRatingMin(event.target.value)}
            aria-label="ocena co najmniej przy uzupełnianiu"
          >
            <option value="">dowolna</option>
            {RATINGS.map((value) => (
              <option key={value} value={value}>
                {'★'.repeat(value)} i wyżej
              </option>
            ))}
          </select>
        </label>
        <label className="filter-check">
          <input
            type="checkbox"
            checked={inLibrary}
            onChange={(event) => setInLibrary(event.target.checked)}
          />
          <span>tylko z biblioteki</span>
        </label>
        <button onClick={() => preview()} disabled={busy || disabled} data-testid="fill-set">
          {busy ? 'Dobieram…' : 'Uzupełnij'}
        </button>
      </div>

      {fill && (
        <div className="proposal" data-testid="fill-preview">
          <p className="muted result-summary">
            teraz {fill.currentTrackCount} utw. · {formatDuration(fill.currentDurationMs)} → po
            uzupełnieniu {fill.currentTrackCount + fill.addedTrackCount} utw. ·{' '}
            {formatDuration(fill.totalDurationMs)} z {formatDuration(fill.targetDurationMs)} · seed{' '}
            {fill.seed}
          </p>

          {fill.notes.length > 0 && (
            <div className="warnings" data-testid="fill-notes">
              <ul>
                {fill.notes.map((note) => (
                  <li key={note}>{note}</li>
                ))}
              </ul>
            </div>
          )}

          <ol className="proposal-tracks">
            {fill.tracks.map((entry) => (
              <li key={entry.track.spotifyId}>
                {/* kolor niesie kropka, etykieta zostaje w kolorze tekstu (D22) */}
                <span className="slot">
                  <span
                    className={`slot-dot slot-bg-${entry.djSlot ?? 'UNKNOWN'}`}
                    aria-hidden="true"
                  />
                  {slotLabel(entry.djSlot)}
                </span>
                <span className="proposal-title">{entry.track.title ?? entry.track.spotifyId}</span>
                <span className="muted">{entry.track.artist ?? DASH}</span>
                <span className="muted set-bpm">{entry.track.bpm ?? DASH} BPM</span>
                <span className="muted set-bpm">{entry.track.camelot ?? DASH}</span>
              </li>
            ))}
          </ol>

          <div className="row">
            <button
              onClick={append}
              disabled={busy || disabled || fill.tracks.length === 0}
              data-testid="fill-append"
            >
              Dopisz {fill.addedTrackCount} utworów do setu
            </button>
            <button
              className="link"
              onClick={() => preview()}
              disabled={busy}
              data-testid="fill-reroll"
            >
              spróbuj inaczej
            </button>
            <button
              className="link"
              onClick={() => preview(fill.seed)}
              disabled={busy}
              title="ten sam seed daje ten sam dalszy ciąg"
            >
              powtórz ten układ
            </button>
          </div>
        </div>
      )}
    </section>
  )
}
