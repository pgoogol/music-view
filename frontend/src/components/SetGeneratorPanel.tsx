// Generator setu (M4.2/D26): propozycja do obejrzenia, nie zapis. Playlistę
// zakłada dopiero „Utwórz set z propozycji" — istniejącą drogą przez
// POST /api/playlists, więc generator zostaje bezstanowy.

import { useState } from 'react'
import {
  SET_CURVES,
  SET_CURVE_LABELS,
  api,
  type SetCurve,
  type SetProposalResponse,
} from '../api'
import { useToast } from './Toasts'
import { DASH, formatDuration, slotLabel } from '../format'

const GENRES = ['LATIN', 'ROCK', 'POP', 'DISCO', 'DISCO_POLO', 'ELECTRONIC', 'HIP_HOP', 'OTHER']
const TARGETS = [60, 90, 120, 180, 240, 300]
const RATINGS = [1, 2, 3, 4, 5]

interface Props {
  onCreated: (playlistId: number) => void
}

export default function SetGeneratorPanel({ onCreated }: Props) {

  const { notify, reportError } = useToast()
  const [targetMinutes, setTargetMinutes] = useState(120)
  const [curve, setCurve] = useState<SetCurve>('STANDARD')
  const [genreFamily, setGenreFamily] = useState('')
  const [ratingMin, setRatingMin] = useState('')
  const [inLibrary, setInLibrary] = useState(true)
  const [proposal, setProposal] = useState<SetProposalResponse | null>(null)
  const [busy, setBusy] = useState(false)

  const propose = async (seed?: number) => {
    setBusy(true)
    try {
      setProposal(
        await api.proposeSet({
          targetMinutes,
          curve,
          seed,
          genreFamily: genreFamily || undefined,
          ratingMin: ratingMin ? Number(ratingMin) : undefined,
          inLibrary: inLibrary ? true : undefined,
        }),
      )
    } catch (error) {
      setProposal(null)
      reportError(error, 'Nie udało się ułożyć setu')
    } finally {
      setBusy(false)
    }
  }

  // zapis idzie istniejącą drogą (D26) — utwór po utworze, tak jak przy
  // dokładaniu zaznaczonych z biblioteki
  const materialize = async () => {
    if (!proposal || proposal.tracks.length === 0) return
    setBusy(true)
    try {
      const name = `Propozycja ${targetMinutes} min (seed ${proposal.seed})`
      const created = await api.createPlaylist(name)
      for (const entry of proposal.tracks) {
        await api.addPlaylistTrack(created.id, entry.track.spotifyId)
      }
      notify(`Utworzono set „${name}" z ${proposal.tracks.length} utworów`)
      setProposal(null)
      onCreated(created.id)
    } catch (error) {
      reportError(error, 'Nie udało się zapisać setu z propozycji')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel" aria-label="Generator setu" data-testid="set-generator">
      <h2>Zaproponuj set</h2>
      <p className="muted">
        Układa wieczór z biblioteki: rozgrzewka → środek → szczyt → zamknięcie, bez powtórek
        wykonawcy częściej niż raz na pół godziny. Nic nie zapisuje, dopóki nie klikniesz zapisu.
      </p>

      <div className="filters">
        <label className="filter-group">
          <span>długość</span>
          <select
            value={targetMinutes}
            onChange={(event) => setTargetMinutes(Number(event.target.value))}
            aria-label="długość setu"
          >
            {TARGETS.map((value) => (
              <option key={value} value={value}>
                {value} min
              </option>
            ))}
          </select>
        </label>
        <label className="filter-group">
          <span>profil</span>
          <select
            value={curve}
            onChange={(event) => setCurve(event.target.value as SetCurve)}
            aria-label="profil wieczoru"
            title="profil zmienia proporcje faz wieczoru, nie ich kolejność"
          >
            {SET_CURVES.map((value) => (
              <option key={value} value={value}>
                {SET_CURVE_LABELS[value]}
              </option>
            ))}
          </select>
        </label>
        <label className="filter-group">
          <span>gatunek</span>
          <select
            value={genreFamily}
            onChange={(event) => setGenreFamily(event.target.value)}
            aria-label="gatunek puli"
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
            aria-label="ocena co najmniej w puli"
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
        <button onClick={() => propose()} disabled={busy} data-testid="propose-set">
          {busy ? 'Układam…' : 'Zaproponuj'}
        </button>
      </div>

      {proposal && (
        <div className="proposal" data-testid="set-proposal">
          <p className="muted result-summary">
            {proposal.trackCount} utworów · {formatDuration(proposal.totalDurationMs)} z{' '}
            {formatDuration(proposal.targetDurationMs)} · seed {proposal.seed}
          </p>

          {proposal.notes.length > 0 && (
            <div className="warnings" data-testid="proposal-notes">
              <ul>
                {proposal.notes.map((note) => (
                  <li key={note}>{note}</li>
                ))}
              </ul>
            </div>
          )}

          <ol className="proposal-tracks">
            {proposal.tracks.map((entry) => (
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
            <button onClick={materialize} disabled={busy} data-testid="materialize-set">
              Utwórz set z propozycji
            </button>
            <button
              className="link"
              onClick={() => propose()}
              disabled={busy}
              data-testid="reroll-set"
            >
              spróbuj inaczej
            </button>
            <button
              className="link"
              onClick={() => propose(proposal.seed)}
              disabled={busy}
              title="ten sam seed daje tę samą propozycję"
            >
              powtórz ten układ
            </button>
          </div>
        </div>
      )}
    </section>
  )
}
