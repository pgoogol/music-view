package com.pgoogol.ingestion;

import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogRepository;
import com.pgoogol.library.LibraryEntry;
import com.pgoogol.library.LibraryEntryRepository;
import com.pgoogol.library.LibrarySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Import CSV do biblioteki (M1.2, tryb A). Dedup po {@code spotify_id}:
 * duplikat w bibliotece lub w samym pliku liczony jako {@code alreadyExisted}.
 * Nowy utwór dostaje szkielet w {@code track_catalog} (title/artist/album z CSV;
 * resztę uzupełnia wzbogacanie) + wpis {@code library_entry} z source=FILE.
 */
@Service
public class FileIngestionService {

    private static final Logger log = LoggerFactory.getLogger(FileIngestionService.class);

    private final CsvTrackParser parser;
    private final TrackCatalogRepository trackCatalogRepository;
    private final LibraryEntryRepository libraryEntryRepository;

    public FileIngestionService(CsvTrackParser parser,
                                TrackCatalogRepository trackCatalogRepository,
                                LibraryEntryRepository libraryEntryRepository) {

        this.parser = parser;
        this.trackCatalogRepository = trackCatalogRepository;
        this.libraryEntryRepository = libraryEntryRepository;
    }

    @Transactional
    public IngestReport ingestFile(InputStream csv) {

        CsvParseResult parsed = parser.parse(csv);
        Map<String, ParsedTrack> uniqueTracks = parsed.tracks().stream().collect(Collectors.toMap(
            ParsedTrack::spotifyId, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        int duplicatesInFile = parsed.tracks().size() - uniqueTracks.size();

        Set<String> existingInLibrary = uniqueTracks.isEmpty()
            ? Set.of()
            : libraryEntryRepository.findExistingTrackIds(uniqueTracks.keySet());
        List<ParsedTrack> toImport = uniqueTracks.values().stream()
            .filter(track -> !existingInLibrary.contains(track.spotifyId()))
            .toList();

        saveSkeletons(toImport);
        saveLibraryEntries(toImport);

        IngestReport report = new IngestReport(
            toImport.size(),
            existingInLibrary.size() + duplicatesInFile,
            parsed.errors());
        log.info("Import CSV zakończony: imported={}, alreadyExisted={}, failed={}",
            report.imported(), report.alreadyExisted(), report.failed().size());
        return report;
    }

    private void saveSkeletons(List<ParsedTrack> toImport) {

        Set<String> existingInCatalog = toImport.isEmpty()
            ? Set.of()
            : trackCatalogRepository.findExistingIds(
                toImport.stream().map(ParsedTrack::spotifyId).collect(Collectors.toSet()));
        List<TrackCatalog> skeletons = toImport.stream()
            .filter(track -> !existingInCatalog.contains(track.spotifyId()))
            .map(this::toSkeleton)
            .toList();
        trackCatalogRepository.saveAll(skeletons);
    }

    private void saveLibraryEntries(List<ParsedTrack> toImport) {

        List<LibraryEntry> entries = toImport.stream()
            .map(track -> new LibraryEntry(
                trackCatalogRepository.getReferenceById(track.spotifyId()), LibrarySource.FILE))
            .toList();
        libraryEntryRepository.saveAll(entries);
    }

    private TrackCatalog toSkeleton(ParsedTrack parsedTrack) {

        TrackCatalog track = new TrackCatalog(
            parsedTrack.spotifyId(), parsedTrack.title(), parsedTrack.artist());
        track.setAlbum(parsedTrack.album());
        return track;
    }
}
