# Security

> Zaadaptowane z zewnętrznego rulesetu (D16). Zakres mocno okrojony: aplikacja jest
> lokalnym narzędziem jednego DJ-a **bez auth** (D2/D14) — sekcje Spring Security
> (SecurityFilterChain, method security), JWT/OAuth2 logowania, hasła/BCrypt i nagłówki
> HTTP z oryginalnego rulesetu **nie obowiązują** i nie należy ich implementować.
> Jedyny OAuth w projekcie to jednorazowe połączenie konta Spotify właściciela (D4).

## General
- Never log sensitive data: tokens, API keys, dane osobowe
- Never store secrets in code, `application*.yml`, or tests — use environment variables /
  local `.env` (D14; w repo tylko `.env.example`)
- Always sanitize user input before passing to SQL, file paths, or external commands
  (dotyczy m.in. importu CSV: traktuj zawartość pliku jako niezaufaną)
- Klucz/token, który pojawił się jawnie (czat, log, commit) → spalony, wymaga rotacji

## Spotify OAuth tokens (D4)
- Access/refresh tokeny przechowuj server-side (w bazie), nigdy w repo ani w logach
- Refresh tokenów nie umieszczaj w odpowiedziach API frontu

## Input validation
- Use `@Valid` / `@Validated` on all controller method parameters that accept request bodies
- Define validation constraints on the DTO, not in service code
- Return `400 Bad Request` for validation failures — never `500`
- Use `@Pattern`, `@Size`, `@NotBlank` — avoid writing custom validators for things Bean Validation covers

## Actuator
- Expose only `health,info` (lokalnie ewentualnie `metrics`) — never expose full `/actuator`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
```
