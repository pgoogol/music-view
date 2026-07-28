package com.pgoogol.api;

import com.pgoogol.TestcontainersConfiguration;
import com.pgoogol.enrichment.spotify.SpotifyAccount;
import com.pgoogol.enrichment.spotify.SpotifyAccountRepository;
import com.pgoogol.enrichment.spotify.SpotifyOAuthClient;
import com.pgoogol.enrichment.spotify.SpotifyTokens;
import com.pgoogol.enrichment.spotify.SpotifyUserProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Przepływ połączenia konta właściciela (DoD M2.2): start logowania → powrót
 * z kodem → zapis konta. Serwer autoryzacji Spotify zamockowany — jego kontrakt
 * pokrywa SpotifyOAuthClientTest na nagranych odpowiedziach.
 */
@SpringBootTest(properties = {
    "clients.spotify.client-id=test-client-id",
    "clients.spotify.auth-url=https://accounts.spotify.com"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SpotifyAuthApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpotifyAccountRepository accountRepository;

    @MockitoBean
    private SpotifyOAuthClient oauthClient;

    @AfterEach
    void cleanDatabase() {
        accountRepository.deleteAll();
    }

    @Test
    void status_whenAccountNotConnected_reportsDisconnected() throws Exception {

        // when + then
        mockMvc.perform(get("/api/auth/spotify/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(false))
            .andExpect(jsonPath("$.spotifyUserId").doesNotExist());
    }

    @Test
    void login_whenStarted_redirectsToSpotifyConsentScreen() throws Exception {

        // when + then
        mockMvc.perform(get("/api/auth/spotify/login"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location",
                org.hamcrest.Matchers.startsWith("https://accounts.spotify.com/authorize")))
            .andExpect(header().string("Location",
                org.hamcrest.Matchers.containsString("code_challenge_method=S256")));
    }

    @Test
    void callback_whenCodeReturned_savesAccountAndReportsConnected() throws Exception {

        // given
        given(oauthClient.exchangeCode(anyString(), anyString()))
            .willReturn(new SpotifyTokens("access", "refresh", 3600, "playlist-read-private"));
        given(oauthClient.getCurrentUser("access"))
            .willReturn(new SpotifyUserProfile("dj-pgoogol", "DJ pgoogol"));
        String state = startLoginAndReadState();

        // when + then
        mockMvc.perform(get("/api/auth/spotify/callback")
                .param("code", "kod-zgody").param("state", state))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(true))
            .andExpect(jsonPath("$.displayName").value("DJ pgoogol"))
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(jsonPath("$.refreshToken").doesNotExist());

        assertThat(accountRepository.findConnected()).hasValueSatisfying(account -> {
            assertThat(account.getSpotifyUserId()).isEqualTo("dj-pgoogol");
            assertThat(account.getRefreshToken()).isEqualTo("refresh");
            assertThat(account.getId()).isEqualTo(SpotifyAccount.SINGLE_ROW_ID);
        });

        mockMvc.perform(get("/api/auth/spotify/status"))
            .andExpect(jsonPath("$.connected").value(true));
    }

    @Test
    void callback_whenUserDeniedConsent_returns400() throws Exception {

        // when + then
        mockMvc.perform(get("/api/auth/spotify/callback").param("error", "access_denied"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("SPOTIFY_AUTH_DENIED"));
    }

    @Test
    void callback_whenStateForged_returns400AndLeavesAccountDisconnected() throws Exception {

        // given
        startLoginAndReadState();

        // when + then
        mockMvc.perform(get("/api/auth/spotify/callback")
                .param("code", "kod").param("state", "podrobiony"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("SPOTIFY_AUTH_STATE_MISMATCH"));
        assertThat(accountRepository.findConnected()).isEmpty();
    }

    private String startLoginAndReadState() throws Exception {

        MvcResult result = mockMvc.perform(get("/api/auth/spotify/login")).andReturn();
        String location = result.getResponse().getHeader("Location");
        return Arrays.stream(URI.create(location).getQuery().split("&"))
            .filter(param -> param.startsWith("state="))
            .map(param -> param.substring("state=".length()))
            .findFirst()
            .orElseThrow();
    }
}
