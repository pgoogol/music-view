package com.pgoogol.catalog;

import com.pgoogol.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Metryki wgrane ręcznie (D24) — klucz dzielony z utworem katalogu i odczyt
 * partiami, tak jak czyta je job wzbogacania.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ManualMetricsRepositoryTest {

    @Autowired
    private ManualMetricsRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void save_whenMetricsGiven_sharesPrimaryKeyWithTrack() {

        // given
        TrackCatalog track = TrackCatalogFixtures.skeletonTrack("sp-1");
        entityManager.persist(track);
        ManualMetrics metrics = new ManualMetrics(track);
        metrics.setBpm(new BigDecimal("96.00"));
        metrics.setMusicalKey("G minor");
        metrics.setCamelot("6A");
        metrics.setEnergy(new BigDecimal("0.890"));
        metrics.setLoudnessDb(new BigDecimal("-6.00"));
        metrics.setTimeSignature(4);
        metrics.setSource("metryki.csv");

        // when
        repository.saveAndFlush(metrics);
        entityManager.clear();

        // then
        assertThat(repository.findById("sp-1")).hasValueSatisfying(saved -> {
            assertThat(saved.getSpotifyId()).isEqualTo("sp-1");
            assertThat(saved.getBpm()).isEqualByComparingTo("96.00");
            assertThat(saved.getMusicalKey()).isEqualTo("G minor");
            assertThat(saved.getCamelot()).isEqualTo("6A");
            assertThat(saved.getEnergy()).isEqualByComparingTo("0.890");
            assertThat(saved.getLoudnessDb()).isEqualByComparingTo("-6.00");
            assertThat(saved.getTimeSignature()).isEqualTo(4);
            assertThat(saved.getSource()).isEqualTo("metryki.csv");
            assertThat(saved.getImportedAt()).isNotNull();
        });
    }

    @Test
    void findBySpotifyIdIn_whenBatchOfTracks_returnsOnlyThoseWithMetrics() {

        // given
        TrackCatalog withMetrics = TrackCatalogFixtures.skeletonTrack("sp-2");
        TrackCatalog withoutMetrics = TrackCatalogFixtures.skeletonTrack("sp-3");
        entityManager.persist(withMetrics);
        entityManager.persist(withoutMetrics);
        repository.saveAndFlush(new ManualMetrics(withMetrics));
        entityManager.clear();

        // when
        List<ManualMetrics> found = repository.findBySpotifyIdIn(List.of("sp-2", "sp-3"));

        // then
        assertThat(found).singleElement()
            .satisfies(metrics -> assertThat(metrics.getSpotifyId()).isEqualTo("sp-2"));
    }
}
