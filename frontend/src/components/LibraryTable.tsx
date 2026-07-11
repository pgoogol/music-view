import { useEffect, useMemo, useState } from 'react'
import { api, type PageResponse, type SearchParams, type TrackResponse } from '../api'

const GENRES = ['LATIN', 'ROCK', 'POP', 'DISCO', 'DISCO_POLO', 'ELECTRONIC', 'HIP_HOP', 'OTHER']
const TEMPO_CLASSES = ['SLOW', 'MEDIUM', 'FAST', 'VERY_FAST']
const ENERGIES = ['low', 'medium', 'high']
const PAGE_SIZE = 20

type SortKey = 'title' | 'artist' | 'year' | 'bpm' | 'genreFamily' | 'energy'

interface Props {
  refreshKey: number
  selectedIds: ReadonlySet<string>
  onSelectionChange: (ids: ReadonlySet<string>) => void
  onOpenDetails: (spotifyId: string) => void
}

export default function LibraryTable({
  refreshKey,
  selectedIds,
  onSelectionChange,
  onOpenDetails,
}: Props) {
  const [search, setSearch] = useState('')
  const [genreFamily, setGenreFamily] = useState('')
  const [bpmMin, setBpmMin] = useState('')
  const [bpmMax, setBpmMax] = useState('')
  const [tempoClass, setTempoClass] = useState('')
  const [energy, setEnergy] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<PageResponse<TrackResponse> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [sort, setSort] = useState<{ key: SortKey; asc: boolean } | null>(null)

  useEffect(() => {
    const params: SearchParams = {
      search: search || undefined,
      genreFamily: genreFamily || undefined,
      bpmMin: bpmMin ? Number(bpmMin) : undefined,
      bpmMax: bpmMax ? Number(bpmMax) : undefined,
      tempoClass: tempoClass || undefined,
      energy: energy || undefined,
      page,
      size: PAGE_SIZE,
    }
    // debounce wyszukiwarki; filtry i paginacja łapią się na ten sam timer
    const timer = window.setTimeout(() => {
      api.searchTracks(params).then(setResult).catch((ex) => setError(String(ex)))
    }, 250)
    return () => window.clearTimeout(timer)
  }, [search, genreFamily, bpmMin, bpmMax, tempoClass, energy, page, refreshKey])

  const rows = useMemo(() => {
    const content = result?.content ?? []
    if (!sort) return content
    const direction = sort.asc ? 1 : -1
    return [...content].sort((a, b) => {
      const left = a[sort.key]
      const right = b[sort.key]
      if (left === null || left === undefined) return 1
      if (right === null || right === undefined) return -1
      if (typeof left === 'number' && typeof right === 'number') {
        return (left - right) * direction
      }
      return String(left).localeCompare(String(right), 'pl') * direction
    })
  }, [result, sort])

  const toggleSort = (key: SortKey) =>
    setSort((current) =>
      current?.key === key ? { key, asc: !current.asc } : { key, asc: true },
    )

  const toggleRow = (spotifyId: string) => {
    const next = new Set(selectedIds)
    if (next.has(spotifyId)) next.delete(spotifyId)
    else next.add(spotifyId)
    onSelectionChange(next)
  }

  const togglePage = () => {
    const pageIds = rows.map((row) => row.spotifyId)
    const allSelected = pageIds.every((id) => selectedIds.has(id))
    const next = new Set(selectedIds)
    pageIds.forEach((id) => (allSelected ? next.delete(id) : next.add(id)))
    onSelectionChange(next)
  }

  const sortMark = (key: SortKey) => (sort?.key === key ? (sort.asc ? ' ▲' : ' ▼') : '')

  return (
    <section className="panel table-panel" aria-label="Biblioteka">
      <div className="filters">
        <input
          type="search"
          placeholder="Szukaj: tytuł / wykonawca…"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value)
            setPage(0)
          }}
          data-testid="search-input"
        />
        <select value={genreFamily} onChange={(e) => { setGenreFamily(e.target.value); setPage(0) }}>
          <option value="">gatunek: wszystkie</option>
          {GENRES.map((g) => <option key={g} value={g}>{g}</option>)}
        </select>
        <input type="number" placeholder="BPM od" value={bpmMin}
               onChange={(e) => { setBpmMin(e.target.value); setPage(0) }} />
        <input type="number" placeholder="BPM do" value={bpmMax}
               onChange={(e) => { setBpmMax(e.target.value); setPage(0) }} />
        <select value={tempoClass} onChange={(e) => { setTempoClass(e.target.value); setPage(0) }}>
          <option value="">tempo: wszystkie</option>
          {TEMPO_CLASSES.map((t) => <option key={t} value={t}>{t}</option>)}
        </select>
        <select value={energy} onChange={(e) => { setEnergy(e.target.value); setPage(0) }}>
          <option value="">energia: wszystkie</option>
          {ENERGIES.map((level) => <option key={level} value={level}>{level}</option>)}
        </select>
      </div>

      {error && <p className="error">{error}</p>}

      <table data-testid="library-table">
        <thead>
          <tr>
            <th><input type="checkbox" onChange={togglePage} aria-label="zaznacz stronę" /></th>
            <th onClick={() => toggleSort('title')}>Tytuł{sortMark('title')}</th>
            <th onClick={() => toggleSort('artist')}>Wykonawca{sortMark('artist')}</th>
            <th onClick={() => toggleSort('year')}>Rok{sortMark('year')}</th>
            <th onClick={() => toggleSort('bpm')}>BPM{sortMark('bpm')}</th>
            <th>Tempo</th>
            <th onClick={() => toggleSort('genreFamily')}>Gatunek{sortMark('genreFamily')}</th>
            <th>Styl</th>
            <th onClick={() => toggleSort('energy')}>Energia{sortMark('energy')}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((track) => (
            <tr key={track.spotifyId}>
              <td>
                <input
                  type="checkbox"
                  checked={selectedIds.has(track.spotifyId)}
                  onChange={() => toggleRow(track.spotifyId)}
                  aria-label={`zaznacz ${track.title ?? track.spotifyId}`}
                />
              </td>
              <td>
                <button className="link" onClick={() => onOpenDetails(track.spotifyId)}>
                  {track.title ?? '—'}
                </button>
              </td>
              <td>{track.artist ?? '—'}</td>
              <td>{track.year ?? '—'}</td>
              <td>{track.bpm ?? '—'}</td>
              <td>{track.tempoClass ?? '—'}</td>
              <td>{track.genreFamily ?? '—'}</td>
              <td>{track.style ?? '—'}</td>
              <td>{track.energy ?? '—'}</td>
            </tr>
          ))}
          {rows.length === 0 && (
            <tr>
              <td colSpan={9} className="muted">
                Brak utworów — zaimportuj CSV powyżej.
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {result && result.totalPages > 1 && (
        <div className="pager">
          <button disabled={page === 0} onClick={() => setPage(page - 1)}>‹ poprzednia</button>
          <span>
            strona {result.page + 1} / {result.totalPages} ({result.totalElements} utworów)
          </span>
          <button disabled={page + 1 >= result.totalPages} onClick={() => setPage(page + 1)}>
            następna ›
          </button>
        </div>
      )}
    </section>
  )
}
