package com.pgoogol.enrichment.llm;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Prompt „ekspert muzyczny i DJ" ładowany z zasobu wersjonowanego przez
 * konfigurację ({@code llm.prompt-version}, D15) — treść promptu nigdy w kodzie.
 * Format pliku opisuje {@link PromptTemplate}; placeholder: {@code {{TRACKS}}}.
 */
@Component
public class TrackAnalysisPrompt {

    private final PromptTemplate template;
    private final String version;

    public TrackAnalysisPrompt(LlmProperties properties) {

        this.version = properties.promptVersion();
        this.template = PromptTemplate.load("llm/track-analysis-%s.txt".formatted(version));
    }

    public String system() {
        return template.system();
    }

    public String user(String tracksJson) {
        return template.user(Map.of("TRACKS", tracksJson));
    }

    public String version() {
        return version;
    }
}
