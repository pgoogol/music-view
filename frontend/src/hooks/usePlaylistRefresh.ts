// Stan automatycznego odświeżania playlist (M4.7/D35). Robotę robi backend,
// front tylko pyta, kiedy poszedł ostatni przebieg — bez tego otwarta karta
// pokazywałaby dane sprzed odświeżenia, dopóki DJ sam by jej nie przeładował.

import { useEffect, useRef, useState } from 'react'
import { api, type PlaylistRefreshStatusResponse } from '../api'

/**
 * Odpytujemy rzadziej, niż backend odświeża: to odczyt z pamięci, ale sam
 * przebieg trwa (kilkadziesiąt playlist to kilkadziesiąt wywołań Spotify),
 * więc częstsze pytanie i tak nie pokazałoby nic nowego.
 */
export const REFRESH_POLL_MS = 60_000

export interface PlaylistRefresh {
  status: PlaylistRefreshStatusResponse | null
  /**
   * Rośnie po każdym <b>zaobserwowanej zmianie</b> znacznika ostatniego
   * przebiegu — widok trzyma to w zależnościach efektu i przeładowuje dane.
   * Pierwsza odpowiedź tylko zapamiętuje stan i licznika nie rusza: inaczej
   * samo wejście na zakładkę pobierałoby listę dwa razy.
   */
  completedRuns: number
}

export function usePlaylistRefresh(): PlaylistRefresh {

  const [status, setStatus] = useState<PlaylistRefreshStatusResponse | null>(null)
  const [completedRuns, setCompletedRuns] = useState(0)
  const seen = useRef<{ any: boolean; lastRunAt: string | null }>({ any: false, lastRunAt: null })

  useEffect(() => {
    let active = true
    const load = () =>
      api
        .playlistRefreshStatus()
        .then((loaded) => {
          if (!active) return
          setStatus(loaded)
          if (seen.current.any && seen.current.lastRunAt !== loaded.lastRunAt) {
            setCompletedRuns((runs) => runs + 1)
          }
          seen.current = { any: true, lastRunAt: loaded.lastRunAt }
        })
        // odpytywanie w tle milczy przy błędzie: toast co minutę przy zgaszonym
        // backendzie byłby gorszy od braku informacji o odświeżaniu
        .catch(() => undefined)

    load()
    const timer = window.setInterval(load, REFRESH_POLL_MS)
    return () => {
      active = false
      window.clearInterval(timer)
    }
  }, [])

  return { status, completedRuns }
}

/** Jednozdaniowy opis stanu — każdy przypadek mówi, czy dane są świeże. */
export function refreshLabel(
  status: PlaylistRefreshStatusResponse | null,
  formatTime: (value: string) => string,
): string {

  if (!status) return ''
  switch (status.outcome) {
    case 'DISABLED':
      return 'Automatyczne odświeżanie playlist jest wyłączone'
    case 'NEVER_RUN':
      return 'Pierwsze automatyczne odświeżenie jeszcze nie poszło'
    case 'SKIPPED_NOT_CONNECTED':
      return 'Automatyczne odświeżanie czeka na połączenie konta Spotify'
    case 'FAILED':
      return `Ostatnie odświeżanie nie powiodło się: ${status.message ?? 'nieznany powód'}`
    case 'REFRESHED': {
      const when = status.lastRunAt ? formatTime(status.lastRunAt) : ''
      const failed =
        status.failedPlaylists > 0 ? `, nieudane: ${status.failedPlaylists}` : ''
      return `Odświeżono automatycznie ${when} — playlist: ${status.refreshedPlaylists}${failed}`
    }
  }
}
