package com.pgoogol.catalog;

import com.pgoogol.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teksty utworów (D32): klucz dzielony z utworem katalogu oraz liczenie braków
 * w grupie LYRICS — status rozstrzygnięty ma <b>nie</b> wracać do kolejki.
 * Teksty w testach są wymyślone na potrzeby testu.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TrackLyricsRepositoryTest {

    private static final String LYRICS = "Pierwszy wers testowego tekstu\nDrugi wers testowego tekstu";

    @Autowired
    private TrackLyricsRepository repository;

    @Autowired
    private TrackCatalogRepository trackCatalogRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void save_whenTranslationGiven_sharesPrimaryKeyWithTrack() {

        // given
        entityManager.persist(TrackCatalogFixtures.skeletonTrack("sp-1"));
        TrackLyrics lyrics = new TrackLyrics("sp-1", LyricsStatus.TRANSLATED);
        lyrics.setLrclibId(3396226L);
        lyrics.setSourceLanguage("hiszpański");
        lyrics.setOriginalLyrics(LYRICS);
        lyrics.setTranslationPl("Tłumaczenie testowe");
        lyrics.setInterpretationPl("Interpretacja testowa.");
        lyrics.setFetchedAt(Instant.parse("2026-08-09T10:00:00Z"));
        lyrics.setTranslatedAt(Instant.parse("2026-08-09T10:00:05Z"));
        lyrics.setModelUsed("test-model");
        lyrics.setPromptVersion(1);

        // when
        repository.saveAndFlush(lyrics);
        entityManager.clear();

        // then
        assertThat(repository.findById("sp-1")).hasValueSatisfying(saved -> {
            assertThat(saved.getStatus()).isEqualTo(LyricsStatus.TRANSLATED);
            assertThat(saved.getLrclibId()).isEqualTo(3396226L);
            assertThat(saved.getSourceLanguage()).isEqualTo("hiszpański");
            assertThat(saved.getOriginalLyrics()).isEqualTo(LYRICS);
            assertThat(saved.getTranslationPl()).isEqualTo("Tłumaczenie testowe");
            assertThat(saved.getInterpretationPl()).isEqualTo("Interpretacja testowa.");
            assertThat(saved.getModelUsed()).isEqualTo("test-model");
            assertThat(saved.getPromptVersion()).isEqualTo(1);
            assertThat(saved.isResolved()).isTrue();
        });
    }

    @Test
    void countMissingByGroup_countsTracksWithoutResolvedLyrics() {

        // given
        persistWithLyrics("sp-translated", LyricsStatus.TRANSLATED);
        persistWithLyrics("sp-not-found", LyricsStatus.NOT_FOUND);
        persistWithLyrics("sp-instrumental", LyricsStatus.INSTRUMENTAL);
        // tekst pobrany, tłumaczenie nie powstało — utwór wraca do kolejki
        persistWithLyrics("sp-fetched", LyricsStatus.FETCHED);
        // utwór bez wiersza w track_lyrics też jest brakiem
        entityManager.persist(TrackCatalogFixtures.skeletonTrack("sp-nowy"));
        entityManager.flush();

        // when
        TrackCatalogRepository.MissingCounts counts = trackCatalogRepository.countMissingByGroup();

        // then
        assertThat(counts.getLyrics()).isEqualTo(2);
    }

    @Test
    void countMissingForFields_whenLyricsSelected_countsOnlyUnresolved() {

        // given
        persistWithLyrics("sp-translated", LyricsStatus.TRANSLATED);
        persistWithLyrics("sp-fetched", LyricsStatus.FETCHED);
        entityManager.flush();

        // when
        long lyricsOnly = trackCatalogRepository.countMissingForFields(false, false, false, true);

        // then
        assertThat(lyricsOnly).isEqualTo(1);
    }

    private void persistWithLyrics(String spotifyId, LyricsStatus status) {

        entityManager.persist(TrackCatalogFixtures.skeletonTrack(spotifyId));
        TrackLyrics lyrics = new TrackLyrics(spotifyId, status);
        if (status != LyricsStatus.NOT_FOUND && status != LyricsStatus.INSTRUMENTAL) {
            lyrics.setOriginalLyrics(LYRICS);
        }
        entityManager.persist(lyrics);
    }
}
