package com.pgoogol.catalog;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Sprowadza swobodne listy gatunków (np. „timba, son cubano, salsa") do enuma
 * {@link GenreFamily} (D8). Używane przy imporcie metryk z CSV (D24), żeby utwór
 * miał rodzinę gatunkową zanim ruszy LLM — bez niej nie policzymy slotu wieczoru
 * ani korekty half-time.
 *
 * <p>Kolejność reguł ma znaczenie: „disco polo" nie jest disco, a „latynoskie
 * dance" nie jest muzyką elektroniczną. Wygrywa pierwsza pasująca reguła;
 * gdy nic nie pasuje, gatunku po prostu nie ma — zostaje dla LLM-a (D11).</p>
 */
@Component
public class GenreFamilyMapper {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");

    private static final List<Rule> RULES = List.of(
        new Rule(GenreFamily.DISCO_POLO, List.of("disco polo", "disco-polo")),
        new Rule(GenreFamily.LATIN, List.of(
            "latin", "latyn", "salsa", "timba", "cubano", "cuban", "kuban", "bachata", "merengue",
            "cumbia", "reggaeton", "bolero", "mambo", "cha-cha", "chacha", "guaracha", "songo",
            "charanga", "trova", "samba", "bossa", "tango", "flamenco", "rumba", "afrokub", "afro-cub")),
        new Rule(GenreFamily.HIP_HOP, List.of("hip hop", "hip-hop", "hiphop", "rap", "trap")),
        new Rule(GenreFamily.ROCK, List.of("rock", "metal", "punk", "grunge")),
        new Rule(GenreFamily.ELECTRONIC, List.of(
            "electronic", "elektron", "house", "techno", "trance", "edm", "dubstep",
            "drum and bass", "drum'n'bass")),
        new Rule(GenreFamily.DISCO, List.of("disco", "funk", "boogie", "italo")),
        new Rule(GenreFamily.POP, List.of("pop")));

    /**
     * @param rawGenres listy gatunków od najbardziej szczegółowej; puste i {@code null}
     *                  wpisy są pomijane
     */
    public Optional<GenreFamily> map(List<String> rawGenres) {

        Objects.requireNonNull(rawGenres, "rawGenres");
        String genres = rawGenres.stream()
            .filter(Objects::nonNull)
            .map(this::normalize)
            .filter(value -> !value.isBlank())
            .collect(Collectors.joining(", "));
        if (genres.isBlank()) {
            return Optional.empty();
        }
        return RULES.stream()
            .filter(rule -> rule.matches(genres))
            .map(Rule::family)
            .findFirst();
    }

    private String normalize(@Nullable String raw) {

        if (Objects.isNull(raw)) {
            return "";
        }
        String withoutDiacritics = DIACRITICS.matcher(
            Normalizer.normalize(raw, Normalizer.Form.NFD)).replaceAll("");
        return withoutDiacritics.toLowerCase(Locale.ROOT).trim();
    }

    private record Rule(GenreFamily family, List<String> keywords) {

        boolean matches(String genres) {
            return keywords.stream().anyMatch(genres::contains);
        }
    }
}
