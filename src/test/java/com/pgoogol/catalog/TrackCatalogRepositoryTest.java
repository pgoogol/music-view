package com.pgoogol.catalog;

import com.pgoogol.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TrackCatalogRepositoryTest {

    @Autowired
    private TrackCatalogRepository repository;

    @Test
    void save_whenEnrichedTrackGiven_persistsAllFieldGroups() {

        // given
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack("sp-full");

        // when
        repository.saveAndFlush(track);
        Optional<TrackCatalog> found = repository.findById("sp-full");

        // then
        assertThat(found).hasValueSatisfying(t -> {
            assertThat(t.getTitle()).isEqualTo("Vivir Mi Vida");
            assertThat(t.getIsrc()).isEqualTo("USSD11300483");
            assertThat(t.getYear()).isEqualTo(2013);
            assertThat(t.getGenreFamily()).isEqualTo(GenreFamily.LATIN);
            assertThat(t.getBpm()).isEqualTo(92);
            assertThat(t.getBpmSource()).isEqualTo(BpmSource.ACOUSTICBRAINZ);
            assertThat(t.getDanceability()).isEqualByComparingTo(new BigDecimal("0.850"));
            assertThat(t.getTempoClass()).isEqualTo(TempoClass.MEDIUM);
            assertThat(t.getDescriptionPl()).isEqualTo("Energetyczna salsa o radości życia.");
        });
    }

    @Test
    void findMetadataMissing_whenMetadataFieldsNull_returnsOnlyIncompleteTrack() {

        // given
        repository.save(TrackCatalogFixtures.skeletonTrack("sp-skeleton"));
        repository.save(TrackCatalogFixtures.enrichedTrack("sp-full"));

        // when
        List<TrackCatalog> missing = repository.findMetadataMissing();

        // then
        assertThat(missing).extracting(TrackCatalog::getSpotifyId).containsExactly("sp-skeleton");
    }

    @Test
    void findAudioMissing_whenBpmNull_returnsOnlyIncompleteTrack() {

        // given
        repository.save(TrackCatalogFixtures.skeletonTrack("sp-skeleton"));
        repository.save(TrackCatalogFixtures.enrichedTrack("sp-full"));

        // when
        List<TrackCatalog> missing = repository.findAudioMissing();

        // then
        assertThat(missing).extracting(TrackCatalog::getSpotifyId).containsExactly("sp-skeleton");
    }

    @Test
    void findAiMissing_whenPartOfAiFieldsNull_returnsOnlyIncompleteTrack() {

        // given
        TrackCatalog partiallyEnriched = TrackCatalogFixtures.enrichedTrack("sp-partial");
        partiallyEnriched.setLyricsTheme(null);
        repository.save(partiallyEnriched);
        repository.save(TrackCatalogFixtures.enrichedTrack("sp-full"));

        // when
        List<TrackCatalog> missing = repository.findAiMissing();

        // then
        assertThat(missing).extracting(TrackCatalog::getSpotifyId).containsExactly("sp-partial");
    }

    @Test
    void countAudioMissing_whenOneOfTwoTracksIncomplete_returnsOne() {

        // given
        repository.save(TrackCatalogFixtures.skeletonTrack("sp-skeleton"));
        repository.save(TrackCatalogFixtures.enrichedTrack("sp-full"));

        // when
        long count = repository.countMissingByGroup().getAudio();

        // then
        assertThat(count).isEqualTo(1);
    }
}
