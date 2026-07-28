package com.pgoogol.enrichment.spotify;

import com.pgoogol.common.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Połączenie konta właściciela ze Spotify (D4): start logowania z wyzwaniem PKCE,
 * domknięcie po powrocie z {@code /callback} i wydawanie ważnego tokenu użytkownika
 * (odświeżanego w tle). Konto jest jedno (D2), więc rozpoczęte logowanie trzymamy
 * w pamięci — po restarcie aplikacji trzeba je po prostu powtórzyć.
 */
@Service
public class SpotifyAccountService {

    private static final Duration AUTHORIZATION_TTL = Duration.ofMinutes(10);
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(SpotifyAccountService.class);

    private final SpotifyProperties properties;
    private final SpotifyOAuthClient oauthClient;
    private final SpotifyPkce pkce;
    private final SpotifyAccountRepository accountRepository;
    private final AtomicReference<PendingAuthorization> pending = new AtomicReference<>();

    public SpotifyAccountService(SpotifyProperties properties, SpotifyOAuthClient oauthClient,
                                 SpotifyPkce pkce, SpotifyAccountRepository accountRepository) {

        this.properties = properties;
        this.oauthClient = oauthClient;
        this.pkce = pkce;
        this.accountRepository = accountRepository;
    }

    /** Adres zgody Spotify; równolegle zapamiętuje verifier i state do weryfikacji powrotu. */
    public URI authorizationUri() {

        requireClientConfigured();
        SpotifyPkce.Challenge challenge = pkce.generate();
        pending.set(new PendingAuthorization(challenge.state(), challenge.verifier(), Instant.now()));
        return UriComponentsBuilder.fromUriString(properties.authUrl())
            .path("/authorize")
            .queryParam("client_id", properties.clientId())
            .queryParam("response_type", "code")
            .queryParam("redirect_uri", properties.redirectUri())
            .queryParam("code_challenge_method", "S256")
            .queryParam("code_challenge", challenge.challenge())
            .queryParam("state", challenge.state())
            .queryParam("scope", properties.scopes())
            .build()
            .encode()
            .toUri();
    }

    @Transactional
    public SpotifyAccountStatus completeAuthorization(String code, String state) {

        Objects.requireNonNull(code, "code");
        String verifier = consumePending(state);
        SpotifyTokens tokens = oauthClient.exchangeCode(code, verifier);
        SpotifyUserProfile profile = oauthClient.getCurrentUser(tokens.accessToken());
        SpotifyAccount account = accountRepository.save(new SpotifyAccount(
            profile.spotifyUserId(), profile.displayName(), tokens.accessToken(),
            tokens.refreshToken(), expiresAt(tokens), tokens.scope()));
        log.info("Połączono konto Spotify właściciela: {} ({})",
            account.getSpotifyUserId(), account.getDisplayName());
        return SpotifyAccountStatus.of(account);
    }

    @Transactional(readOnly = true)
    public SpotifyAccountStatus status() {

        return accountRepository.findConnected()
            .map(SpotifyAccountStatus::of)
            .orElseGet(SpotifyAccountStatus::disconnected);
    }

    @Transactional(readOnly = true)
    public Optional<String> connectedUserId() {
        return accountRepository.findConnected().map(SpotifyAccount::getSpotifyUserId);
    }

    /** Ważny token właściciela; wygasły odświeża i zapisuje nowy komplet. */
    @Transactional
    public String userAccessToken() {

        SpotifyAccount account = accountRepository.findConnected()
            .orElseThrow(() -> new ValidationException("SPOTIFY_NOT_CONNECTED",
                "Konto Spotify nie jest połączone — otwórz /api/auth/spotify/login"));
        if (!account.isExpiredAt(Instant.now().plus(EXPIRY_MARGIN))) {
            return account.getAccessToken();
        }
        SpotifyTokens tokens = oauthClient.refresh(account.getRefreshToken());
        account.refreshed(tokens.accessToken(), tokens.refreshToken(), expiresAt(tokens));
        log.info("Odświeżono token konta Spotify {}", account.getSpotifyUserId());
        return account.getAccessToken();
    }

    private String consumePending(String state) {

        PendingAuthorization authorization = pending.getAndSet(null);
        if (Objects.isNull(authorization) || authorization.isExpired()) {
            throw new ValidationException("SPOTIFY_AUTH_EXPIRED",
                "Logowanie wygasło lub nie zostało rozpoczęte — otwórz /api/auth/spotify/login");
        }
        if (!Objects.equals(authorization.state(), state)) {
            throw new ValidationException("SPOTIFY_AUTH_STATE_MISMATCH",
                "Parametr state nie zgadza się z rozpoczętym logowaniem");
        }
        return authorization.verifier();
    }

    private void requireClientConfigured() {

        if (Objects.toString(properties.clientId(), "").isBlank()) {
            throw new ValidationException("SPOTIFY_CLIENT_NOT_CONFIGURED",
                "Brak SPOTIFY_CLIENT_ID w konfiguracji środowiska");
        }
    }

    private Instant expiresAt(SpotifyTokens tokens) {
        return Instant.now().plusSeconds(tokens.expiresIn());
    }

    private record PendingAuthorization(String state, String verifier, Instant startedAt) {

        boolean isExpired() {
            return Instant.now().isAfter(startedAt.plus(AUTHORIZATION_TTL));
        }
    }
}
