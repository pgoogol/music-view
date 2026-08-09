// Typowany klient API music-view — kontrakty 1:1 z DTO backendu (M1.7).

export interface TrackResponse {
  spotifyId: string
  title: string | null
  artist: string | null
  album: string | null
  year: number | null
  durationMs: number | null
  popularity: number | null
  explicit: boolean | null
  albumImageUrl: string | null
  isrc: string | null
  genreFamily: string | null
  style: string | null
  bpm: number | null
  bpmSource: string | null
  danceability: number | null
  musicalKey: string | null
  /** Pozycja koła Camelot liczona z `musicalKey` przez backend (D25) — nie kolumna. */
  camelot: string | null
  tempoClass: string | null
  energy: string | null
  lyricsTheme: string | null
  descriptionPl: string | null
  confidence: string | null
  enrichedAt: string | null
  modelUsed: string | null
  enrichVersion: number | null
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface LibraryEntryResponse {
  id: number
  spotifyId: string
  source: string
  addedAt: string
  djNotes: string | null
  customTags: string[] | null
  rating: number | null
  djSlotOverride: string | null
  /** Wersja do blokady optymistycznej (D29) — odsyłamy ją przy PATCH-u. */
  version: number
  track: TrackResponse
}

export interface IngestFileResponse {
  imported: number
  alreadyExisted: number
  failed: { line: number; reason: string }[]
}

/** Metryki wgrane ręcznie z CSV (D24) — surowe wartości z pliku, skala 0..1. */
export interface TrackMetricsResponse {
  spotifyId: string
  bpm: number | null
  musicalKey: string | null
  camelot: string | null
  danceability: number | null
  energy: number | null
  valence: number | null
  acousticness: number | null
  instrumentalness: number | null
  speechiness: number | null
  liveness: number | null
  loudnessDb: number | null
  timeSignature: number | null
  source: string | null
  importedAt: string | null
}

export interface RowErrorResponse {
  line: number
  reason: string
}

/** Raport jednego pliku partii; `error` niepuste = plik odpadł w całości. */
export interface MetricsFileReportResponse {
  file: string
  applied: number
  matchedByIsrc: number
  skipped: RowErrorResponse[]
  failed: RowErrorResponse[]
  errorCode: string | null
  error: string | null
}

/** Liczby na wierzchu są sumą partii; numery wierszy mają sens tylko przy pliku. */
export interface IngestMetricsResponse {
  applied: number
  matchedByIsrc: number
  skippedRows: number
  failedRows: number
  files: MetricsFileReportResponse[]
}

export interface IngestPlaylistResponse {
  playlistId: number
  spotifyPlaylistId: string
  name: string
  tracks: number
  imported: number
  alreadyExisted: number
  skipped: { position: number; reason: string }[]
}

/** Tryb C: playlista, która padła, nie przerywa importu — wraca w `failed`. */
export interface IngestMyPlaylistsResponse {
  imported: IngestPlaylistResponse[]
  failed: { spotifyPlaylistId: string; name: string; errorCode: string; reason: string }[]
}

export interface EnrichJobResponse {
  executionId: number
  jobInstanceId: number
  status: string
  scope: string
  fields: string
  readCount: number
  writeCount: number
  startTime: string | null
  endTime: string | null
  exitDescription: string | null
}

export interface PlaylistSummaryResponse {
  id: number
  name: string
  spotifyPlaylistId: string | null
  createdAt: string
  trackCount: number
  version: number
}

export interface PlaylistTrackResponse {
  position: number
  djSlot: string | null
  djSlotOverride: string | null
  /** Z metryk wgranych z pliku (D24) — tylko dla ostrzeżeń planera setu (D25). */
  loudnessDb: number | null
  timeSignature: number | null
  track: TrackResponse
}

export interface PlaylistResponse {
  id: number
  name: string
  spotifyPlaylistId: string | null
  createdAt: string
  /** Wersja agregatu (D29) — odsyłamy ją przy zmianie kolejności i nazwy. */
  version: number
  tracks: PlaylistTrackResponse[]
}

export interface PlaylistExportResponse {
  playlistId: number
  spotifyPlaylistId: string
  name: string
  exportedTracks: number
  created: boolean
  spotifyUrl: string
}

export interface SpotifyAccountResponse {
  connected: boolean
  spotifyUserId: string | null
  displayName: string | null
  scopes: string | null
  expiresAt: string | null
  connectedAt: string | null
}

/** Jeden słupek rozkładu w przeglądzie biblioteki (M4.3). */
export interface BucketResponse {
  label: string
  count: number
}

export interface LibraryOverviewResponse {
  catalogTracks: number
  libraryTracks: number
  tracksWithMetrics: number
  metadataMissing: number
  audioMissing: number
  aiMissing: number
  genres: BucketResponse[]
  tempoClasses: BucketResponse[]
  energies: BucketResponse[]
  /** Ile biblioteki stoi na faktach, a ile na estymacie LLM (kryterium D19). */
  bpmSources: BucketResponse[]
  ratings: BucketResponse[]
  bpmHistogram: BucketResponse[]
  topArtists: BucketResponse[]
  monthlyGrowth: BucketResponse[]
}

/** Propozycja setu (M4.2/D26) — generator niczego nie zapisuje. */
export interface SetProposalRequest {
  targetMinutes: number
  seed?: number
  search?: string
  genreFamily?: string
  bpmMin?: number
  bpmMax?: number
  tempoClass?: string
  energy?: string
  inLibrary?: boolean
  ratingMin?: number
  tag?: string
  camelot?: string
  camelotCompatible?: boolean
}

export interface ProposedTrackResponse {
  position: number
  djSlot: string | null
  track: TrackResponse
}

export interface SetProposalResponse {
  trackCount: number
  totalDurationMs: number
  targetDurationMs: number
  /** Ziarno użyte przy losowaniu — podaj je z powrotem, żeby dostać ten sam set. */
  seed: number
  notes: string[]
  tracks: ProposedTrackResponse[]
}

/** Pokrycie katalogu metrykami z pliku — kontekst filtrów metryk (M4.1). */
export interface MetricsCoverageResponse {
  withMetrics: number
  total: number
}

/** Szacunek zlecenia wzbogacania (M5.1/D28) — nic nie uruchamia. */
export interface EnrichmentEstimateResponse {
  trackCount: number
  /** Utwory, za które realnie zapłacimy — tylko grupa AI. */
  aiTracks: number
  /** null = brak stawek w konfiguracji, nie zero. */
  estimatedCost: number | null
  limit: number
  withinLimit: boolean
}

export interface MissingCountResponse {
  metadata: number
  audio: number
  ai: number
}

/** Biała lista sortowania po stronie API (M3.1, enum CatalogSort). */
export const CATALOG_SORTS = [
  'RELEVANCE',
  'TITLE',
  'ARTIST',
  'YEAR',
  'BPM',
  'POPULARITY',
  'DURATION',
  'ENERGY',
] as const

export type CatalogSort = (typeof CATALOG_SORTS)[number]

export type SortDirection = 'ASC' | 'DESC'

export interface SearchParams {
  search?: string
  genreFamily?: string
  bpmMin?: number
  bpmMax?: number
  tempoClass?: string
  energy?: string
  /** Filtry biblioteki DJ-a (M3.2): przynależność, ocena minimalna, custom tag. */
  inLibrary?: boolean
  ratingMin?: number
  tag?: string
  /** Filtr harmoniczny (M4.1/D25): pozycja koła + czy rozszerzyć do zgodnych. */
  camelot?: string
  camelotCompatible?: boolean
  /** Filtry metryk (D24) — odsiewają utwory bez metryk, stąd licznik pokrycia. */
  valenceMin?: number
  valenceMax?: number
  instrumentalMin?: number
  livenessMax?: number
  sort?: CatalogSort
  direction?: SortDirection
  page?: number
  size?: number
}

export interface UpdateLibraryEntryRequest {
  djNotes?: string | null
  customTags?: string[] | null
  rating?: number | null
  djSlotOverride?: string | null
  /** Wymagana (D29) — bez niej backend odrzuca PATCH. */
  version: number
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly errorCode: string,
    readonly status: number,
  ) {
    super(message)
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  if (!response.ok) {
    let errorCode = 'HTTP_' + response.status
    let message = response.statusText
    try {
      const body = await response.json()
      errorCode = body.errorCode ?? errorCode
      message = body.message ?? message
    } catch {
      /* odpowiedź bez JSON-a — zostają wartości domyślne */
    }
    throw new ApiError(message, errorCode, response.status)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

const jsonInit = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

export const api = {
  searchTracks(params: SearchParams): Promise<PageResponse<TrackResponse>> {
    const query = new URLSearchParams()
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        query.set(key, String(value))
      }
    })
    return request(`/api/catalog/tracks?${query}`)
  },

