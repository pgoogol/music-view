package com.pgoogol.api;

import com.pgoogol.common.ValidationException;
import com.pgoogol.ingestion.FileIngestionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    private final FileIngestionService fileIngestionService;
    private final IngestApiMapper mapper;

    public IngestController(FileIngestionService fileIngestionService, IngestApiMapper mapper) {

        this.fileIngestionService = fileIngestionService;
        this.mapper = mapper;
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngestFileResponse ingestFile(@RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            throw new ValidationException("FILE_EMPTY", "Przesłany plik jest pusty");
        }
        try (InputStream input = file.getInputStream()) {
            return mapper.toResponse(fileIngestionService.ingestFile(input));
        } catch (IOException ex) {
            throw new ValidationException("FILE_UNREADABLE", "Nie udało się odczytać przesłanego pliku");
        }
    }
}
