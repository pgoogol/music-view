package com.pgoogol.api;

import com.pgoogol.common.ValidationException;
import com.pgoogol.library.LibraryEntry;
import com.pgoogol.library.LibraryEntryUpdate;
import com.pgoogol.playlist.DjSlot;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

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
            canonicalSlotOverride(request.djSlotOverride()));
    }

    /** Override slotu musi być jedną z wartości {@link DjSlot}; pusty = wyczyszczenie. */
    private String canonicalSlotOverride(String djSlotOverride) {

        if (Objects.isNull(djSlotOverride) || djSlotOverride.isBlank()) {
            return djSlotOverride;
        }
        return DjSlot.parse(djSlotOverride)
            .map(DjSlot::name)
            .orElseThrow(() -> new ValidationException("DJ_SLOT_INVALID",
                "Nieznany slot '%s' — dopuszczalne: %s".formatted(djSlotOverride,
                    Arrays.stream(DjSlot.values()).map(DjSlot::name)
                        .collect(Collectors.joining(", ")))));
    }
}
