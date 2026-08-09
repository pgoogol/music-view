package com.pgoogol.enrichment;

import com.pgoogol.catalog.AudioFeatures;
import com.pgoogol.catalog.AudioFeaturesRepository;
import com.pgoogol.catalog.BpmSource;
import com.pgoogol.catalog.ManualMetrics;
import com.pgoogol.catalog.ManualMetricsRepository;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.enrichment.bpm.BpmResolver;
import com.pgoogol.enrichment.bpm.HalfTimeCorrector;
import com.pgoogol.enrichment.llm.LlmProperties;
import com.pgoogol.enrichment.llm.TrackAnalysis;
import com.pgoogol.enrichment.llm.TrackAnalysisResult;
import com.pgoogol.enrichment.llm.TrackAnalysisService;
import com.pgoogol.enrichment.lyrics.LyricsEnricher;
import com.pgoogol.enrichment.metrics.ManualMetricsApplier;
import com.pgoogol.enrichment.musicbrainz.MusicBrainzClient;
import com.pgoogol.enrichment.spotify.SpotifyClient;
import com.pgoogol.enrichment.spotify.SpotifyTrackMetadata;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Wzbogaca partię utworów w kolejności METADATA → AUDIO → AI → LYRICS (D11,
 * D32) — serce processora joba M1.6. Pracuje na partii wielkości chunka (5),
 * dzięki czemu wywołania Spotify i LLM idą batchem. Mutuje encje katalogu;
 * ich zapis należy do writera (teksty mają własną tabelę i własny zapis).
 */
@Component
public class TrackEnricher {


    private final SpotifyClient spotifyClient;
    private final MusicBrainzClient musicBrainzClient;
    private final AudioFeaturesRepository audioFeaturesRepository;
    private final ManualMetricsRepository manualMetricsRepository;
    private final ManualMetricsApplier manualMetricsApplier;
    private final BpmResolver bpmResolver;
    private final TrackAnalysisService trackAnalysisService;
    private final LyricsEnricher lyricsEnricher;
    private final TempoClassifier tempoClassifier;
    private final HalfTimeCorrector halfTimeCorrector;
    private final LlmProperties llmProperties;

    public TrackEnricher(SpotifyClient spotifyClient, MusicBrainzClient musicBrainzClient,
                         AudioFeaturesRepository audioFeaturesRepository,
                         ManualMetricsRepository manualMetricsRepository,
                         ManualMetricsApplier manualMetricsApplier, BpmResolver bpmResolver,
                         TrackAnalysisService trackAnalysisService, LyricsEnricher lyricsEnricher,
                         TempoClassifier tempoClassifier,
                         HalfTimeCorrector halfTimeCorrector, LlmProperties llmProperties) {

        this.spotifyClient = spotifyClient;
        this.musicBrainzClient = musicBrainzClient;
        this.audioFeaturesRepository = audioFeaturesRepository;
        this.manualMetricsRepository = manualMetricsRepository;
        this.manualMetricsApplier = manualMetricsApplier;
        this.bpmResolver = bpmResolver;
        this.trackAnalysisService = trackAnalysisService;
        this.lyricsEnricher = lyricsEnricher;
        this.tempoClassifier = tempoClassifier;
        this.halfTimeCorrector = halfTimeCorrector;
        this.llmProperties = llmProperties;
    }

    /**
     * @param force zlecenie na konkretne utwory (SINGLE/SELECTED) — pobiera
     *              tekst od nowa zamiast omijać utwory rozstrzygnięte (D32)
     */
    public void enrich(List<TrackCatalog> tracks, Set<FieldGroup> fields, boolean force) {

        Objects.requireNonNull(tracks, "tracks");
        Objects.requireNonNull(fields, "fields");
        if (tracks.isEmpty()) {
            return;
        }
        Map<String, ManualMetrics> manualMetrics = manualMetrics(tracks);
        if (fields.contains(FieldGroup.METADATA)) {
            applyMetadata(tracks);
        }
        if (fields.contains(FieldGroup.AUDIO)) {
            applyAudio(tracks, manualMetrics);
        }
        if (fields.contains(FieldGroup.AI)) {
            applyAi(tracks, manualMetrics);
        }
        if (fields.contains(FieldGroup.LYRICS)) {
            // teksty zapisuje własne repozytorium (osobna tabela, D32) — writer
            // joba utrwala tylko katalog, więc ten zapis nie należy do niego
            tracks.forEach(track -> lyricsEnricher.enrich(track, force));
        }
        tracks.stream()
            .filter(track -> Objects.nonNull(track.getBpm()))
            .forEach(this::finalizeBpm);
    }

