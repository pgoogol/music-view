package com.pgoogol.api;

import com.pgoogol.common.ValidationException;
import com.pgoogol.enrichment.spotify.SpotifyAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Jednorazowe połączenie konta właściciela ze Spotify (D4) — nie jest to system
 * kont aplikacji (D2/D14): logowania do samego music-view nie ma.
 */
@RestController
@RequestMapping("/api/auth/spotify")
@Tag(name = "Auth Spotify", description = "Połączenie konta właściciela (OAuth PKCE — D4)")
public class SpotifyAuthController {

    private final SpotifyAccountService accountService;
    private final SpotifyAccountApiMapper mapper;

    public SpotifyAuthController(SpotifyAccountService accountService,
                                 SpotifyAccountApiMapper mapper) {

        this.accountService = accountService;
        this.mapper = mapper;
    }

    @GetMapping("/login")
    @Operation(summary = "Start logowania — przekierowanie na ekran zgody Spotify",
        description = "Otwórz ten adres w przeglądarce; po zatwierdzeniu zgód Spotify wróci "
            + "na /api/auth/spotify/callback i konto zostanie zapisane.")
    public ResponseEntity<Void> login() {

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(accountService.authorizationUri())
            .build();
    }

    @GetMapping("/callback")
    @Operation(summary = "Powrót ze Spotify — wymiana kodu na tokeny")
    public SpotifyAccountResponse callback(@RequestParam(required = false) String code,
                                           @RequestParam(required = false) String state,
                                           @RequestParam(required = false) String error) {

        if (Objects.nonNull(error)) {
            throw new ValidationException("SPOTIFY_AUTH_DENIED",
                "Spotify odrzucił logowanie: %s".formatted(error));
        }
        if (Objects.isNull(code)) {
            throw new ValidationException("SPOTIFY_AUTH_CODE_MISSING",
                "Brak parametru code w powrocie ze Spotify");
        }
        return mapper.toResponse(accountService.completeAuthorization(code, state));
    }

    @GetMapping("/status")
    @Operation(summary = "Czy konto Spotify jest połączone")
    public SpotifyAccountResponse status() {
        return mapper.toResponse(accountService.status());
    }
}
