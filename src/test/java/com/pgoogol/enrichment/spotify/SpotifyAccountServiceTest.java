package com.pgoogol.enrichment.spotify;

import com.pgoogol.common.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SpotifyAccountServiceTest {

    private final SpotifyProperties properties = new SpotifyProperties(
        "https://api.spotify.com", "https://accounts.spotify.com", "client-abc", "secret", 8,
        "http://127.0.0.1:8080/api/auth/spotify/callback", "playlist-read-private");

    @Mock
    private SpotifyOAuthClient oauthClient;

    @Mock
    private SpotifyAccountRepository accountRepository;

    private final SpotifyPkce pkce = new SpotifyPkce();

    private SpotifyAccountService service() {
        return new SpotifyAccountService(properties, oauthClient, pkce, accountRepository);
    }

    @Test
    void authorizationUri_whenLoginStarted_pointsAtSpotifyWithPkceChallenge() {

        // when
        URI uri = service().authorizationUri();

        // then
        assertThat(uri.toString())
            .startsWith("https://accounts.spotify.com/authorize")
            .contains("client_id=client-abc")
            .contains("response_type=code")
            .contains("code_challenge_method=S256")
            .contains("code_challenge=")
            .contains("state=")
            .contains("scope=playlist-read-private");
        assertThat(uri.toString()).doesNotContain("secret");
    }

    @Test
    void completeAuthorization_whenStateMatches_savesConnectedAccount() {

        // given
        SpotifyAccountService service = service();
        String state = stateOf(service.authorizationUri());
        given(oauthClient.exchangeCode(anyString(), anyString()))
            .willReturn(new SpotifyTokens("access", "refresh", 3600, "playlist-read-private"));
        given(oauthClient.getCurrentUser("access"))
            .willReturn(new SpotifyUserProfile("dj-pgoogol", "DJ pgoogol"));
        given(accountRepository.save(any(SpotifyAccount.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        // when
        SpotifyAccountStatus status = service.completeAuthorization("kod", state);

        // then
        assertThat(status.connected()).isTrue();
        assertThat(status.spotifyUserId()).isEqualTo("dj-pgoogol");
        assertThat(status.displayName()).isEqualTo("DJ pgoogol");
    }

    @Test
    void completeAuthorization_whenStateDoesNotMatch_rejectsWithoutCallingSpotify() {

        // given
        SpotifyAccountService service = service();
        service.authorizationUri();

        // when + then
        assertThatThrownBy(() -> service.completeAuthorization("kod", "podrobiony-state"))
            .isInstanceOf(ValidationException.class)
            .hasFieldOrPropertyWithValue("errorCode", "SPOTIFY_AUTH_STATE_MISMATCH");
        then(oauthClient).should(never()).exchangeCode(anyString(), anyString());
    }

    @Test
    void completeAuthorization_whenLoginNotStarted_rejectsCallback() {

        // when + then
        assertThatThrownBy(() -> service().completeAuthorization("kod", "state"))
            .isInstanceOf(ValidationException.class)
            .hasFieldOrPropertyWithValue("errorCode", "SPOTIFY_AUTH_EXPIRED");
    }

    @Test
    void userAccessToken_whenTokenStillValid_doesNotCallSpotify() {

        // given
        given(accountRepository.findConnected()).willReturn(Optional.of(
            account("wazny-token", Instant.now().plus(1, ChronoUnit.HOURS))));

        // when
        String token = service().userAccessToken();

        // then
        assertThat(token).isEqualTo("wazny-token");
        then(oauthClient).should(never()).refresh(anyString());
    }

    @Test
    void userAccessToken_whenTokenExpired_refreshesAndKeepsOldRefreshToken() {

        // given — Spotify nie zwrócił nowego refresh tokenu
        SpotifyAccount expired = account("stary-token", Instant.now().minusSeconds(10));
        given(accountRepository.findConnected()).willReturn(Optional.of(expired));
        given(oauthClient.refresh("refresh-token"))
            .willReturn(new SpotifyTokens("swiezy-token", null, 3600, "playlist-read-private"));

        // when
        String token = service().userAccessToken();

        // then
        assertThat(token).isEqualTo("swiezy-token");
        assertThat(expired.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(expired.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void userAccessToken_whenAccountNotConnected_failsWithGuidance() {

        // given
        given(accountRepository.findConnected()).willReturn(Optional.empty());

        // when + then
        assertThatThrownBy(() -> service().userAccessToken())
            .isInstanceOf(ValidationException.class)
            .hasFieldOrPropertyWithValue("errorCode", "SPOTIFY_NOT_CONNECTED");
    }

    private SpotifyAccount account(String accessToken, Instant expiresAt) {

        return new SpotifyAccount("dj-pgoogol", "DJ pgoogol", accessToken, "refresh-token",
            expiresAt, "playlist-read-private");
    }

    private String stateOf(URI authorizationUri) {

        return Arrays.stream(authorizationUri.getQuery().split("&"))
            .filter(param -> param.startsWith("state="))
            .map(param -> param.substring("state=".length()))
            .findFirst()
            .orElseThrow();
    }
}
