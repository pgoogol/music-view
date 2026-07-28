// Object Mother dla testów frontu (docs/rules/testing.md) — minimalne obiekty
// zgodne z DTO backendu, z nadpisywaniem tylko istotnych pól w teście.

import type {
  IngestPlaylistResponse,
  PageResponse,
  PlaylistResponse,
  PlaylistSummaryResponse,
  PlaylistTrackResponse,
  TrackResponse,
} from '../api'

export function aTrack(overrides: Partial<TrackResponse> = {}): TrackResponse {

  return {
    spotifyId: 'sp-1',
    title: 'Vivir Mi Vida',
    artist: 'Marc Anthony',
    album: '3.0',
    year: 2013,
    durationMs: 252306,
    popularity: 80,
    explicit: false,
    albumImageUrl: null,
    isrc: 'USSD11300483',
    genreFamily: 'LATIN',
    style: 'salsa',
    bpm: 92,
    bpmSource: 'ACOUSTICBRAINZ',
    danceability: 0.85,
    musicalKey: 'A minor',
    tempoClass: 'MEDIUM',
    energy: 'high',
    lyricsTheme: 'afirmacja życia',
    descriptionPl: 'Energetyczna salsa.',
    confidence: 'high',
    enrichedAt: '2026-07-03T12:00:00Z',
    modelUsed: 'test-model-1',
    enrichVersion: 1,
    ...overrides,
  }
}

export function aPlaylistTrack(
  track: Partial<TrackResponse>,
  djSlot: string | null,
  position = 1,
): PlaylistTrackResponse {

  return { position, djSlot, djSlotOverride: null, track: aTrack(track) }
}

export function aPlaylistSummary(
  overrides: Partial<PlaylistSummaryResponse> = {},
): PlaylistSummaryResponse {

  return {
    id: 1,
    name: 'Sabor Latino — piątek',
    spotifyPlaylistId: null,
    createdAt: '2026-07-01T18:00:00Z',
    trackCount: 2,
    ...overrides,
  }
}

export function aPlaylist(
  tracks: PlaylistTrackResponse[],
  overrides: Partial<PlaylistResponse> = {},
): PlaylistResponse {

  return {
    id: 1,
    name: 'Sabor Latino — piątek',
    spotifyPlaylistId: null,
    createdAt: '2026-07-01T18:00:00Z',
    tracks,
    ...overrides,
  }
}

export function anIngestReport(
  overrides: Partial<IngestPlaylistResponse> = {},
): IngestPlaylistResponse {

  return {
    playlistId: 1,
    spotifyPlaylistId: 'sp-playlist-1',
    name: 'Wesela 2026',
    tracks: 30,
    imported: 12,
    alreadyExisted: 18,
    skipped: [],
    ...overrides,
  }
}

export function aPage<T>(content: T[], overrides: Partial<PageResponse<T>> = {}): PageResponse<T> {

  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    ...overrides,
  }
}
