package com.pgoogol.enrichment.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pgoogol.common.ValidationException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Objects;

/**
 * Wymiana i odświeżanie tokenów konta właściciela (Authorization Code + PKCE — D4)
 * oraz odczyt profilu {@code /v1/me}. PKCE nie wymaga client_secret — dowodem
 * tożsamości klienta jest {@code code_verifier}.
 */
@Component
public class SpotifyOAuthClient {

    private final RestClient authClient;
    private final RestClient apiClient;
    private final SpotifyProperties properties;
    private final SpotifyApiExecutor executor;

    public SpotifyOAuthClient(RestClient.Builder restClientBuilder, SpotifyProperties properties,
                              SpotifyApiExecutor executor) {

        this.authClient = restClientBuilder.clone().baseUrl(properties.authUrl()).build();
        this.apiClient = restClientBuilder.clone().baseUrl(properties.baseUrl()).build();
        this.properties = properties;
        this.executor = executor;
    }

    public SpotifyTokens exchangeCode(String code, String codeVerifier) {

        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(codeVerifier, "codeVerifier");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri());
        form.add("client_id", properties.clientId());
        form.add("code_verifier", codeVerifier);
        return requestTokens(form, "Nie udało się wymienić kodu autoryzacji na token");
    }

    public SpotifyTokens refresh(String refreshToken) {

        Objects.requireNonNull(refreshToken, "refreshToken");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", properties.clientId());
        return requestTokens(form, "Nie udało się odświeżyć tokenu konta Spotify");
    }

    public SpotifyUserProfile getCurrentUser(String accessToken) {

        Objects.requireNonNull(accessToken, "accessToken");
        UserResponse response = executor.call("profil właściciela", () -> apiClient.get()
            .uri("/v1/me")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .retrieve()
            .body(UserResponse.class));
        return new SpotifyUserProfile(response.id(), response.displayName());
    }

    private SpotifyTokens requestTokens(MultiValueMap<String, String> form, String failureMessage) {

        TokenResponse response = executor.call("token konta", () -> {
            try {
                return authClient.post()
                    .uri("/api/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            } catch (HttpClientErrorException.BadRequest ex) {
                // 400 z tego endpointu to zawsze wina wejścia (zużyty/zły kod,
                // cofnięta zgoda) — ponawianie nic nie da
                throw new ValidationException("SPOTIFY_AUTH_REJECTED", failureMessage);
            }
        });
        return new SpotifyTokens(response.accessToken(), response.refreshToken(),
            response.expiresIn(), response.scope());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("refresh_token") String refreshToken,
                                 @JsonProperty("expires_in") long expiresIn,
                                 String scope) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UserResponse(String id, @JsonProperty("display_name") String displayName) {

    }
}
