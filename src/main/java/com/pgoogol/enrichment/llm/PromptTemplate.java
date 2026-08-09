package com.pgoogol.enrichment.llm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Prompt wczytany z zasobu wersjonowanego przez konfigurację (D15) — treść
 * promptu nigdy w kodzie. Format pliku: prompt systemowy, separator
 * {@code ---USER---}, szablon użytkownika z placeholderami {@code {{NAZWA}}}.
 *
 * <p>Wspólne dla wszystkich promptów projektu: analizy utworu (M1.5)
 * i tłumaczenia tekstu (M6.1) — różnią się wyłącznie nazwą zasobu
 * i zestawem placeholderów.</p>
 */
public final class PromptTemplate {

    private static final String SEPARATOR = "---USER---";

    private final String system;
    private final String userTemplate;

    private PromptTemplate(String system, String userTemplate) {

        this.system = system;
        this.userTemplate = userTemplate;
    }

    public static PromptTemplate load(String resourcePath) {

        String raw = read(resourcePath);
        String[] parts = raw.split(SEPARATOR, 2);
        if (parts.length != 2) {
            throw new IllegalStateException(
                "Prompt %s nie zawiera separatora %s".formatted(resourcePath, SEPARATOR));
        }
        return new PromptTemplate(parts[0].strip(), parts[1].strip());
    }

    public String system() {
        return system;
    }

    public String user(Map<String, String> values) {

        String user = userTemplate;
        for (Map.Entry<String, String> value : values.entrySet()) {
            user = user.replace("{{%s}}".formatted(value.getKey()), value.getValue());
        }
        return user;
    }

    private static String read(String resourcePath) {

        try (InputStream input = PromptTemplate.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (Objects.isNull(input)) {
                throw new IllegalStateException(
                    "Brak pliku promptu '%s' — sprawdź wersję promptu w konfiguracji"
                        .formatted(resourcePath));
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Nie udało się wczytać promptu: " + resourcePath, ex);
        }
    }
}