    /**
     * Kaskada AUDIO biegnie przed AI, więc korekta half-time w BpmResolverze
     * nie zna jeszcze gatunku — ponawiamy ją tutaj, gdy genre_family jest już
     * ustalone (idempotentna: po podwojeniu BPM ≥ 100); na końcu tempo_class.
     */
    private void finalizeBpm(TrackCatalog track) {

        int corrected = halfTimeCorrector.correct(track.getGenreFamily(), track.getBpm());
        track.setBpm(corrected);
        track.setTempoClass(tempoClassifier.classify(corrected));
    }

    private void applyMetadata(List<TrackCatalog> tracks) {

        List<String> ids = tracks.stream().map(TrackCatalog::getSpotifyId).toList();
        Map<String, SpotifyTrackMetadata> bySpotifyId = spotifyClient.getTracks(ids).stream()
            .collect(Collectors.toMap(SpotifyTrackMetadata::spotifyId, Function.identity()));
        tracks.forEach(track -> Optional.ofNullable(bySpotifyId.get(track.getSpotifyId()))
            .ifPresent(metadata -> applyMetadata(track, metadata)));
    }

    private void applyMetadata(TrackCatalog track, SpotifyTrackMetadata metadata) {

        track.setTitle(metadata.title());
        track.setArtist(metadata.artist());
        track.setAlbum(metadata.album());
        track.setIsrc(metadata.isrc());
        track.setYear(metadata.year());
        track.setDurationMs(metadata.durationMs());
        track.setPopularity(metadata.popularity());
        track.setExplicit(metadata.explicit());
        track.setAlbumImageUrl(metadata.albumImageUrl());
    }

    /** Metryki wgrane ręcznie (D24) są faktami z pliku, więc idą po AcousticBrainz — wygrywają. */
    private void applyAudio(List<TrackCatalog> tracks, Map<String, ManualMetrics> manualMetrics) {

        tracks.forEach(track -> {
            if (Objects.nonNull(track.getIsrc())) {
                // buduje trwały cache ISRC→MBID pod ETL dumpa AB (docs/AB_ETL.md)
                musicBrainzClient.lookupMbid(track.getIsrc());
            }
            audioFeaturesRepository.findByTrackSpotifyId(track.getSpotifyId())
                .ifPresent(features -> applyAudioFeatures(track, features));
            Optional.ofNullable(manualMetrics.get(track.getSpotifyId()))
                .ifPresent(metrics -> manualMetricsApplier.apply(track, metrics));
            bpmResolver.resolve(track).ifPresent(resolution -> {
                track.setBpm(resolution.bpm());
                track.setBpmSource(resolution.source());
            });
        });
    }

    private Map<String, ManualMetrics> manualMetrics(List<TrackCatalog> tracks) {

        List<String> ids = tracks.stream().map(TrackCatalog::getSpotifyId).toList();
        return manualMetricsRepository.findBySpotifyIdIn(ids).stream()
            .collect(Collectors.toMap(ManualMetrics::getSpotifyId, Function.identity()));
    }

    private void applyAudioFeatures(TrackCatalog track, AudioFeatures features) {

        if (Objects.nonNull(features.getMusicalKey())) {
            track.setMusicalKey(features.getMusicalKey());
        }
        if (Objects.nonNull(features.getDanceability())) {
            track.setDanceability(features.getDanceability());
        }
    }

    private void applyAi(List<TrackCatalog> tracks, Map<String, ManualMetrics> manualMetrics) {

        TrackAnalysisResult result = trackAnalysisService.analyze(tracks, true);
        Map<String, TrackCatalog> byId = tracks.stream()
            .collect(Collectors.toMap(TrackCatalog::getSpotifyId, Function.identity()));
        result.analyses().forEach(analysis -> applyAnalysis(byId.get(analysis.spotifyId()), analysis,
            measuredEnergy(manualMetrics.get(analysis.spotifyId()))));
    }

    /** Zmierzona energia z pliku (D24) bije estymatę LLM-a — reszta analizy zostaje AI-owa. */
    private boolean measuredEnergy(ManualMetrics metrics) {

        return Objects.nonNull(metrics) && Objects.nonNull(metrics.getEnergy());
    }

    private void applyAnalysis(TrackCatalog track, TrackAnalysis analysis, boolean measuredEnergy) {

        track.setStyle(analysis.style());
        track.setGenreFamily(analysis.genreFamily());
        track.setLyricsTheme(analysis.lyricsTheme());
        track.setDescriptionPl(analysis.descriptionPl());
        if (!measuredEnergy) {
            track.setEnergy(analysis.energy());
        }
        track.setConfidence(analysis.confidence());
        if (Objects.isNull(track.getBpm()) && Objects.nonNull(analysis.bpmEstimate())) {
            track.setBpm(analysis.bpmEstimate());
            track.setBpmSource(BpmSource.LLM);
        }
        track.setEnrichedAt(Instant.now());
        track.setModelUsed(llmProperties.model());
        track.setEnrichVersion(llmProperties.promptVersionNumber().orElse(null));
    }
}
