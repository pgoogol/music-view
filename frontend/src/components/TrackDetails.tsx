import { useEffect, useState } from 'react'
import { api, ApiError, type LibraryEntryResponse } from '../api'

interface Props {
  spotifyId: string
  onClose: () => void
  onChanged: () => void
}

export default function TrackDetails({ spotifyId, onClose, onChanged }: Props) {
  const [entry, setEntry] = useState<LibraryEntryResponse | null>(null)
  const [djNotes, setDjNotes] = useState('')
  const [tags, setTags] = useState('')
  const [rating, setRating] = useState(0)
  const [slotOverride, setSlotOverride] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .getLibraryEntry(spotifyId)
      .then((loaded) => {
        setEntry(loaded)
        setDjNotes(loaded.djNotes ?? '')
        setTags((loaded.customTags ?? []).join(', '))
        setRating(loaded.rating ?? 0)
        setSlotOverride(loaded.djSlotOverride ?? '')
      })
      .catch((ex) => setError(String(ex)))
  }, [spotifyId])

  const save = async () => {
    setMessage(null)
    setError(null)
    try {
      const saved = await api.updateLibraryEntry(spotifyId, {
        djNotes,
        customTags: tags.split(',').map((tag) => tag.trim()).filter(Boolean),
        rating,
        djSlotOverride: slotOverride,
      })
      setEntry(saved)
      setMessage('Zapisano')
      onChanged()
    } catch (ex) {
      setError(ex instanceof ApiError ? `${ex.errorCode}: ${ex.message}` : String(ex))
    }
  }

  const remove = async () => {
    setError(null)
    try {
      await api.deleteLibraryEntry(spotifyId)
      onChanged()
      onClose()
    } catch (ex) {
      setError(ex instanceof ApiError ? `${ex.errorCode}: ${ex.message}` : String(ex))
    }
  }

  const track = entry?.track

  return (
    <div className="drawer-backdrop" onClick={onClose}>
      <aside className="drawer" onClick={(e) => e.stopPropagation()} data-testid="track-details">
        <button className="close" onClick={onClose} aria-label="zamknij">×</button>

        {error && <p className="error">{error}</p>}
        {track && (
          <>
            <h2>{track.title ?? spotifyId}</h2>
            <p className="muted">{track.artist} — {track.album ?? 'brak albumu'}</p>

            <dl className="track-facts">
              <dt>Rok</dt><dd>{track.year ?? '—'}</dd>
              <dt>BPM</dt><dd>{track.bpm ?? '—'} {track.bpmSource ? `(${track.bpmSource})` : ''}</dd>
              <dt>Tempo</dt><dd>{track.tempoClass ?? '—'}</dd>
              <dt>Tonacja</dt><dd>{track.musicalKey ?? '—'}</dd>
              <dt>Taneczność</dt><dd>{track.danceability ?? '—'}</dd>
              <dt>Gatunek</dt><dd>{track.genreFamily ?? '—'}</dd>
              <dt>Styl</dt><dd>{track.style ?? '—'}</dd>
              <dt>Energia</dt><dd>{track.energy ?? '—'}</dd>
              <dt>O czym</dt><dd>{track.lyricsTheme ?? '—'}</dd>
              <dt>Opis</dt><dd>{track.descriptionPl ?? '—'}</dd>
              <dt>ISRC</dt><dd>{track.isrc ?? '—'}</dd>
              <dt>Wzbogacono</dt>
              <dd>
                {track.enrichedAt
                  ? `${new Date(track.enrichedAt).toLocaleString('pl')} (${track.modelUsed ?? '?'}, v${track.enrichVersion ?? '?'})`
                  : 'jeszcze nie'}
              </dd>
            </dl>

            <h3>Dane DJ-a</h3>
            <label className="field">
              uwagi
              <textarea
                value={djNotes}
                onChange={(e) => setDjNotes(e.target.value)}
                rows={3}
                data-testid="dj-notes"
              />
            </label>
            <label className="field">
              custom tagi (po przecinku)
              <input value={tags} onChange={(e) => setTags(e.target.value)} data-testid="dj-tags" />
            </label>
            <label className="field">
              rating (0 = brak)
              <select value={rating} onChange={(e) => setRating(Number(e.target.value))}>
                {[0, 1, 2, 3, 4, 5].map((value) => (
                  <option key={value} value={value}>
                    {value === 0 ? 'brak' : '★'.repeat(value)}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              slot override
              <input
                value={slotOverride}
                onChange={(e) => setSlotOverride(e.target.value)}
                placeholder="np. peak / warmup"
              />
            </label>

            <div className="row">
              <button onClick={save} data-testid="dj-save">Zapisz</button>
              <button className="danger" onClick={remove}>Usuń z biblioteki</button>
            </div>
            {message && <p className="report" data-testid="save-message">{message}</p>}
          </>
        )}
      </aside>
    </div>
  )
}
