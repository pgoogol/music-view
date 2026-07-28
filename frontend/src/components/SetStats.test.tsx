import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import SetStats from './SetStats'
import { aPlaylistTrack } from '../test/fixtures'

describe('SetStats', () => {

  it('pokazuje kafle z długością setu i zakresem tempa', () => {

    render(
      <SetStats
        tracks={[
          aPlaylistTrack({ spotifyId: 'a', durationMs: 240_000, bpm: 95 }, 'WARMUP'),
          aPlaylistTrack({ spotifyId: 'b', durationMs: 240_000, bpm: 105 }, 'MIDDLE'),
        ]}
      />,
    )

    expect(screen.getByText('8 min')).toBeInTheDocument()
    expect(screen.getByText('95–105')).toBeInTheDocument()
    expect(screen.getByText('100')).toBeInTheDocument()
  })

  it('wypisuje ostrzeżenia o skokach tempa i utworach bez BPM', () => {

    render(
      <SetStats
        tracks={[
          aPlaylistTrack({ spotifyId: 'a', bpm: 95 }, 'WARMUP'),
          aPlaylistTrack({ spotifyId: 'b', bpm: 140 }, 'PEAK'),
          aPlaylistTrack({ spotifyId: 'c', title: 'Bez tempa', bpm: null }, null),
        ]}
      />,
    )

    const warnings = screen.getByTestId('set-warnings')
    expect(within(warnings).getByText(/skok tempa 95 → 140/)).toBeInTheDocument()
    expect(within(warnings).getByText(/Bez tempa/)).toBeInTheDocument()
  })

  it('legenda faz wieczoru opisuje kolory słowami', () => {

    render(
      <SetStats
        tracks={[
          aPlaylistTrack({ spotifyId: 'a', bpm: 95 }, 'WARMUP'),
          aPlaylistTrack({ spotifyId: 'b', bpm: 100 }, null),
        ]}
      />,
    )

    expect(screen.getByText(/rozgrzewka/)).toBeInTheDocument()
    expect(screen.getByText(/bez slotu/)).toBeInTheDocument()
  })

  it('dla pustego setu nie rysuje statystyk', () => {

    const { container } = render(<SetStats tracks={[]} />)

    expect(container).toBeEmptyDOMElement()
  })
})
