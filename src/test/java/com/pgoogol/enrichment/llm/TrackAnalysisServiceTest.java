package com.pgoogol.enrichment.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.common.ExternalServiceException;
import com.pgoogol.enrichment.bpm.HalfTimeCorrector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class TrackAnalysisServiceTest {

    private static final LlmProperties PROPERTIES = new LlmProperties(
        "openai", null, "test-key", "test-model", "v1", 5, 100, 2048, 0.2, 500, null);

    @Mock
    private LlmClient llmClient;

    private TrackAnalysisService service;

    @BeforeEach
    void setUp() {

        service = new TrackAnalysisService(llmClient, new TrackAnalysisPrompt(PROPERTIES),
            new ObjectMapper(), new HalfTimeCorrector(), PROPERTIES);
    }

    @Test
    void analyze_whenTwelveTracks_batchesByFive() {

        // given
        List<TrackCatalog> tracks = tracks(12);
        given(llmClient.complete(any())).willReturn(new LlmCompletion("[]", 10, 5));

        // when
        service.analyze(tracks, false);

        // then
        then(llmClient).should(times(3)).complete(any());
    }

    @Test
    void analyze_whenBpmMissingAndEstimateRequested_flagsOnlyTracksWithoutBpm() {

        // given
        TrackCatalog withBpm = track("sp-0", "Vivir Mi Vida", "Marc Anthony");
        withBpm.setBpm(184);
        TrackCatalog withoutBpm = track("sp-1", "La Gozadera", "Gente De Zona");
        given(llmClient.complete(any())).willReturn(new LlmCompletion("[]", 10, 5));

        // when
        service.analyze(List.of(withBpm, withoutBpm), true);

        // then
        ArgumentCaptor<LlmPrompt> prompt = ArgumentCaptor.forClass(LlmPrompt.class);
        then(llmClient).should().complete(prompt.capture());
        assertThat(prompt.getValue().user())
            .contains("\"spotify_id\":\"sp-0\",\"title\":\"Vivir Mi Vida\"")
            .contains("\"bpm\":184,\"oszacuj_bpm\":false")
            .contains("\"bpm\":null,\"oszacuj_bpm\":true");
        assertThat(prompt.getValue().system()).contains("ekspertem muzycznym");
    }

    @Test
    void analyze_whenValidResponse_mapsStructuredAnalysis() {

        // given
        TrackCatalog track = track("sp-0", "Vivir Mi Vida", "Marc Anthony");
        given(llmClient.complete(any())).willReturn(new LlmCompletion("""
            [{
              "spotify_id": "sp-0",
              "style": "salsa",
              "genre_family": "latin",
              "lyrics_theme": "afirmacja życia mimo przeciwności",
              "description_pl": "Energetyczna salsa na parkiet w szczycie imprezy.",
              "energy": "high",
              "confidence": "high",
              "bpm_estimate": null
            }]
            """, 700, 550));

        // when
        TrackAnalysisResult result = service.analyze(List.of(track), false);

        // then
        assertThat(result.analyses()).containsExactly(new TrackAnalysis(
            "sp-0", "salsa", GenreFamily.LATIN, "afirmacja życia mimo przeciwności",
            "Energetyczna salsa na parkiet w szczycie imprezy.", "high", "high", null));
        assertThat(result.inputTokens()).isEqualTo(700);
        assertThat(result.outputTokens()).isEqualTo(550);
    }

    @Test
    void analyze_whenResponseWrappedInMarkdownFences_stillParses() {

        // given
        TrackCatalog track = track("sp-0", "Utwór", "Wykonawca");
        given(llmClient.complete(any())).willReturn(new LlmCompletion("""
            ```json
            [{"spotify_id": "sp-0", "style": "pop", "genre_family": "pop",
              "lyrics_theme": "t", "description_pl": "d", "energy": "medium",
              "confidence": "low", "bpm_estimate": null}]
            ```
            """, 10, 5));

        // when
        TrackAnalysisResult result = service.analyze(List.of(track), false);

        // then
        assertThat(result.analyses()).singleElement()
            .satisfies(analysis -> assertThat(analysis.genreFamily()).isEqualTo(GenreFamily.POP));
    }

    @Test
    void analyze_whenResponseNotJson_throwsExternalServiceException() {

        // given
        TrackCatalog track = track("sp-0", "Utwór", "Wykonawca");
        given(llmClient.complete(any()))
            .willReturn(new LlmCompletion("Przepraszam, nie mogę tego zrobić.", 10, 5));

        // when
        Throwable thrown = catchThrowable(() -> service.analyze(List.of(track), false));

        // then
        assertThat(thrown).isInstanceOf(ExternalServiceException.class)
            .hasMessageContaining("JSON");
    }

    @Test
    void analyze_whenUnknownGenreFamily_fallsBackToOther() {

        // given
        TrackCatalog track = track("sp-0", "Utwór", "Wykonawca");
        given(llmClient.complete(any())).willReturn(new LlmCompletion("""
            [{"spotify_id": "sp-0", "style": "shanty", "genre_family": "sea_shanty",
              "lyrics_theme": "t", "description_pl": "d", "energy": "low",
              "confidence": "low", "bpm_estimate": null}]
            """, 10, 5));

        // when
        TrackAnalysisResult result = service.analyze(List.of(track), false);

        // then
        assertThat(result.analyses()).singleElement()
            .satisfies(analysis -> assertThat(analysis.genreFamily()).isEqualTo(GenreFamily.OTHER));
    }

    @Test
    void analyze_whenLatinBpmEstimateLooksHalfTime_appliesHalfTimeCorrection() {

        // given
        TrackCatalog track = track("sp-0", "Timba utwór", "Los Van Van");
        given(llmClient.complete(any())).willReturn(new LlmCompletion("""
            [{"spotify_id": "sp-0", "style": "timba", "genre_family": "latin",
              "lyrics_theme": "t", "description_pl": "d", "energy": "high",
              "confidence": "medium", "bpm_estimate": 95}]
            """, 10, 5));

        // when
        TrackAnalysisResult result = service.analyze(List.of(track), true);

        // then
        assertThat(result.analyses()).singleElement()
            .satisfies(analysis -> assertThat(analysis.bpmEstimate()).isEqualTo(190));
    }

    @Test
    void analyze_whenResponseContainsUnknownTrack_skipsItWithWarning() {

        // given
        TrackCatalog track = track("sp-0", "Utwór", "Wykonawca");
        given(llmClient.complete(any())).willReturn(new LlmCompletion("""
            [{"spotify_id": "sp-obcy", "style": "x", "genre_family": "pop",
              "lyrics_theme": "t", "description_pl": "d", "energy": "low",
              "confidence": "low", "bpm_estimate": null}]
            """, 10, 5));

        // when
        TrackAnalysisResult result = service.analyze(List.of(track), false);

        // then
        assertThat(result.analyses()).isEmpty();
    }

    @Test
    void analyze_whenTenDiverseTracks_returnsSensibleGenreFamilies() {

        // given — DoD M1.5: 10 zróżnicowanych utworów (latino / rock / disco polo…),
        // odpowiedzi jak od realnego modelu, w 2 batchach po 5
        List<TrackCatalog> tracks = List.of(
            track("sp-0", "Vivir Mi Vida", "Marc Anthony"),
            track("sp-1", "La Vida Es Un Carnaval", "Celia Cruz"),
            track("sp-2", "Propuesta Indecente", "Romeo Santos"),
            track("sp-3", "Bohemian Rhapsody", "Queen"),
            track("sp-4", "Highway to Hell", "AC/DC"),
            track("sp-5", "Przez Twe Oczy Zielone", "Akcent"),
            track("sp-6", "Jesteś szalona", "Boys"),
            track("sp-7", "One More Time", "Daft Punk"),
            track("sp-8", "I Will Survive", "Gloria Gaynor"),
            track("sp-9", "Lose Yourself", "Eminem"));
        given(llmClient.complete(any())).willReturn(
            new LlmCompletion(firstBatchResponse(), 750, 620),
            new LlmCompletion(secondBatchResponse(), 730, 590));

        // when
        TrackAnalysisResult result = service.analyze(tracks, false);

        // then
        Map<String, GenreFamily> expected = Map.of(
            "sp-0", GenreFamily.LATIN, "sp-1", GenreFamily.LATIN, "sp-2", GenreFamily.LATIN,
            "sp-3", GenreFamily.ROCK, "sp-4", GenreFamily.ROCK,
            "sp-5", GenreFamily.DISCO_POLO, "sp-6", GenreFamily.DISCO_POLO,
            "sp-7", GenreFamily.ELECTRONIC, "sp-8", GenreFamily.DISCO, "sp-9", GenreFamily.HIP_HOP);
        assertThat(result.analyses()).hasSize(10);
        assertThat(result.analyses()).allSatisfy(analysis -> {
            assertThat(analysis.genreFamily()).isEqualTo(expected.get(analysis.spotifyId()));
            assertThat(analysis.descriptionPl()).isNotBlank();
            assertThat(analysis.lyricsTheme()).isNotBlank();
        });
        assertThat(result.inputTokens()).isEqualTo(1480);
        assertThat(result.outputTokens()).isEqualTo(1210);
    }

    private String firstBatchResponse() {
        return """
            [
              {"spotify_id": "sp-0", "style": "salsa", "genre_family": "latin",
               "lyrics_theme": "afirmacja życia", "description_pl": "Salsa na szczyt imprezy.",
               "energy": "high", "confidence": "high", "bpm_estimate": null},
              {"spotify_id": "sp-1", "style": "salsa", "genre_family": "latin",
               "lyrics_theme": "życie jak karnawał", "description_pl": "Klasyk salsy, zawsze działa.",
               "energy": "high", "confidence": "high", "bpm_estimate": null},
              {"spotify_id": "sp-2", "style": "bachata", "genre_family": "latin",
               "lyrics_theme": "nieprzyzwoita propozycja", "description_pl": "Zmysłowa bachata na środek wieczoru.",
               "energy": "medium", "confidence": "high", "bpm_estimate": null},
              {"spotify_id": "sp-3", "style": "rock progresywny", "genre_family": "rock",
               "lyrics_theme": "spowiedź skazańca", "description_pl": "Epicki klasyk do wspólnego śpiewania.",
               "energy": "medium", "confidence": "high", "bpm_estimate": null},
              {"spotify_id": "sp-4", "style": "hard rock", "genre_family": "rock",
               "lyrics_theme": "droga do piekła", "description_pl": "Rockowy pewniak na rozkręcenie parkietu.",
               "energy": "high", "confidence": "high", "bpm_estimate": null}
            ]
            """;
    }

    private String secondBatchResponse() {
        return """
            [
              {"spotify_id": "sp-5", "style": "disco polo", "genre_family": "disco_polo",
               "lyrics_theme": "zielone oczy ukochanej", "description_pl": "Hymn polskich wesel.",
               "energy": "high", "confidence": "high", "bpm_estimate": null},
              {"spotify_id": "sp-6", "style": "disco polo", "genre_family": "disco_polo",
               "lyrics_theme": "szalona dziewczyna", "description_pl": "Klasyk disco polo, pełen parkiet.",
               "energy": "high", "confidence": "high", "bpm_estimate": null},
              {"spotify_id": "sp-7", "style": "french house", "genre_family": "electronic",
               "lyrics_theme": "wspólna zabawa", "description_pl": "House'owy klasyk na taneczny peak.",
               "energy": "high", "confidence": "high", "bpm_estimate": null},
              {"spotify_id": "sp-8", "style": "disco", "genre_family": "disco",
               "lyrics_theme": "siła po rozstaniu", "description_pl": "Disco klasyk, śpiewa cała sala.",
               "energy": "high", "confidence": "high", "bpm_estimate": null},
              {"spotify_id": "sp-9", "style": "rap", "genre_family": "hip_hop",
               "lyrics_theme": "wykorzystaj swoją szansę", "description_pl": "Motywacyjny rap, mocny moment.",
               "energy": "high", "confidence": "high", "bpm_estimate": null}
            ]
            """;
    }

    private List<TrackCatalog> tracks(int count) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> track("sp-" + i, "Utwór " + i, "Wykonawca " + i))
            .toList();
    }

    private TrackCatalog track(String spotifyId, String title, String artist) {
        return new TrackCatalog(spotifyId, title, artist);
    }
}
