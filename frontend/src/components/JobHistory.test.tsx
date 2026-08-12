import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import JobHistory from './JobHistory'
import type { EnrichJobResponse } from '../api'
import { jsonResponse, renderWithToasts } from '../test/renderWithToasts'

function aJob(overrides: Partial<EnrichJobResponse> = {}): EnrichJobResponse {
  return {
    executionId: 7,
    jobInstanceId: 3,
    status: 'COMPLETED',
    scope: 'MISSING',
    fields: 'AUDIO',
    readCount: 2500,
    writeCount: 2463,
    failedCount: 37,
    startTime: '2026-08-12T10:00:00',
    endTime: '2026-08-12T10:40:00',
    exitDescription: '',
    ...overrides,
  }
}

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn().mockResolvedValue(jsonResponse([
    { spotifyId: 'sp-1', reason: 'DeezerException: 429 Too Many Requests', failedAt: '2026-08-12T10:05:00Z' },
    { spotifyId: 'sp-2', reason: 'MusicBrainzException: brak ISRC', failedAt: '2026-08-12T10:06:00Z' },
  ]))
  globalThis.fetch = fetchMock as unknown as typeof fetch
})

describe('JobHistory', () => {

  it('pokazuje liczbę pominiętych utworów obok przetworzonych (D37)', () => {

    renderWithToasts(<JobHistory jobs={[aJob()]} onRestart={vi.fn()} busy={false} />)

    expect(screen.getByTestId('job-failed-7')).toHaveTextContent('37')
    expect(screen.getByText('2463 / 2500')).toBeInTheDocument()
  })

  it('„szczegóły" otwiera okno z powodami, a nie dymek systemowy', async () => {

    renderWithToasts(<JobHistory jobs={[aJob()]} onRestart={vi.fn()} busy={false} />)

    await userEvent.click(screen.getByTestId('job-details-7'))

    const failures = await screen.findByTestId('job-failures')

    expect(within(failures).getByText('sp-1')).toBeInTheDocument()
    expect(within(failures).getByText(/429 Too Many Requests/)).toBeInTheDocument()
    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/enrich/jobs/7/failures')
  })

  it('mówi, ile powodów pokazano, gdy lista jest przycięta', async () => {

    renderWithToasts(
      <JobHistory jobs={[aJob({ failedCount: 500 })]} onRestart={vi.fn()} busy={false} />,
    )

    await userEvent.click(screen.getByTestId('job-details-7'))

    expect(await screen.findByText(/Pokazano 2 z 500/)).toBeInTheDocument()
  })

  it('job bez pominięć i bez komunikatu nie kusi pustymi szczegółami', () => {

    renderWithToasts(
      <JobHistory
        jobs={[aJob({ failedCount: 0, exitDescription: '' })]}
        onRestart={vi.fn()}
        busy={false}
      />,
    )

    expect(screen.queryByTestId('job-details-7')).not.toBeInTheDocument()
  })

  it('nieudany job pokazuje komunikat zakończenia w oknie, nie w atrybucie title', async () => {

    renderWithToasts(
      <JobHistory
        jobs={[aJob({ status: 'FAILED', failedCount: 0, exitDescription: 'java.lang.IllegalStateException: brak klucza' })]}
        onRestart={vi.fn()}
        busy={false}
      />,
    )

    await userEvent.click(screen.getByTestId('job-details-7'))

    await waitFor(() =>
      expect(screen.getByTestId('job-exit-description')).toHaveTextContent('brak klucza'))
    // bez pominiętych utworów nie ma po co pytać API o listę powodów
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
