# Testing

> Zaadaptowane z zewnętrznego rulesetu (D16); usunięto Kafka i testy kontraktowe
> (brak messagingu i mikroserwisów w projekcie).

## Tooling
- **Unit tests**: JUnit 5 + Mockito + AssertJ
- **Integration tests**: `@SpringBootTest` + Testcontainers
- **Web layer tests**: `@WebMvcTest`
- **Repository tests**: `@DataJpaTest` + Testcontainers (never use H2 for JPA tests — D12)
- **External API clients**: WireMock — nagrane odpowiedzi, bez realnych wywołań w testach

## Naming convention
```
[methodUnderTest]_[whatItDoes]_[expectedResult]

ingestFile_whenTrackAlreadyExists_reportsAlreadyExisted
findById_whenTrackExists_returnsTrackResponse
```

## Test structure
Structure every test body in explicit **given / when / then** sections, separated
by `// given`, `// when`, `// then` comments (or blank lines):
```java
@Test
void resolveBpm_whenAllSourcesEmpty_leavesBpmForAi() {

    // given
    TrackCatalog track = TrackCatalogFixtures.skeletonTrack("sp-1");

    // when
    BpmResolution resolution = resolver.resolve(track);

    // then
    assertThat(resolution.source()).isNull();
}
```

## Unit test rules
- One `@Test` = one assertion concept (can use `assertAll` for related fields)
- Use `@ExtendWith(MockitoExtension.class)` — never `@SpringBootTest` for pure unit tests
- Mock only direct dependencies, not transitive ones
- Use `ArgumentCaptor` to verify what was passed to mocks, not just that they were called
- Never test private methods directly — test behavior through the public API
- Avoid `Thread.sleep()` in tests — use `Awaitility` for async assertions

## Integration test rules
- Use `@Testcontainers` + real database image (`postgres:16-alpine` — ta sama wersja co docker-compose)
- Share one container across the test suite (`@ServiceConnection` bean w współdzielonej
  `TestcontainersConfiguration` lub `@Container` + `static` field)
- Use `@Sql("/test-data/….sql")` for larger test fixtures — not hardcoded inserts in test methods
- Reset state between tests with `@Transactional` (rollback) or `@Sql(executionPhase = AFTER_TEST_METHOD)`
- Test the full HTTP stack with `MockMvc` or `WebTestClient` — not by calling service methods directly

## Coverage targets
- Service layer: **≥ 80%** line coverage
- Critical paths (import CSV, zapisy enrichmentu, mutacje library): **100%** branch coverage
- Do not chase coverage numbers — untested edge cases matter more than the percentage

## Test data builders
Use the **Builder pattern** or **Object Mother** for test fixtures — never repeat `new TrackCatalog(...)` with many args across tests:
```java
// Object Mother
public final class TrackCatalogFixtures {
    public static TrackCatalog enrichedTrack(String spotifyId) {
        // pełny rekord z sensownymi wartościami domenowymi
    }
}
```

## What NOT to test
- Spring framework internals (auto-configuration, bean wiring)
- Simple getters/setters on entities (unless they contain logic)
- Trivial one-liners that are obvious from the code