  libraryOverview(): Promise<LibraryOverviewResponse> {
    return request('/api/library/overview')
  },

  proposeSet(body: SetProposalRequest): Promise<SetProposalResponse> {
    return request('/api/sets/propose', jsonInit('POST', body))
  },

  metricsCoverage(): Promise<MetricsCoverageResponse> {
    return request('/api/catalog/metrics-coverage')
  },

  /** Słownik custom tagów DJ-a — podpowiedzi filtra bibliotecznego (M3.2). */
  listTags(): Promise<string[]> {
    return request('/api/library/tags')
  },

  getLibraryEntry(spotifyId: string): Promise<LibraryEntryResponse> {
    return request(`/api/library/tracks/${encodeURIComponent(spotifyId)}`)
  },

  updateLibraryEntry(
    spotifyId: string,
    body: UpdateLibraryEntryRequest,
  ): Promise<LibraryEntryResponse> {
    return request(`/api/library/tracks/${encodeURIComponent(spotifyId)}`, jsonInit('PATCH', body))
  },

  deleteLibraryEntry(spotifyId: string): Promise<void> {
    return request(`/api/library/tracks/${encodeURIComponent(spotifyId)}`, { method: 'DELETE' })
  },

  ingestFile(file: File): Promise<IngestFileResponse> {
    const form = new FormData()
    form.append('file', file)
    return request('/api/ingest/file', { method: 'POST', body: form })
  },

