// Przegląd biblioteki (M4.3, przebudowany na pulpit w M5.4/D32) — ekran
// odpowiadający na pytanie „co ja właściwie mam" i „czy da się z tego zagrać".
//
// Czyta się go w pięciu strefach, w kolejności malejącej ogólności: skala →
// wnioski → brzmienie → kompletność danych → czas i gust. Wszystkie agregaty
// liczy baza jednym wywołaniem (D27); front tylko rysuje, inline w SVG,
// bez biblioteki wykresów i bez zasobów z sieci (D23).

import { useEffect, useState, type ReactNode } from 'react'
import { api, type LibraryOverviewResponse } from '../api'
import ColumnChart from '../components/ColumnChart'
import { useToast } from '../components/Toasts'
import AreaChart from '../components/viz/AreaChart'
import CamelotWheel from '../components/viz/CamelotWheel'
import CoverStrip from '../components/viz/CoverStrip'
import Gauge from '../components/viz/Gauge'
import HeatMatrix from '../components/viz/HeatMatrix'
import RadarChart from '../components/viz/RadarChart'
import RankedBars from '../components/viz/RankedBars'
import StackedBar from '../components/viz/StackedBar'
import StatTile from '../components/viz/StatTile'
import TagCloud from '../components/viz/TagCloud'
import { SOURCE_LABELS } from '../format'
import {
  coverageParts,
  cumulativeGrowth,
  insights,
  percentLabel,
  readinessScore,
  sumBuckets,
} from '../overviewInsights'

interface Props {
  refreshKey: number
}

/** Kaskada BPM z D6/D24 opisana słowami — „DEEZER" nic nie mówi o wiarygodności. */
const BPM_SOURCE_LABELS: Record<string, string> = {
  MANUAL: 'pomiar z pliku',
  ACOUSTICBRAINZ: 'AcousticBrainz',
  DEEZER: 'Deezer',
  LLM: 'estymata modelu',
  'BRAK BPM': 'bez tempa',
}

const CONFIDENCE_LABELS: Record<string, string> = {
  high: 'pewność wysoka',
  medium: 'pewność średnia',
  low: 'pewność niska',
  HIGH: 'pewność wysoka',
  MEDIUM: 'pewność średnia',
  LOW: 'pewność niska',
  'BEZ ANALIZY': 'bez analizy AI',
}

const LIBRARY_SOURCE_LABELS: Record<string, string> = { ...SOURCE_LABELS, nieznane: 'nieznane' }

