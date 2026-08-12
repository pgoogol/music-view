// Object Mother dla testów frontu (docs/rules/testing.md) — minimalne obiekty
// zgodne z DTO backendu, z nadpisywaniem tylko istotnych pól w teście.

import type {
  IngestPlaylistResponse,
  LibraryOverviewResponse,
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
    camelot: '8A',
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
  overrides: Partial<PlaylistTrackResponse> = {},
): PlaylistTrackResponse {

  return {
    position,
    djSlot,
    djSlotOverride: null,
    loudnessDb: null,
    timeSignature: null,
    track: aTrack(track),
    ...overrides,
  }
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
    version: 0,
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
    version: 0,
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

/**
 * Przegląd biblioteki w kształcie z M5.4 — jeden komplet danych dla testów
 * logiki wniosków i całego pulpitu. Liczby są tak dobrane, żeby dało się je
 * sprawdzić w pamięci: 2500 w katalogu, 2120 znanych temp, dominująca 8A.
 */
export const overviewFixture: LibraryOverviewResponse = {
  scale: {
    catalogTracks: 2500,
    libraryTracks: 2310,
    tracksWithMetrics: 120,
    libraryDurationMs: 22_000_000,
    distinctArtists: 640,
    averageBpm: 118.4,
    playlists: 7,
    tracksInPlaylists: 310,
    tracksOutsidePlaylists: 2000,
  },
  quality: {
    metadataMissing: 4,
    audioMissing: 380,
    aiMissing: 90,
    bpmSources: [
      { label: 'MANUAL', count: 120 },
      { label: 'DEEZER', count: 900 },
      { label: 'LLM', count: 1100 },
      { label: 'BRAK BPM', count: 380 },
    ],
    confidences: [
      { label: 'high', count: 1500 },
      { label: 'BEZ ANALIZY', count: 90 },
    ],
  },
  sound: {
    genres: [
      { label: 'LATIN', count: 1500 },
      { label: 'BEZ GATUNKU', count: 100 },
    ],
    styles: [{ label: 'salsa', count: 700 }],
    tempoClasses: [{ label: 'MEDIUM', count: 900 }],
    energies: [{ label: 'high', count: 1200 }],
    bpmHistogram: [
      { label: '90–99', count: 200 },
      { label: '100–109', count: 400 },
      { label: '110–119', count: 150 },
    ],
    camelotKeys: [
      { label: '8A', count: 300 },
      { label: '9A', count: 120 },
      { label: 'BEZ TONACJI', count: 380 },
    ],
    durations: [
      { label: '3 min', count: 800 },
      { label: '4 min', count: 900 },
      { label: '5 min', count: 300 },
    ],
    popularity: [
      { label: '40–49', count: 500 },
      { label: '50–59', count: 700 },
      { label: '60–69', count: 400 },
    ],
    tempoEnergy: [
      { tempoClass: 'MEDIUM', energy: 'HIGH', count: 700 },
      { tempoClass: 'FAST', energy: 'HIGH', count: 220 },
    ],
    audioProfile: [
      { label: 'danceability', value: 0.78 },
      { label: 'energy', value: 0.64 },
      { label: 'valence', value: 0.71 },
      { label: 'acousticness', value: 0.18 },
      { label: 'instrumentalness', value: 0.04 },
      { label: 'speechiness', value: 0.09 },
      { label: 'liveness', value: 0.16 },
    ],
  },
  timeline: {
    monthlyGrowth: [
      { label: '2026-06', count: 100 },
      { label: '2026-07', count: 300 },
    ],
    decades: [
      { label: '1990s', count: 400 },
      { label: '2000s', count: 1200 },
    ],
  },
  taste: {
    topArtists: [
      { label: 'Marc Anthony', count: 42 },
      { label: 'Romeo Santos', count: 31 },
    ],
    topTags: [
      { label: 'parkiet', count: 60 },
      { label: 'wolne', count: 12 },
    ],
    ratings: [
      { label: '5', count: 210 },
      { label: 'bez oceny', count: 2000 },
    ],
    sources: [
      { label: 'FILE', count: 2000 },
      { label: 'PLAYLIST', count: 310 },
    ],
  },
  recentlyAdded: [
    {
      spotifyId: 'sp-1',
      title: 'Vivir Mi Vida',
      artist: 'Marc Anthony',
      albumImageUrl: null,
      addedAt: '2026-07-30T18:00:00Z',
    },
  ],
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
