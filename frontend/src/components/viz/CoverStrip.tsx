// Taśma ostatnio dodanych (M5.4) — jedyne miejsce na przeglądzie, gdzie widać
// muzykę, a nie liczby o muzyce. Okładka jest linkiem do Spotify; utwór bez
// okładki dostaje ramkę z inicjałem, żeby taśma nie miała dziur.

import type { RecentTrackResponse } from '../../api'
import { DASH, formatDateTime, spotifyTrackUrl } from '../../format'

interface Props {
  tracks: readonly RecentTrackResponse[]
  testId?: string
}

export default function CoverStrip({ tracks, testId }: Props) {

  if (tracks.length === 0) {
    return <p className="muted" data-testid={testId}>Biblioteka jest jeszcze pusta.</p>
  }

  return (
    <ul className="cover-strip" data-testid={testId}>
      {tracks.map((track, index) => (
        <li key={track.spotifyId} style={{ animationDelay: `${index * 45}ms` }}>
          <a
            href={spotifyTrackUrl(track.spotifyId)}
            target="_blank"
            rel="noreferrer"
            title={`${track.title ?? DASH} — ${track.artist ?? DASH}, dodany ${formatDateTime(track.addedAt)}`}
          >
            {track.albumImageUrl ? (
              <img className="cover cover-tile" src={track.albumImageUrl} alt="" loading="lazy" />
            ) : (
              <span className="cover cover-tile cover-empty" aria-hidden="true">
                {(track.title ?? '?').slice(0, 1).toUpperCase()}
              </span>
            )}
            <span className="cover-title">{track.title ?? DASH}</span>
            <span className="cover-artist muted">{track.artist ?? DASH}</span>
          </a>
        </li>
      ))}
    </ul>
  )
}
