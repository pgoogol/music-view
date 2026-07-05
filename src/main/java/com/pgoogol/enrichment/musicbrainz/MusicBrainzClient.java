package com.pgoogol.enrichment.musicbrainz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pgoogol.common.ExternalServiceException;
import com.pgoogol.common.ratelimit.ApiCallGuard;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * MusicBrainz wyłącznie do lookupu ISRC→MBID (D6) — klucz do AcousticBrainz.
 * Twardy throttle 1 req/s (wymóg MB, niekonfigurowalny) + trwały cache w bazie
 * (D18) z negative cache'em; wymagany User-Agent z kontaktem (D14).
 */
@Component
public class MusicBrainzClient {

    private static final int HARD_REQUESTS_PER_SECOND = 1;

    private final RestClient restClient;
    private final MusicBrainzIsrcCacheRepository cacheRepository;
    private final ApiCallGuard guard;

    public MusicBrainzClient(RestClient.Builder restClientBuilder,
                             MusicBrainzProperties properties,
                             MusicBrainzIsrcCacheRepository cacheRepository) {

        this.restClient = restClientBuilder.clone()
            .baseUrl(properties.baseUrl())
            .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
            .defaultHeader(HttpHeaders.ACCEPT, "application/json")
            .build();
        this.cacheRepository = cacheRepository;
        this.guard = ApiCallGuard.of("musicbrainz", HARD_REQUESTS_PER_SECOND);
    }

    public Optional<String> lookupMbid(String isrc) {

        Objects.requireNonNull(isrc, "isrc");
        Optional<MusicBrainzIsrcCache> cached = cacheRepository.findById(isrc);
        if (cached.isPresent()) {
            return Optional.ofNullable(cached.get().getMbid());
        }
        Optional<String> mbid = fetchMbid(isrc);
        cacheRepository.save(new MusicBrainzIsrcCache(isrc, mbid.orElse(null)));
        return mbid;
    }

    private Optional<String> fetchMbid(String isrc) {

        return guard.execute(() -> {
            try {
                IsrcResponse response = restClient.get()
                    .uri("/ws/2/isrc/{isrc}?fmt=json", isrc)
                    .retrieve()
                    .body(IsrcResponse.class);
                return firstRecordingId(response);
            } catch (HttpClientErrorException.NotFound ex) {
                return Optional.<String>empty();
            } catch (HttpServerErrorException | ResourceAccessException ex) {
                throw new ExternalServiceException("MUSICBRAINZ_UNAVAILABLE",
                    "MusicBrainz API niedostępne", ex);
            }
        });
    }

    private Optional<String> firstRecordingId(IsrcResponse response) {

        return Optional.ofNullable(response)
            .map(IsrcResponse::recordings)
            .orElse(List.of())
            .stream()
            .findFirst()
            .map(Recording::id);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IsrcResponse(List<Recording> recordings) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Recording(String id) {

    }
}
