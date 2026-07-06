package com.pgoogol.api;

import com.pgoogol.library.LibraryEntry;
import com.pgoogol.library.LibraryEntryUpdate;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class LibraryApiMapper {

    private final CatalogApiMapper catalogApiMapper;

    public LibraryApiMapper(CatalogApiMapper catalogApiMapper) {
        this.catalogApiMapper = catalogApiMapper;
    }

    public LibraryEntryResponse toResponse(LibraryEntry entry) {

        return new LibraryEntryResponse(
            entry.getId(),
            entry.getTrack().getSpotifyId(),
            Objects.toString(entry.getSource(), null),
            entry.getAddedAt(),
            entry.getDjNotes(),
            entry.getCustomTags(),
            entry.getRating(),
            entry.getDjSlotOverride(),
            catalogApiMapper.toResponse(entry.getTrack()));
    }

    public LibraryEntryUpdate toUpdate(UpdateLibraryEntryRequest request) {

        return new LibraryEntryUpdate(
            request.djNotes(),
            request.customTags(),
            request.rating(),
            request.djSlotOverride());
    }
}
