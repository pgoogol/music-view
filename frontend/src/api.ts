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
  track: TrackResponse
}

export interface IngestFileResponse {
  imported: number
  alreadyExisted: number
  failed: { line: number; reason: string }[]
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

export interface MissingCountResponse {
  metadata: number
  audio: number
  ai: number
}

export interface SearchParams {
  search?: string
  genreFamily?: string
  bpmMin?: number
  bpmMax?: number
  tempoClass?: string
  energy?: string
  page?: number
  size?: number
}

export interface UpdateLibraryEntryRequest {
  djNotes?: string | null
  customTags?: string[] | null
  rating?: number | null
  djSlotOverride?: string | null
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

  ingestPlaylist(url: string): Promise<IngestPlaylistResponse> {
    return request('/api/ingest/playlist', jsonInit('POST', { url }))
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

  missingCount(): Promise<MissingCountResponse> {
    return request('/api/enrich/missing-count')
  },
}
