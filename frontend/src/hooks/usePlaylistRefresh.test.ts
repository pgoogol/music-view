import { describe, expect, it } from 'vitest'
import { refreshLabel } from './usePlaylistRefresh'
import type { PlaylistRefreshStatusResponse } from '../api'

function status(
  overrides: Partial<PlaylistRefreshStatusResponse>,
): PlaylistRefreshStatusResponse {

  return {
    outcome: 'REFRESHED',
    lastRunAt: '2026-08-11T20:15:00Z',
    refreshedPlaylists: 12,
    failedPlaylists: 0,
    message: null,
    intervalSeconds: 300,
    ...overrides,
  }
}

const at = (value: string) => `o ${value.slice(11, 16)}`

describe('refreshLabel', () => {

  it('po udanym przebiegu podaje godzinę i liczbę playlist', () => {

    expect(refreshLabel(status({}), at)).toBe('Odświeżono automatycznie o 20:15 — playlist: 12')
  })

  it('nieudane playlisty pokazuje osobno — przebieg leci mimo nich (D31)', () => {

    expect(refreshLabel(status({ failedPlaylists: 2 }), at)).toContain('nieudane: 2')
  })

  it('brak połączonego konta opisuje jako oczekiwanie, nie awarię', () => {

    const label = refreshLabel(status({ outcome: 'SKIPPED_NOT_CONNECTED' }), at)

    expect(label).toContain('czeka na połączenie')
    expect(label).not.toContain('nie powiodło')
  })

  it('awaria niesie powód, żeby dało się ocenić świeżość danych', () => {

    const label = refreshLabel(
      status({ outcome: 'FAILED', message: 'Spotify nie odpowiada' }),
      at,
    )

    expect(label).toContain('Spotify nie odpowiada')
  })

  it('wyłączone odświeżanie i brak pierwszego przebiegu mają własne komunikaty', () => {

    expect(refreshLabel(status({ outcome: 'DISABLED' }), at)).toContain('wyłączone')
    expect(refreshLabel(status({ outcome: 'NEVER_RUN' }), at)).toContain('jeszcze nie poszło')
  })

  it('bez odpowiedzi z backendu nie pokazuje niczego', () => {

    expect(refreshLabel(null, at)).toBe('')
  })
})
