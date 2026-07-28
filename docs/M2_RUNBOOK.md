# Etap 2 — runbook: od playlisty do gotowego setu na Spotify

Przebieg DoD Etapu 2 na realnym koncie. Wymagania: `.env` z `SPOTIFY_CLIENT_ID`
i `SPOTIFY_CLIENT_SECRET`, aplikacja zarejestrowana w
[dashboardzie Spotify](https://developer.spotify.com/dashboard) z **Redirect URI**
ustawionym dokładnie na `http://127.0.0.1:8080/api/auth/spotify/callback`
(albo na wartość, którą podasz w `SPOTIFY_REDIRECT_URI`).

## 1. Start

```bash
docker compose up -d
set -a && source .env && set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm install && npm run dev   # http://localhost:5173
```

## 2. Połączenie konta (M2.2)

Otwórz w przeglądarce `http://localhost:8080/api/auth/spotify/login` (albo
przycisk „Połącz konto" w panelu *Konto Spotify*). Po zatwierdzeniu zgód Spotify
wraca na `/callback`, a aplikacja zapisuje konto i tokeny w bazie.

```bash
curl -s http://localhost:8080/api/auth/spotify/status
# {"connected":true,"spotifyUserId":"…","displayName":"…", …}  — bez tokenów (D20)
```

Zgody nadawane raz: `playlist-read-private`, `playlist-read-collaborative`
(import) oraz `playlist-modify-private`, `playlist-modify-public` (eksport).

## 3. Import playlist (M2.1 / M2.2)

```bash
# pojedyncza playlista — własna albo cudza (link, URI albo samo id)
curl -X POST -H 'Content-Type: application/json' \
  -d '{"url":"https://open.spotify.com/playlist/37i9dQZF1DX10zKzsJ2jva"}' \
  http://localhost:8080/api/ingest/playlist

# wszystkie własne playlisty konta (tryb C) — obserwowane cudze są pomijane
curl -X POST http://localhost:8080/api/ingest/my-playlists
```

W raporcie: `tracks` (unikalne utwory), `imported` (nowe w bibliotece),
`alreadyExisted`, `skipped` (pliki lokalne, odcinki podcastów, utwory usunięte).

## 4. Wzbogacenie zaimportowanych utworów

Import playlisty wypełnia grupę METADATA od ręki, więc zwykle wystarczy:

```bash
curl -X POST -H 'Content-Type: application/json' \
  -d '{"scope":"MISSING","fields":["AUDIO","AI"]}' http://localhost:8080/api/enrich
```

Bez pól AUDIO/AI sloty wieczoru zostaną puste — kaskada D9 potrzebuje bpm i energii.

## 5. Planowanie setu (M2.3)

W panelu *Playlisty*: utwórz set, zaznacz utwory w tabeli biblioteki i kliknij
„Dodaj zaznaczone", potem ułóż kolejność przeciąganiem. Slot (rozgrzewka / środek /
szczyt / zamknięcie / przerwa) liczy się z bpm + energy + genre_family (D9/D21);
gwiazdka `✱` oznacza ręczny override z `library_entry.dj_slot_override`
(ustawiany w szczegółach utworu).

Z konsoli to samo:

```bash
curl -X POST -H 'Content-Type: application/json' \
  -d '{"name":"Wesele Kowalskich"}' http://localhost:8080/api/playlists
curl -X POST -H 'Content-Type: application/json' \
  -d '{"spotifyId":"4uLU6hMCjMI75M1A2tKUQC"}' http://localhost:8080/api/playlists/1/tracks
curl -X PUT -H 'Content-Type: application/json' \
  -d '{"spotifyIds":["…","…"]}' http://localhost:8080/api/playlists/1/tracks
```

`PUT` wymaga permutacji obecnego składu — pominięty utwór to błąd
`PLAYLIST_ORDER_MISMATCH`, nie ciche usunięcie (D21).

## 6. Eksport na Spotify (M2.4)

```bash
curl -X POST http://localhost:8080/api/playlists/1/export-to-spotify
# {"spotifyPlaylistId":"…","exportedTracks":42,"created":true,
#  "spotifyUrl":"https://open.spotify.com/playlist/…"}
```

Pierwszy eksport zakłada playlistę **prywatną** na koncie właściciela i zapamiętuje
jej id; kolejne nadpisują zawartość tej samej playlisty, więc kolejność na Spotify
zawsze odpowiada setowi w music-view. Utwory idą partiami po 100 URI.

## 7. Typowe potknięcia

| Objaw | Przyczyna / rozwiązanie |
|---|---|
| `SPOTIFY_NOT_CONNECTED` | brak połączonego konta — przejdź krok 2 |
| `SPOTIFY_AUTH_EXPIRED` | logowanie starsze niż 10 min albo restart aplikacji w jego trakcie — otwórz `/login` ponownie |
| `SPOTIFY_AUTH_REJECTED` | Redirect URI w dashboardzie różni się od `SPOTIFY_REDIRECT_URI` (musi być znak w znak) |
| `PLAYLIST_URL_INVALID` | wklejony link nie prowadzi do playlisty (np. do albumu) |
| `PLAYLIST_EMPTY` | eksport pustego setu — najpierw dodaj utwory |
| sloty puste na całej playliście | utwory niewzbogacone — uruchom krok 4 |