  ingestMetrics(files: File[]): Promise<IngestMetricsResponse> {
    const form = new FormData()
    files.forEach((file) => form.append('file', file))
    return request('/api/ingest/metrics', { method: 'POST', body: form })
  },

  /** 204 z backendu (utwór bez metryk) wraca jako undefined — patrz `request`. */
  getTrackMetrics(spotifyId: string): Promise<TrackMetricsResponse | undefined> {
    return request(`/api/catalog/tracks/${encodeURIComponent(spotifyId)}/metrics`)
  },

  ingestPlaylist(url: string): Promise<IngestPlaylistResponse> {
    return request('/api/ingest/playlist', jsonInit('POST', { url }))
  },

  ingestMyPlaylists(): Promise<IngestMyPlaylistsResponse> {
    return request('/api/ingest/my-playlists', { method: 'POST' })
  },

  spotifyAccount(): Promise<SpotifyAccountResponse> {
    return request('/api/auth/spotify/status')
  },

  listPlaylists(): Promise<PlaylistSummaryResponse[]> {
    return request('/api/playlists')
  },

  getPlaylist(id: number): Promise<PlaylistResponse> {
    return request(`/api/playlists/${id}`)
  },

  createPlaylist(name: string): Promise<PlaylistSummaryResponse> {
    return request('/api/playlists', jsonInit('POST', { name }))
  },

  renamePlaylist(id: number, name: string, version: number): Promise<PlaylistSummaryResponse> {
    return request(`/api/playlists/${id}`, jsonInit('PATCH', { name, version }))
  },

  deletePlaylist(id: number): Promise<void> {
    return request(`/api/playlists/${id}`, { method: 'DELETE' })
  },

  addPlaylistTrack(id: number, spotifyId: string): Promise<PlaylistResponse> {
    return request(`/api/playlists/${id}/tracks`, jsonInit('POST', { spotifyId }))
  },

  removePlaylistTrack(id: number, spotifyId: string): Promise<PlaylistResponse> {
    return request(`/api/playlists/${id}/tracks/${encodeURIComponent(spotifyId)}`, {
      method: 'DELETE',
    })
  },

  reorderPlaylist(id: number, spotifyIds: string[], version: number): Promise<PlaylistResponse> {
    return request(`/api/playlists/${id}/tracks`, jsonInit('PUT', { spotifyIds, version }))
  },

  exportPlaylist(id: number): Promise<PlaylistExportResponse> {
    return request(`/api/playlists/${id}/export-to-spotify`, { method: 'POST' })
  },

  startEnrichment(scope: string, fields: string[], spotifyIds: string[]): Promise<{ executionId: number }> {
    return request('/api/enrich', jsonInit('POST', { scope, fields, spotifyIds }))
  },

  listJobs(limit = 10): Promise<EnrichJobResponse[]> {
    return request(`/api/enrich/jobs?limit=${limit}`)
  },

  jobStatus(executionId: number): Promise<EnrichJobResponse> {
    return request(`/api/enrich/jobs/${executionId}`)
  },

  restartJob(executionId: number): Promise<{ executionId: number }> {
    return request(`/api/enrich/jobs/${executionId}/restart`, { method: 'POST' })
  },

  estimateEnrichment(
    scope: string,
    fields: string[],
    spotifyIds: string[],
  ): Promise<EnrichmentEstimateResponse> {
    return request('/api/enrich/estimate', jsonInit('POST', { scope, fields, spotifyIds }))
  },

  missingCount(): Promise<MissingCountResponse> {
    return request('/api/enrich/missing-count')
  },
}
