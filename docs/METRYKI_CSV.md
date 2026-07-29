# Metryki utworów z pliku CSV (D24)

Ręczne wejście dla cech audio, których nie oddaje już API Spotify. Plik uzupełnia
utwory **już obecne w katalogu** — biblioteki nie zakłada (to robi import playlist
ze Spotify, D6).

## Jak wgrać

- **UI:** zakładka *Import* → panel „Metryki utworów (CSV)" → wybierz plik → *Wgraj metryki*.
- **API:** `POST /api/ingest/metrics`, multipart, pole `file`.

```bash
curl -F file=@metryki.csv http://localhost:8080/api/ingest/metrics
```

Odpowiedź: `applied` (utwory uzupełnione), `matchedByIsrc` (wiersze dopasowane dopiero
po ISRC), `skipped` (wiersze bez utworu w katalogu), `failed` (wiersze odrzucone przez
parser) — dwa ostatnie z numerem wiersza i powodem.

Ponowny import **nadpisuje** metryki utworu; wiersz jest liczony raz, nawet jeśli
powtarza się w pliku.

## Kolumny

Nagłówki rozpoznawane bez względu na wielkość liter; kolumn spoza tabeli plik może mieć
dowolnie dużo — są ignorowane. Wymagane jest **co najmniej jedno** pole identyfikujące
utwór i **co najmniej jedna** metryka, inaczej import kończy się błędem `CSV_MISSING_COLUMNS`.

| Kolumna (aliasy) | Trafia do | Uwagi |
|---|---|---|
| `Spotify Track Id`, `Spotify Id`, `Track Id`, `Track URI`, `URI` | dopasowanie | samo id (22 znaki), `spotify:track:…` albo link `open.spotify.com/track/…` |
| `ISRC` | dopasowanie awaryjne | używane, gdy nie ma id albo nie ma takiego utworu; wielkość liter i myślniki bez znaczenia |
| `BPM`, `Tempo` | `bpm` + `bpm_source=MANUAL` | 20–400; w katalogu po korekcie half-time (§16.1) |
| `Key`, `Musical Key` | `musical_key` | `G minor`, `G`, `F#/G♭ minor`, `C♯/D♭` → normalizowane do `G minor` / `G major` / `F# minor` / `C# major`; sama liczba (pitch class) jest ignorowana |
| `Camelot`, `Camelot Key` | `camelot` | `1A`–`12B`, do miksowania harmonicznego |
| `Dance`, `Danceability` | `danceability` | |
| `Energy` | `energy` | w katalogu jako `low`/`medium`/`high` |
| `Valence`, `Happiness` | `valence` | |
| `Acoustic`, `Acousticness` | `acousticness` | |
| `Instrumental`, `Instrumentalness` | `instrumentalness` | |
| `Speech`, `Speechiness` | `speechiness` | |
| `Live`, `Liveness` | `liveness` | |
| `Loud (Db)`, `Loudness` | `loudness_db` | zakres −60…10 dB |
| `Time Signature` | `time_signature` | 1–16 |
| `Genres`, `Parent Genres` | `genre_family` | mapowane na enum D8 (`latin`, `rock`, `pop`, `disco`, `disco_polo`, `electronic`, `hip_hop`); zapisywane tylko, gdy utwór nie ma jeszcze gatunku — potem i tak ustala go LLM |

**Skala cech:** wartość powyżej 1 jest traktowana jak procent (`89` → `0.890`), wartość
0–1 jako ułamek (`0.89` → `0.890`). Przecinek dziesiętny, znak `%` i jednostki są
tolerowane. Wartość spoza sensownego zakresu (np. BPM `0`) jest traktowana jak brak danych.

Kolumny z metadanymi (`Song`, `Artist`, `Album`, `Popularity`, `Duration`, `Label`,
`Explicit`, `Added At`, …) są **pomijane** — metadane pochodzą ze Spotify, a warstwa
opisowa (styl, o czym utwór, opis) z LLM-a (D11).

## Przykład

```csv
#,Song,Artist,BPM,Camelot,Energy,Dance,Valence,Loud (Db),Key,Time Signature,Genres,Spotify Track Id,ISRC
1,La Lámpara,Alain Pérez,96,6A,89,66,88,-6,G minor,4,"timba, salsa",2c7nzxJYmPtkimDdrhcfJx,ES71G2337397
```

W `manual_metrics` ląduje surowy zapis wiersza: `bpm=96.00`, `energy=0.890`,
`danceability=0.660`, `camelot=6A`. W `track_catalog` — `bpm=192` (salsa: korekta
half-time), `tempo_class=VERY_FAST`, `musical_key=G minor`, `energy=high`,
`bpm_source=MANUAL` oraz `genre_family=LATIN`, o ile utwór nie miał jeszcze gatunku.

## Co widać po imporcie

- biblioteka i wyszukiwarka: BPM, tempo, tonacja, energia (filtry i sortowanie działają
  jak dla pozostałych źródeł),
- szuflada utworu → sekcja **Metryki z pliku**: surowe wartości, Camelot, głośność,
  metrum oraz kiedy i z jakiego pliku pochodzą,
- `bpm_source=MANUAL` przy BPM — audyt, skąd wzięła się wartość.
