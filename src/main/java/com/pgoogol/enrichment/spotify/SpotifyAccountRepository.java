package com.pgoogol.enrichment.spotify;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpotifyAccountRepository extends JpaRepository<SpotifyAccount, Short> {

    default Optional<SpotifyAccount> findConnected() {
        return findById(SpotifyAccount.SINGLE_ROW_ID);
    }
}
