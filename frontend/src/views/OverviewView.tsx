// Przegląd biblioteki (M4.3/D27) — ekran odpowiadający na pytanie „co ja
// właściwie mam". Wszystkie agregaty liczy baza jednym wywołaniem; front tylko
// rysuje. Wykresy inline w SVG, bez biblioteki wykresów (D23).

import { useEffect, useState } from 'react'
import { api, type LibraryOverviewResponse } from '../api'
import ColumnChart from '../components/ColumnChart'
import Distribution from '../components/Distribution'
import { useToast } from '../components/Toasts'

interface Props {
  refreshKey: number
}

export default function OverviewView({ refreshKey }: Props) {

  const { reportError } = useToast()
  const [overview, setOverview] = useState<LibraryOverviewResponse | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let current = true
    setLoading(true)
    api
      .libraryOverview()
      .then((loaded) => {
        if (current) setOverview(loaded)
      })
      .catch((error) => {
        if (current) reportError(error, 'Nie udało się pobrać przeglądu biblioteki')
      })
      .finally(() => {
        if (current) setLoading(false)
      })
    return () => {
      current = false
    }
  }, [refreshKey, reportError])

  if (loading && !overview) {
    return (
      <section className="panel" aria-label="Przegląd biblioteki">
        <p className="muted">Ładowanie…</p>
      </section>
    )
  }

  if (!overview) {
    return (
      <section className="panel" aria-label="Przegląd biblioteki">
        <p className="error">Przegląd jest chwilowo niedostępny.</p>
      </section>
    )
  }

  const enriched = overview.catalogTracks - overview.aiMissing
  // „z tekstem" znaczy tu „rozstrzygnięty" (D32): przetłumaczony albo z potwierdzoną
  // odpowiedzią LRCLIB, że tekstu nie ma — jedno i drugie zdejmuje utwór z kolejki
  const withLyrics = overview.catalogTracks - overview.lyricsMissing

  return (
    <div className="overview-layout" data-testid="overview">
      <section className="panel" aria-label="Biblioteka w liczbach">
        <h2>Biblioteka w liczbach</h2>
        <dl className="stat-grid" data-testid="overview-headline">
          <div>
            <dt>W katalogu</dt>
            <dd>{overview.catalogTracks}</dd>
          </div>
          <div>
            <dt>W bibliotece</dt>
            <dd>{overview.libraryTracks}</dd>
          </div>
          <div>
            <dt>Z metrykami</dt>
            <dd>{overview.tracksWithMetrics}</dd>
          </div>
          <div>
            <dt>Opisane przez AI</dt>
            <dd>{enriched}</dd>
          </div>
          <div>
            <dt>Z tekstem</dt>
            <dd>{withLyrics}</dd>
          </div>
        </dl>
        <p className="muted">
          Do uzupełnienia: metadane {overview.metadataMissing}, cechy audio{' '}
          {overview.audioMissing}, analiza AI {overview.aiMissing}, teksty{' '}
          {overview.lyricsMissing} — zlecisz to w zakładce Wzbogacanie.
        </p>
      </section>

      <section className="panel" aria-label="Skąd znamy BPM">
        <h2>Skąd znamy BPM</h2>
        <Distribution
          title="Źródło tempa"
          buckets={overview.bpmSources}
          caption="Ile biblioteki stoi na zmierzonym fakcie, a ile na estymacie modelu (D6/D19)."
          testId="bpm-sources"
        />
      </section>

      <section className="panel" aria-label="Rozkłady katalogu">
        <h2>Co gram</h2>
        <Distribution title="Gatunki" buckets={overview.genres} testId="genres" />
        <Distribution title="Tempo" buckets={overview.tempoClasses} />
        <Distribution title="Energia" buckets={overview.energies} />
      </section>

      <section className="panel" aria-label="Tempo biblioteki">
        <h2>Tempo biblioteki</h2>
        <ColumnChart
          title="Histogram BPM"
          buckets={overview.bpmHistogram}
          caption="Koszyki po 10 BPM; utwory bez tempa nie wchodzą do wykresu."
          testId="bpm-histogram"
        />
      </section>

      <section className="panel" aria-label="Biblioteka w czasie">
        <h2>Biblioteka w czasie</h2>
        <ColumnChart
          title="Przyrost po miesiącach"
          buckets={overview.monthlyGrowth}
          caption="Ostatnie 12 miesięcy, licząc po dacie dodania do biblioteki."
          testId="monthly-growth"
        />
        <Distribution title="Oceny" buckets={overview.ratings} />
      </section>

      <section className="panel" aria-label="Najczęstsi wykonawcy">
        <h2>Najczęstsi wykonawcy</h2>
        <Distribution title="Top 10" buckets={overview.topArtists} testId="top-artists" />
      </section>
    </div>
  )
}
