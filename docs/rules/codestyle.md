# Code Style & Naming Conventions

> Zaadaptowane z zewnętrznego rulesetu (D16). Obowiązuje cały nowy kod.

## Java version features
- Use **records** for DTOs and value objects — never plain POJOs with getters/setters for data carriers
  (JPA entities are the exception — Hibernate requires mutable classes)
- Use **sealed classes** for domain result types (e.g. `Success | Failure | NotFound`)
- Use **pattern matching** (`instanceof`, `switch`) instead of cascaded if-else
- Use **virtual threads** (`Executors.newVirtualThreadPerTaskExecutor()`) for blocking I/O tasks
- **Do not use `var`** — always declare the explicit type for local variables
- Use text blocks for multi-line SQL, JSON, and HTML strings

## Naming
| Element | Convention | Example |
|---|---|---|
| Class | PascalCase, noun | `EnrichmentService`, `SpotifyClient` |
| Interface | PascalCase, noun or adjective | `Auditable`, `TrackCatalogRepository` |
| Method | camelCase, verb | `findById`, `resolveBpm` |
| Constant | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| Package | lowercase, singular | `com.pgoogol.catalog` |
| DTO | Suffix with `Request` / `Response` | `IngestFileRequest`, `TrackResponse` |
| Exception | Suffix with `Exception` | `TrackNotFoundException` |
| Config class | Suffix with `Config` | `EnrichmentJobConfig` |
| Entity | Plain noun, no suffix | `TrackCatalog`, `Playlist` |

## Code structure rules
- Maximum method length: **30 lines** — extract if longer
- Maximum class length: **300 lines** — split by responsibility
- No `static` utility classes — use Spring beans (exception: test fixtures / Object Mothers)
- No `null` returns from public methods — use `Optional<T>` or throw a typed exception
- Prefer `List.of()`, `Map.of()`, `Set.of()` for immutable collections
- Always use `@NonNull` / `@Nullable` (from `org.springframework.lang`) on method parameters and return types

## Loops & chaining
- Avoid plain `for` loops (both indexed `for (int i …)` and enhanced `for (x : xs)`) — prefer `Stream` / `forEach` / declarative constructs, **unless** a plain loop is genuinely cheaper in time or complexity (then a `for` is fine, and say why)
- Avoid chained calls like `a().b().c()` — unless using a Builder, `Optional`, `Stream`, or Mockito API

## Spring-specific
- Use constructor injection only — never `@Autowired` on fields
- Mark service classes `@Transactional` at class level only when ALL methods need it; otherwise annotate individual methods
- Use `@Value` only for simple scalar configs; use `@ConfigurationProperties` for groups of related properties
- Never use `@Autowired` on constructors — Spring injects automatically when there is only one constructor
- Keep `@RestController` thin: no business logic, only input validation + delegation to service

## Comparisons & null checks
- Use `Objects.equals(a, b)` instead of `a.equals(b)` or `a == b` for object equality — null-safe
- Use `Objects.isNull(x)` / `Objects.nonNull(x)` instead of `x == null` / `x != null`
- Use `Objects.requireNonNull(x, "message")` for guard clauses at the top of methods
- Use `Objects.requireNonNullElse(x, default)` instead of ternary null checks
- Use `Objects.toString(x, "fallback")` instead of `x != null ? x.toString() : "fallback"`
- Exception: `== null` is acceptable inside `equals()` overrides and null-check chain starts

```java
// WRONG
if (track.getBpm() == null || track.getBpm().equals(other.getBpm())) { ... }
if (entry != null) return entry.getDjNotes();

// CORRECT
if (Objects.isNull(track.getBpm()) || Objects.equals(track.getBpm(), other.getBpm())) { ... }
return Objects.toString(entry, "unknown");
```

## Collections — CollectionUtils
- Use `CollectionUtils.isEmpty(col)` / `CollectionUtils.isNotEmpty(col)` instead of `col == null || col.isEmpty()`
- Use `CollectionUtils.emptyIfNull(col)` instead of ternary null-to-empty-list guards
- Use `CollectionUtils.containsAny(col, candidates)` instead of manual stream + anyMatch for simple membership checks
- Use `CollectionUtils.intersection(a, b)` / `union(a, b)` / `subtract(a, b)` instead of manual set operations
- Use Apache Commons `CollectionUtils` (`org.apache.commons.collections4`) — not Spring's limited variant
  (dodaj zależność `commons-collections4` przy pierwszym użyciu)

```java
// WRONG
if (tracks == null || tracks.isEmpty()) { ... }
List<String> tags = entry.getCustomTags() != null ? entry.getCustomTags() : Collections.emptyList();

// CORRECT
if (CollectionUtils.isEmpty(tracks)) { ... }
List<String> tags = CollectionUtils.emptyIfNull(entry.getCustomTags());
```

## Formatting
- 4-space indentation (no tabs)
- Opening brace on the same line
- Leave one blank line after an opening brace `{` that starts a class or method body
- Enforce with Checkstyle or Spotless (do włączenia w przyszłym kamieniu)
