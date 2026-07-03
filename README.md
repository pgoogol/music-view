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

## Stan projektu

Faza planowania zakończona — implementacja startuje od **Etapu 0** (bootstrap) i **M1.1**
(schemat danych) zgodnie z [docs/PLAN.md](docs/PLAN.md).
