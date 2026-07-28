package com.pgoogol.enrichment.spotify;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Wyzwanie PKCE (RFC 7636) dla logowania właściciela: losowy {@code code_verifier}
 * i jego skrót {@code S256} jako {@code code_challenge}. Dzięki PKCE wymiana kodu
 * na token nie wymaga client_secret po stronie przeglądarki (D4).
 */
@Component
public class SpotifyPkce {

    private static final int VERIFIER_BYTES = 64;
    private static final int STATE_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public Challenge generate() {

        String verifier = randomToken(VERIFIER_BYTES);
        return new Challenge(verifier, sha256(verifier), randomToken(STATE_BYTES));
    }

    private String randomToken(int bytes) {

        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return encoder.encodeToString(buffer);
    }

    private String sha256(String verifier) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return encoder.encodeToString(digest.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Brak SHA-256 w środowisku uruchomieniowym", ex);
        }
    }

    public record Challenge(String verifier, String challenge, String state) {

    }
}
