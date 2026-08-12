import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import SetSuggestionList from './SetSuggestionList'
import { aTrack } from '../test/fixtures'

const suggestions = [
  {
    djSlot: 'PEAK',
    bpmDelta: 6,
    harmonic: true,
    track: aTrack({ spotifyId: 'sp-zgodny', title: 'Zgodny', camelot: '8A' }),
  },
  {
    djSlot: 'PEAK',
    bpmDelta: -2,
    harmonic: false,
    track: aTrack({ spotifyId: 'sp-zderzenie', title: 'Zderzenie', camelot: '2A' }),
  },
  {
    djSlot: null,
    bpmDelta: null,
    harmonic: null,
    track: aTrack({ spotifyId: 'sp-bez-danych', title: 'Bez danych', camelot: null }),
  },
]

describe('SetSuggestionList', () => {

  it('pokazuje powody przejścia: różnicę tempa i zgodność tonacji', () => {
    render(
      <SetSuggestionList
        position={3}
        suggestions={suggestions}
        busy={false}
        onPick={vi.fn()}
        onClose={vi.fn()}
      />,
    )

    expect(screen.getByText('Kandydaci na miejsce 4')).toBeInTheDocument()
    expect(screen.getByText('+6 BPM')).toBeInTheDocument()
    expect(screen.getByText('−2 BPM')).toBeInTheDocument()
    expect(screen.getByText('8A ✓')).toBeInTheDocument()
    expect(screen.getByText('2A ✕')).toBeInTheDocument()
  })

  it('brak tonacji albo BPM pokazuje jako brak danych, nie jako zderzenie', () => {
    render(
      <SetSuggestionList
        position={0}
        suggestions={[suggestions[2]]}
        busy={false}
        onPick={vi.fn()}
        onClose={vi.fn()}
      />,
    )

    expect(screen.queryByText(/✕/)).not.toBeInTheDocument()
    expect(screen.getAllByText('—')).not.toHaveLength(0)
  })

  it('wstawienie oddaje identyfikator wybranego utworu', async () => {
    const onPick = vi.fn()
    render(
      <SetSuggestionList
        position={1}
        suggestions={suggestions}
        busy={false}
        onPick={onPick}
        onClose={vi.fn()}
      />,
    )

    await userEvent.click(screen.getByLabelText('wstaw na miejsce 2: Zgodny'))

    expect(onPick).toHaveBeenCalledWith('sp-zgodny')
  })

  it('pusta lista mówi wprost, że nic nie pasuje', () => {
    render(
      <SetSuggestionList
        position={1}
        suggestions={[]}
        busy={false}
        onPick={vi.fn()}
        onClose={vi.fn()}
      />,
    )

    expect(screen.getByTestId('suggestions-empty')).toBeInTheDocument()
  })
})
