package com.pgoogol.enrichment.llm;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Prompt tłumacza i interpretatora tekstów (M6.1/D32) — wersja wyłącznie
 * w konfiguracji ({@code llm.lyrics.prompt-version}, D15), osobno od promptu
 * analizy utworu: tłumaczenie zmienia się z innych powodów niż opis DJ-ski
 * i ma własną wersję w {@code track_lyrics.prompt_version}.
 *
 * <p>Placeholdery: {@code {{TRACK}}} (wykonawca — tytuł, album, rok)
 * i {@code {{LYRICS}}} (tekst oryginalny).</p>
 */
@Component
public class LyricsTranslationPrompt {

    private final PromptTemplate template;
    private final String version;

    public LyricsTranslationPrompt(LlmProperties properties) {

        this.version = properties.lyrics().promptVersion();
        this.template = PromptTemplate.load("llm/lyrics-translation-%s.txt".formatted(version));
    }

    public String system() {
        return template.system();
    }

    public String user(String track, String lyrics) {
        return template.user(Map.of("TRACK", track, "LYRICS", lyrics));
    }

    public String version() {
        return version;
    }
}