const RATING_LABELS: Record<string, string> = {
  '1': '★', '2': '★★', '3': '★★★', '4': '★★★★', '5': '★★★★★',
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
    return <DashboardSkeleton />
  }

  if (!overview) {
    return (
      <section className="panel" aria-label="Przegląd biblioteki">
        <p className="error">Przegląd jest chwilowo niedostępny.</p>
      </section>
    )
  }

  const { scale, quality, sound, timeline, taste } = overview
  const hours = scale.libraryDurationMs / 3_600_000
  const coverage = coverageParts(overview)
  const ratedTracks = sumBuckets(taste.ratings.filter((bucket) => bucket.label !== 'bez oceny'))

  return (
    <div className="dash" data-testid="overview">

      <DashPanel title="Biblioteka w liczbach" span={6} index={0}>
        <div className="dash-hero">
          <div className="stat-tiles" data-testid="overview-headline">
            <StatTile label="w katalogu" value={scale.catalogTracks} tone="measure"
                      hint="utwory, o których cokolwiek wiemy" />
            <StatTile label="w bibliotece" value={scale.libraryTracks} tone="accent"
                      hint="to, co realnie masz u siebie" />
            <StatTile label="czas grania" value={hours} unit=" h" tone="accent"
                      format={(value) => value.toFixed(1).replace('.', ',')}
                      hint="gdyby puścić bibliotekę raz, bez przerw" />
            <StatTile label="wykonawców" value={scale.distinctArtists} tone="measure"
                      hint="różnych nazwisk w bibliotece" />
            <StatTile label="średnie tempo" value={scale.averageBpm ?? 0} unit=" BPM"
                      tone="measure" format={(value) => String(Math.round(value))}
                      hint={scale.averageBpm === null ? 'brak utworów z tempem' : 'średnia po całym katalogu'} />
            <StatTile label="playlist" value={scale.playlists} tone="measure"
                      hint={`${scale.tracksInPlaylists} utworów wpiętych w sety`} />
            <StatTile label="poza setami" value={scale.tracksOutsidePlaylists} tone="warn"
                      hint="nigdy nie trafiły do żadnej playlisty" />
            <StatTile label="z metrykami" value={scale.tracksWithMetrics} tone="measure"
                      hint="utwory z pełnymi cechami audio z pliku (D24)" />
          </div>
          <Gauge
            ratio={readinessScore(overview)}
            label="gotowość danych"
            caption="średnia z czterech pokryć"
            testId="readiness"
          />
        </div>
      </DashPanel>

      <DashPanel title="Co z tego wynika" span={6} index={1}>
        <ul className="insight-list" data-testid="insights">
          {insights(overview).map((insight) => (
            <li key={insight.id}>
              <span className="insight-label">{insight.label}</span>
              <span className="insight-value">{insight.value}</span>
              <span className="insight-hint muted">{insight.hint}</span>
            </li>
          ))}
        </ul>
      </DashPanel>

      <DashPanel title="Puls tempa" span={4} index={2}>
        <AreaChart
          points={sound.bpmHistogram}
          title="Histogram BPM"
          caption="Koszyki po 10 BPM; utwory bez tempa nie wchodzą do wykresu."
          testId="bpm-histogram"
        />
      </DashPanel>

      <DashPanel title="Koło Camelot" span={2} index={3}>
        <CamelotWheel buckets={sound.camelotKeys} testId="camelot-wheel" />
      </DashPanel>

      <DashPanel title="Tempo × energia" span={3} index={4}>
        <HeatMatrix cells={sound.tempoEnergy} testId="tempo-energy" />
      </DashPanel>

      <DashPanel title="Profil brzmienia" span={3} index={5}>
        <RadarChart
          metrics={sound.audioProfile}
          basis={scale.tracksWithMetrics}
          testId="audio-profile"
        />
      </DashPanel>

      <DashPanel title="Skąd wiemy to, co wiemy" span={3} index={6}>
        <h3>Źródło tempa</h3>
        <StackedBar
          buckets={quality.bpmSources}
          labels={BPM_SOURCE_LABELS}
          caption="Ile biblioteki stoi na zmierzonym fakcie, a ile na estymacie modelu (D6/D19)."
          testId="bpm-sources"
        />
        <h3>Pewność analizy AI</h3>
        <StackedBar buckets={quality.confidences} labels={CONFIDENCE_LABELS} testId="confidences" />
      </DashPanel>

      <DashPanel title="Kompletność danych" span={3} index={7}>
        <ul className="coverage" data-testid="coverage">
          {coverage.map((part, index) => (
            <li key={part.label} style={{ animationDelay: `${index * 60}ms` }}>
              <div className="coverage-head">
                <span>{part.label}</span>
                <span>{percentLabel(part.covered, part.total)}</span>
              </div>
              <div className="coverage-bar">
                <span className="coverage-fill" style={{ width: `${part.ratio * 100}%` }} />
              </div>
              <span className="muted">
                {part.covered} z {part.total} utworów katalogu
              </span>
            </li>
          ))}
        </ul>
        <p className="muted chart-caption">
          Braki uzupełnisz w zakładce Wzbogacanie: metadane {quality.metadataMissing},
          cechy audio {quality.audioMissing}, analiza AI {quality.aiMissing}.
        </p>
      </DashPanel>

      <DashPanel title="Biblioteka w czasie" span={4} index={8}>
        <AreaChart
          points={cumulativeGrowth(timeline.monthlyGrowth, scale.libraryTracks)}
          title="Stan biblioteki narastająco"
          caption="Ostatnie 12 miesięcy, kotwiczone dzisiejszym stanem biblioteki."
          testId="growth-cumulative"
        />
        <ColumnChart
          title="Przyrost po miesiącach"
          buckets={timeline.monthlyGrowth}
          caption="Licząc po dacie dodania do biblioteki."
          testId="monthly-growth"
        />
      </DashPanel>

      <DashPanel title="Roczniki" span={2} index={9}>
        <RankedBars buckets={timeline.decades} total={scale.catalogTracks} testId="decades" />
      </DashPanel>

      <DashPanel title="Co gram" span={3} index={10}>
        <h3>Gatunki</h3>
        <RankedBars buckets={sound.genres} total={scale.catalogTracks} testId="genres" />
        <h3>Style</h3>
        <RankedBars
          buckets={sound.styles}
          total={scale.catalogTracks}
          emptyText="Styl to pole free-form (D8) — wypełni je wzbogacanie AI."
          testId="styles"
        />
      </DashPanel>

      <DashPanel title="Najczęstsi wykonawcy" span={3} index={11}>
        <RankedBars
          buckets={taste.topArtists}
          total={scale.libraryTracks}
          ranked
          testId="top-artists"
        />
      </DashPanel>

      <DashPanel title="Twoje tagi" span={2} index={12}>
        <TagCloud buckets={taste.topTags} testId="top-tags" />
      </DashPanel>

      <DashPanel title="Oceny" span={2} index={13}>
        <RankedBars
          buckets={taste.ratings}
          total={scale.libraryTracks}
          labels={RATING_LABELS}
          testId="ratings"
        />
        <p className="muted chart-caption">
          Oceniłeś {ratedTracks} z {scale.libraryTracks} utworów
          ({percentLabel(ratedTracks, scale.libraryTracks)}).
        </p>
      </DashPanel>

      <DashPanel title="Skąd wpisy" span={2} index={14}>
        <StackedBar
          buckets={taste.sources}
          labels={LIBRARY_SOURCE_LABELS}
          caption="Tryby importu z D6 — plik CSV zawsze działa jako fallback."
          testId="library-sources"
        />
      </DashPanel>

      <DashPanel title="Długość utworów" span={3} index={15}>
        <AreaChart
          points={sound.durations}
          title="Rozkład czasu trwania"
          caption="Koszyki po pełnej minucie; przy secie liczy się, ile masz dłużyzn."
          testId="durations"
        />
      </DashPanel>

      <DashPanel title="Popularność" span={3} index={16}>
        <AreaChart
          points={sound.popularity}
          title="Popularność wg Spotify"
          caption="Skala 0–100 z metadanych; mówi, ile w bibliotece pewniaków, a ile niszy."
          testId="popularity"
        />
      </DashPanel>

      <DashPanel title="Ostatnio dodane" span={6} index={17}>
        <CoverStrip tracks={overview.recentlyAdded} testId="recently-added" />
      </DashPanel>
    </div>
  )
}

interface PanelProps {
  title: string
  /** Szerokość w kolumnach siatki pulpitu (z sześciu). */
  span: number
  /** Numer w kolejności wejścia — moduły zapalają się jeden po drugim. */
  index: number
  children: ReactNode
}

function DashPanel({ title, span, index, children }: PanelProps) {

  return (
    <section
      className={`panel dash-panel span-${span}`}
      aria-label={title}
      style={{ animationDelay: `${index * 55}ms` }}
    >
      <h2>{title}</h2>
      {children}
    </section>
  )
}

/** Zanim przyjdą liczby, pulpit pokazuje własny układ, a nie napis „Ładowanie". */
function DashboardSkeleton() {

  const spans = [6, 6, 4, 2, 3, 3, 3, 3]
  return (
    <div className="dash" aria-busy="true" aria-label="Przegląd biblioteki — ładowanie">
      {spans.map((span, index) => (
        <div key={index} className={`panel dash-panel skeleton span-${span}`}>
          <span className="skeleton-line skeleton-title" />
          <span className="skeleton-line" />
          <span className="skeleton-line short" />
        </div>
      ))}
    </div>
  )
}
