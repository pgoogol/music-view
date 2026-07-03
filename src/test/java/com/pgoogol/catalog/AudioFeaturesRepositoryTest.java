package com.pgoogol.catalog;

import com.pgoogol.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class AudioFeaturesRepositoryTest {

    @Autowired
    private AudioFeaturesRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByTrackSpotifyId_whenFeaturesExist_returnsFeatures() {

        // given
        TrackCatalog track = TrackCatalogFixtures.skeletonTrack("sp-1");
        entityManager.persist(track);
        AudioFeatures features = new AudioFeatures("f6d0c9a2-1111-2222-3333-444455556666", track);
        features.setBpm(new BigDecimal("92.00"));
        features.setMusicalKey("A minor");
        features.setDanceability(new BigDecimal("1.150"));
        repository.saveAndFlush(features);
        entityManager.clear();

        // when
        Optional<AudioFeatures> found = repository.findByTrackSpotifyId("sp-1");

        // then
        assertThat(found).hasValueSatisfying(f -> {
            assertThat(f.getMbid()).isEqualTo("f6d0c9a2-1111-2222-3333-444455556666");
            assertThat(f.getBpm()).isEqualByComparingTo(new BigDecimal("92.00"));
            assertThat(f.getMusicalKey()).isEqualTo("A minor");
            assertThat(f.getDanceability()).isEqualByComparingTo(new BigDecimal("1.150"));
        });
    }
}
