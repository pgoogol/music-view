# Error Handling & Logging

> Zaadaptowane z zewnętrznego rulesetu (D16); usunięto sekcję WebFlux/reactive
> (aplikacja jest Spring MVC), a retry/circuit breakery dostosowano do klientów źródeł.

## Exception hierarchy
Define a typed exception hierarchy per domain:
```
AppException (abstract, RuntimeException)
  ├── NotFoundException          → 404
  ├── ValidationException        → 400
  ├── ConflictException          → 409
  └── ExternalServiceException   → 502
```
- Always include a machine-readable `errorCode` field (e.g. `TRACK_NOT_FOUND`) — not just a message string
- Never throw raw `RuntimeException` or `Exception` from business code

## Global exception handler
Use a single `@RestControllerAdvice` class — do not scatter `@ExceptionHandler` across controllers:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NotFoundException ex) {
        log.warn("Resource not found: {}", ex.getErrorCode());
        return ErrorResponse.of(ex.getErrorCode(), ex.getMessage());
    }
}
```

## Error response format
Always return a consistent JSON body for errors:
```json
{
  "errorCode": "TRACK_NOT_FOUND",
  "message": "Track with spotify id 4uLU6hMCjMI75M1A2tKUQC does not exist",
  "timestamp": "2026-07-03T12:00:00Z",
  "traceId": "abc-123"
}
```
- Include `traceId` from MDC / Micrometer tracing
- Never expose stack traces, SQL, or internal class names in the response body

## Logging rules
- Use **SLF4J** with Logback — never `System.out.println`
- Log level guidelines:
  | Level | Use for |
  |---|---|
  | `ERROR` | Unrecoverable failures, data loss risk, external service down |
  | `WARN` | Recoverable issues, retries, fallbacks triggered |
  | `INFO` | Business events (import zakończony, job wzbogacania wystartował/skończył) |
  | `DEBUG` | Request/response details, method entry/exit (dev only) |
  | `TRACE` | Raw SQL, full message payloads (never in production) |
- Always use **parameterized logging** — never string concatenation:
  ```java
  // WRONG
  log.info("Enriching track " + spotifyId + " with fields " + fields);
  // CORRECT
  log.info("Enriching track {} with fields {}", spotifyId, fields);
  ```
- Add `traceId` to MDC at the start of each request (use a filter or interceptor)
- Never log inside a tight loop — use summary logs after the loop (ważne przy batchach ~2500 utworów)
- Nigdy nie loguj sekretów ani tokenów (D14) — patrz [security.md](security.md)

## Transactional error handling
- Mark a method `@Transactional(rollbackFor = Exception.class)` if it must rollback on checked exceptions
- Default Spring behavior rolls back on `RuntimeException` only — be explicit when needed
- Do not swallow exceptions inside `@Transactional` methods — re-throw or wrap them

## Retries & circuit breakers
- Use **Resilience4j** (fundament `common/ratelimit`) — not Spring Retry for new code
- Configure retry only for **idempotent** operations (odczyty z klientów źródeł: Spotify,
  MusicBrainz, Deezer, LLM)
- Never retry on `4xx` errors — only on `5xx` and timeouts; wyjątek: `429 Too Many Requests`
  → honoruj nagłówek `Retry-After` zamiast zwykłego backoffu
- Add a circuit breaker on every external HTTP call
- MusicBrainz: twardy limit 1 req/s obowiązuje NIEZALEŻNIE od retry (retry też podlega limitowi)
