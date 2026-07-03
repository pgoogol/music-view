# music-view

Osobiste narzędzie DJ-a do zarządzania biblioteką muzyczną (marka: **Sabor Latino**,
silnik genre-agnostyczny). Wzbogaca utwory o metadane i cechy audio (BPM, danceability,
energia, tempo), warstwę opisową (o czym utwór, opis PL, styl/gatunek) oraz prywatne uwagi
DJ-a; pozwala przeszukiwać bibliotekę i planować sety/playlisty z eksportem na Spotify.

**Stack:** Spring Boot 3.x (Java 21, pakiet `com.pgoogol`) + Spring Batch + PostgreSQL +
React (Vite). Źródła danych: Spotify, MusicBrainz (MBID), AcousticBrainz (dump),
Deezer (BPM), dowolny model LLM (analiza AI — provider konfigurowalny, do wyboru).

## Dokumentacja

| Dokument | Zawartość |
|---|---|
| [docs/KONCEPT.md](docs/KONCEPT.md) | Oryginalny koncept projektu (specyfikacja źródłowa) |
| [docs/DECYZJE.md](docs/DECYZJE.md) | Rejestr decyzji projektowych (ADR-lite) — obowiązujące rozstrzygnięcia |
| [docs/PLAN.md](docs/PLAN.md) | Rozbicie pracy: etapy, kamienie milowe, zależności, ryzyka |

## Szybki start

```bash
docker compose up -d     # Postgres 16 (profil local łączy się z tą bazą)
./mvnw verify            # build + testy
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Sekrety: skopiuj `.env.example` do `.env` i uzupełnij (plik `.env` nie trafia do repo — D14).

## Stan projektu

**Zakończone: Etap 0, M1.1 (schemat — zamrożony, D17), M1.2 (import CSV)** —
`POST /api/ingest/file` przyjmuje eksport Exportify/własny CSV: dedup po
`spotify_id`, szkielet w `track_catalog` + wpis `library_entry` (source=FILE),
raport `{imported, alreadyExisted, failed[]}`. Następne kamienie: **M1.3**
(klienci źródeł) ∥ **M1.5** (LLM) zgodnie z [docs/PLAN.md](docs/PLAN.md).
