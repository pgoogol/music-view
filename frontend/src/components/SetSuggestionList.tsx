// Kandydaci na jedno miejsce w secie (M4.4/D32). Backend nie losuje i nie
// oddaje punktacji — kolejność niesie ocenę, a DJ dostaje powody, po których
// widać, dlaczego utwór pasuje: różnicę tempa i zgodność tonacji z sąsiadem.

import { type SuggestedTrackResponse } from '../api'
import { DASH, slotLabel } from '../format'

interface Props {
  position: number
  suggestions: SuggestedTrackResponse[]
  busy: boolean
  onPick: (spotifyId: string) => void
  onClose: () => void
}

/** „+6 BPM", „−2 BPM", a przy braku danych po którejś stronie — myślnik. */
function bpmDeltaLabel(delta: number | null): string {
  if (delta === null) return DASH
  if (delta === 0) return 'to samo tempo'
  return `${delta > 0 ? '+' : '−'}${Math.abs(delta)} BPM`
}

export default function SetSuggestionList({
  position,
  suggestions,
  busy,
  onPick,
  onClose,
}: Props) {

  return (
    <section className="panel-inset" aria-label="Dobrane utwory" data-testid="set-suggestions">
      <div className="set-head">
        <h3>Kandydaci na miejsce {position + 1}</h3>
        <button className="link" onClick={onClose} data-testid="suggestions-close">
          zamknij
        </button>
      </div>
      <p className="muted">
        Z biblioteki, uszeregowani wg dopasowania do sąsiadów: tempo, tonacja i faza wieczoru.
        Wykonawca grający w promieniu pół godziny nie wchodzi w ogóle.
      </p>

      {suggestions.length === 0 && (
        <p className="muted" data-testid="suggestions-empty">
          Nic nie pasuje w to miejsce — poluzuj filtry biblioteki albo dobierz gdzie indziej.
        </p>
      )}

      <ul className="proposal-tracks">
        {suggestions.map((entry) => (
          <li key={entry.track.spotifyId}>
            <span className="slot">
              {/* kolor niesie kropka, etykieta zostaje w kolorze tekstu (D22) */}
              <span
                className={`slot-dot slot-bg-${entry.djSlot ?? 'UNKNOWN'}`}
                aria-hidden="true"
              />
              {slotLabel(entry.djSlot)}
            </span>
            <span className="proposal-title">{entry.track.title ?? entry.track.spotifyId}</span>
            <span className="muted">{entry.track.artist ?? DASH}</span>
            <span className="muted set-bpm">{bpmDeltaLabel(entry.bpmDelta)}</span>
            <span className="muted set-bpm">
              {entry.harmonic === null
                ? DASH
                : `${entry.track.camelot ?? DASH} ${entry.harmonic ? '✓' : '✕'}`}
            </span>
            <button
              className="link"
              disabled={busy}
              onClick={() => onPick(entry.track.spotifyId)}
              aria-label={`wstaw na miejsce ${position + 1}: ${entry.track.title ?? entry.track.spotifyId}`}
            >
              wstaw
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}
