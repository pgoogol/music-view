package com.pgoogol.ingestion;

import com.pgoogol.common.ValidationException;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Rozpoznawanie kolumn CSV po nazwie, wspólne dla importu biblioteki (M1.2)
 * i metryk (D24). Nagłówki bywają w różnym zapisie i z BOM-em na początku pliku,
 * więc porównujemy je po sprowadzeniu do małych liter bez białych znaków.
 */
@Component
public class CsvHeaderResolver {

    public Map<String, Integer> normalize(CSVParser csvParser) {

        Map<String, Integer> headerMap = csvParser.getHeaderMap();
        if (Objects.isNull(headerMap) || headerMap.isEmpty()) {
            throw new ValidationException("CSV_EMPTY", "Plik CSV nie zawiera nagłówka");
        }
        return headerMap.entrySet().stream().collect(Collectors.toMap(
            entry -> entry.getKey().replace("\ufeff", "").trim().toLowerCase(Locale.ROOT),
            Map.Entry::getValue,
            (first, second) -> first));
    }

    public Optional<Integer> find(Map<String, Integer> headers, List<String> candidates) {

        return candidates.stream()
            .map(headers::get)
            .filter(Objects::nonNull)
            .findFirst();
    }

    public int require(Map<String, Integer> headers, List<String> candidates, String description) {

        return find(headers, candidates).orElseThrow(() -> new ValidationException(
            "CSV_MISSING_COLUMNS",
            "Plik CSV nie zawiera wymaganej kolumny: %s".formatted(description)));
    }

    /** Wartość komórki z pierwszej pasującej kolumny; pusta, gdy kolumny nie ma lub jest pusta. */
    public Optional<String> value(CSVRecord row, Map<String, Integer> headers, List<String> candidates) {

        return find(headers, candidates)
            .filter(column -> row.size() > column)
            .map(row::get)
            .map(String::trim)
            .filter(value -> !value.isEmpty());
    }
}
