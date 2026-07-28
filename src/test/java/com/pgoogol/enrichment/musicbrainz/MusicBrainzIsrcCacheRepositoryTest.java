package com.pgoogol.enrichment.musicbrainz;

import com.pgoogol.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class MusicBrainzIsrcCacheRepositoryTest {

    @Autowired
    private MusicBrainzIsrcCacheRepository repository;

    @Test
    void findById_whenHitCached_returnsMbid() {

        // given
        repository.saveAndFlush(
            new MusicBrainzIsrcCache("USSD11300483", "9d444787-3f25-4c16-9261-597b9ab021cc"));

        // when
        Optional<MusicBrainzIsrcCache> found = repository.findById("USSD11300483");

        // then
        assertThat(found).hasValueSatisfying(cache -> {
            assertThat(cache.getMbid()).isEqualTo("9d444787-3f25-4c16-9261-597b9ab021cc");
            assertThat(cache.getResolvedAt()).isNotNull();
        });
    }

    @Test
    void findById_whenMissCached_returnsEntryWithNullMbid() {

        // given — negative cache (D18): brak wyniku też jest zapamiętywany
        repository.saveAndFlush(new MusicBrainzIsrcCache("PLXXX0000000", null));

        // when
        Optional<MusicBrainzIsrcCache> found = repository.findById("PLXXX0000000");

        // then
        assertThat(found).hasValueSatisfying(cache -> assertThat(cache.getMbid()).isNull());
    }
}
