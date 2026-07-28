package com.pgoogol.enrichment.spotify;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.pgoogol.WireMockRestClients;
import com.pgoogol.common.ValidationException;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class SpotifyOAuthClientTest {

    private static final String TOKENS_JSON = """
        {
          "access_token": "user-access-token",
          "refresh_token": "user-refresh-token",
          "expires_in": 3600,
          "scope": "playlist-read-private playlist-modify-private"
        }
        """;

    @Test
    void exchangeCode_whenCodeValid_sendsPkceFormAndReturnsTokens(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(post(urlPathEqualTo("/api/token")).willReturn(okJson(TOKENS_JSON)));

        // when
        SpotifyTokens tokens = oauthClient(wireMock).exchangeCode("kod-zgody", "weryfikator-pkce");

        // then
        assertThat(tokens).isEqualTo(new SpotifyTokens("user-access-token", "user-refresh-token",
            3600, "playlist-read-private playlist-modify-private"));
        verify(postRequestedFor(urlPathEqualTo("/api/token"))
            .withRequestBody(containing("grant_type=authorization_code"))
            .withRequestBody(containing("code=kod-zgody"))
            .withRequestBody(containing("code_verifier=weryfikator-pkce"))
            .withRequestBody(containing("client_id=" + SpotifyTestProperties.CLIENT_ID)));
    }

    @Test
    void exchangeCode_whenSpotifyRejectsCode_throwsValidationWithoutRetry(
            WireMockRuntimeInfo wireMock) {

        // given — zużyty lub podrobiony kod: 400 nie jest ponawiane (D16)
        stubFor(post(urlPathEqualTo("/api/token")).willReturn(badRequest()));
        SpotifyOAuthClient client = oauthClient(wireMock);

        // when + then
        assertThatThrownBy(() -> client.exchangeCode("zly-kod", "weryfikator"))
            .isInstanceOf(ValidationException.class)
            .hasFieldOrPropertyWithValue("errorCode", "SPOTIFY_AUTH_REJECTED");
        verify(1, postRequestedFor(urlPathEqualTo("/api/token")));
    }

    @Test
    void refresh_whenTokenRefreshed_sendsRefreshGrant(WireMockRuntimeInfo wireMock) {

        // given — Spotify nie musi zwracać nowego refresh tokenu
        stubFor(post(urlPathEqualTo("/api/token")).willReturn(okJson("""
            {"access_token": "nowy-token", "expires_in": 3600, "scope": "playlist-read-private"}
            """)));

        // when
        SpotifyTokens tokens = oauthClient(wireMock).refresh("stary-refresh-token");

        // then
        assertThat(tokens.accessToken()).isEqualTo("nowy-token");
        assertThat(tokens.refreshToken()).isNull();
        verify(postRequestedFor(urlPathEqualTo("/api/token"))
            .withRequestBody(containing("grant_type=refresh_token"))
            .withRequestBody(containing("refresh_token=stary-refresh-token")));
    }

    @Test
    void getCurrentUser_whenTokenValid_returnsProfile(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(get(urlPathEqualTo("/v1/me")).willReturn(okJson("""
            {"id": "dj-pgoogol", "display_name": "DJ pgoogol", "country": "PL"}
            """)));

        // when
        SpotifyUserProfile profile = oauthClient(wireMock).getCurrentUser("user-access-token");

        // then
        assertThat(profile).isEqualTo(new SpotifyUserProfile("dj-pgoogol", "DJ pgoogol"));
        verify(getRequestedFor(urlPathEqualTo("/v1/me"))
            .withHeader("Authorization", equalTo("Bearer user-access-token")));
    }

    private SpotifyOAuthClient oauthClient(WireMockRuntimeInfo wireMock) {

        SpotifyProperties properties = SpotifyTestProperties.pointingAt(wireMock);
        return new SpotifyOAuthClient(WireMockRestClients.builder(), properties,
            new SpotifyApiExecutor(properties));
    }
}
