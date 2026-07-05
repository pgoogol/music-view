package com.pgoogol.enrichment.llm;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Prompt „ekspert muzyczny i DJ" ładowany z zasobu wersjonowanego przez
 * konfigurację ({@code llm.prompt-version}, D15) — treść promptu nigdy w kodzie.
 * Format pliku: prompt systemowy, separator {@code ---USER---}, szablon
 * użytkownika z placeholderem {@code {{TRACKS}}}.
 */
@Component
public class TrackAnalysisPrompt {

    private static final String SEPARATOR = "---USER---";
    private static final String TRACKS_PLACEHOLDER = "{{TRACKS}}";

    private final String system;
    private final String userTemplate;
    private final String version;

    public TrackAnalysisPrompt(LlmProperties properties) {

        this.version = properties.promptVersion();
        String raw = loadResource("llm/track-analysis-%s.txt".formatted(version));
        String[] parts = raw.split(SEPARATOR, 2);
        if (parts.length != 2) {
            throw new IllegalStateException(
                "Prompt %s nie zawiera separatora %s".formatted(version, SEPARATOR));
        }
        this.system = parts[0].strip();
        this.userTemplate = parts[1].strip();
    }

    public String system() {
        return system;
    }

    public String user(String tracksJson) {
        return userTemplate.replace(TRACKS_PLACEHOLDER, tracksJson);
    }

    public String version() {
        return version;
    }

    private String loadResource(String path) {

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (Objects.isNull(input)) {
                throw new IllegalStateException(
                    "Brak pliku promptu '%s' — sprawdź llm.prompt-version".formatted(path));
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Nie udało się wczytać promptu: " + path, ex);
        }
    }
}
