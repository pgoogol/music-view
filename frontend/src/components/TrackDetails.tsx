// Szuflada szczegółów utworu (M1.8, rozbudowa M3.1): okładka, link do Spotify,
// fakty katalogu oraz edycja danych prywatnych DJ-a (D3) — gwiazdki, chipsy
// tagów i slot z listy wartości enuma DjSlot zamiast wolnego tekstu.

import { useEffect, useState } from 'react'
import { api, type LibraryEntryResponse } from '../api'
import StarRating from './StarRating'
import TagChips from './TagChips'
import { useToast } from './Toasts'
import {
  DASH,
  SLOT_LABELS,
  SLOT_ORDER,
  SOURCE_LABELS,
  energyLabel,
  formatDateTime,
  formatDuration,
  spotifyTrackUrl,
  tempoLabel,
} from '../format'

interface Props {
  spotifyId: string
  onClose: () => void
  onChanged: () => void
}

export default function TrackDetails({ spotifyId, onClose, onChanged }: Props) {

  const { notify, reportError } = useToast()
  const [entry, setEntry] = useState<LibraryEntryResponse | null>(null)
  const [djNotes, setDjNotes] = useState('')
  const [tags, setTags] = useState<string[]>([])
  const [rating, setRating] = useState(0)
  const [slotOverride, setSlotOverride] = useState('')
  const [saving, setSaving] = useState(false)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let current = true
    api
      .getLibraryEntry(spotifyId)
      .then((loaded) => {
        if (!current) return
        setEntry(loaded)
        setDjNotes(loaded.djNotes ?? '')
        setTags(loaded.customTags ?? [])
        setRating(loaded.rating ?? 0)
        setSlotOverride(loaded.djSlotOverride ?? '')
      })
      .catch((error) => {
        if (!current) return
        setFailed(true)
        reportError(error, 'Nie udało się wczytać utworu')
      })
    return () => {
      current = false
    }
  }, [spotifyId, reportError])

  const save = async () => {
    setSaving(true)
    try {
      setEntry(await api.updateLibraryEntry(spotifyId, {
        djNotes,
        customTags: tags,
        rating,
        djSlotOverride: slotOverride,
      }))
      notify('Zapisano dane DJ-a')
      onChanged()
    } catch (error) {
      reportError(error, 'Nie udało się zapisać')
    } finally {
      setSaving(false)
    }
  }

  const remove = async () => {
    try {
      await api.deleteLibraryEntry(spotifyId)
      notify('Usunięto utwór z biblioteki')
      onChanged()
      onClose()
    } catch (error) {
      reportError(error, 'Nie udało się usunąć utworu')
    }
  }

  const track = entry?.track

  return (
    <div className="drawer-backdrop" onClick={onClose}>
      <aside className="drawer" onClick={(event) => event.stopPropagation()} data-testid="track-details">
        <button className="close" onClick={onClose} aria-label="zamknij">
          ×
        </button>

        {!track && !failed && <p className="muted">Ładowanie…</p>}
        {failed && (
          <p className="error">
            Nie udało się wczytać tego utworu — mógł zostać usunięty z biblioteki.
          </p>
        )}
        {track && (
          <>
            <div className="drawer-head">
              {track.albumImageUrl ? (
                <img src={track.albumImageUrl} alt="" className="cover cover-large" />
              ) : (
                <span className="cover cover-large cover-empty" aria-hidden="true" />
              )}
              <div>
                <h2>{track.title ?? spotifyId}</h2>
                <p className="muted">
                  {track.artist ?? DASH} — {track.album ?? 'brak albumu'}
                </p>
                <p className="drawer-meta">
                  <a href={spotifyTrackUrl(spotifyId)} target="_blank" rel="noreferrer">
                    otwórz na Spotify
                  </a>
                  {entry && (
                    <span className="badge" title="skąd utwór trafił do biblioteki">
                      {SOURCE_LABELS[entry.source] ?? entry.source}
                    </span>
                  )}
                </p>
              </div>
            </div>

            <dl className="track-facts">
              <dt>Rok</dt>
              <dd>{track.year ?? DASH}</dd>
              <dt>Czas</dt>
              <dd>{formatDuration(track.durationMs)}</dd>
              <dt>BPM</dt>
              <dd>
                {track.bpm ?? DASH} {track.bpmSource ? <span className="muted">({track.bpmSource})</span> : ''}
              </dd>
              <dt>Tempo</dt>
              <dd>{tempoLabel(track.tempoClass)}</dd>
              <dt>Tonacja</dt>
              <dd>{track.musicalKey ?? DASH}</dd>
              <dt>Taneczność</dt>
              <dd>{track.danceability ?? DASH}</dd>
              <dt>Gatunek</dt>
              <dd>{track.genreFamily ?? DASH}</dd>
              <dt>Styl</dt>
              <dd>{track.style ?? DASH}</dd>
              <dt>Energia</dt>
              <dd>{energyLabel(track.energy)}</dd>
              <dt>Popularność</dt>
              <dd>{track.popularity ?? DASH}</dd>
              <dt>O czym</dt>
              <dd>{track.lyricsTheme ?? DASH}</dd>
              <dt>Opis</dt>
              <dd>{track.descriptionPl ?? DASH}</dd>
              <dt>ISRC</dt>
              <dd>{track.isrc ?? DASH}</dd>
              <dt>Wzbogacono</dt>
              <dd>
                {track.enrichedAt
                  ? `${formatDateTime(track.enrichedAt)} (${track.modelUsed ?? '?'}, v${track.enrichVersion ?? '?'})`
                  : 'jeszcze nie'}
              </dd>
            </dl>

            <h3>Dane DJ-a</h3>
            <label className="field">
              uwagi
              <textarea
                value={djNotes}
                onChange={(event) => setDjNotes(event.target.value)}
                rows={3}
                data-testid="dj-notes"
              />
            </label>

            <div className="field">
              custom tagi
              <TagChips tags={tags} onChange={setTags} />
            </div>

            <div className="field">
              ocena
              <StarRating value={rating} onChange={setRating} />
            </div>

            <label className="field">
              slot wieczoru (pusty = liczony z BPM i energii, D9)
              <select
                value={slotOverride}
                onChange={(event) => setSlotOverride(event.target.value)}
                data-testid="dj-slot"
              >
                <option value="">automatyczny</option>
                {SLOT_ORDER.map((slot) => (
                  <option key={slot} value={slot}>
                    {SLOT_LABELS[slot]}
                  </option>
                ))}
              </select>
            </label>

            <div className="row">
              <button onClick={save} disabled={saving} data-testid="dj-save">
                {saving ? 'Zapisuję…' : 'Zapisz'}
              </button>
              <button className="danger" onClick={remove}>
                Usuń z biblioteki
              </button>
            </div>
          </>
        )}
      </aside>
    </div>
  )
}
