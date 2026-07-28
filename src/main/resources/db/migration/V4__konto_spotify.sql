-- V4: konto Spotify właściciela (D4/D20) — tabela techniczna poza ERD z docs/PLAN.md.
-- Narzędzie jest jednoosobowe (D2), więc trzymamy dokładnie jeden wiersz o stałym id;
-- ponowne połączenie konta nadpisuje ten sam rekord.

CREATE TABLE spotify_account (
    id              smallint     PRIMARY KEY,
    spotify_user_id varchar(64)  NOT NULL,
    display_name    varchar(255),
    access_token    text         NOT NULL,
    refresh_token   text         NOT NULL,
    expires_at      timestamptz  NOT NULL,
    scopes          varchar(500),
    connected_at    timestamptz  NOT NULL
);
