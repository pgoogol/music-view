# Database & Performance

> Zaadaptowane z zewnętrznego rulesetu (D16); usunięto Kafka (brak messagingu),
> nazewnictwo migracji dostosowano do projektu (sekwencyjne, jak w PLAN.md).

## Flyway migrations
- Every schema change goes through a Flyway migration — never modify the schema manually or via `ddl-auto`
- Set `spring.jpa.hibernate.ddl-auto=validate` — Flyway manages the schema, Hibernate only validates
- Naming: sekwencyjne `V{n}__opis.sql` — e.g. `V1__schemat_bazowy.sql`, `V2__dodaj_indeks_energy.sql`
- Migrations must be **idempotent** where possible (use `IF NOT EXISTS`, `IF EXISTS`)
- Never modify an existing migration that has been applied — create a new one
- Test migrations in CI with Testcontainers before merging (testy kontekstu/`@DataJpaTest`
  uruchamiają migracje na czystej bazie)
- Schemat po M1.1 jest **zamrożony** — każda zmiana wymaga jawnej decyzji (DECYZJE.md) + migracji

## JPA / Hibernate rules
- Always set `fetch = FetchType.LAZY` on associations (`@ManyToOne` jest domyślnie EAGER — ustawiaj jawnie)
- Use `@EntityGraph` or JOIN FETCH in queries when you know you need related data — avoid N+1
- Enable SQL logging in dev to catch N+1 queries (profil `local`):
  ```yaml
  spring.jpa.show-sql: true
  spring.jpa.properties.hibernate.format_sql: true
  ```
- Prefer **projections** (interfaces or records) over full entity fetches for read-only queries
- Use `@Version` on entities that are updated concurrently — enables optimistic locking

## Query rules
- Use **Spring Data derived queries** for simple lookups (1–2 conditions)
- Use **JPQL with `@Query`** for joins and projections
- Use **native SQL with `@Query(nativeQuery = true)`** only for complex aggregations or DB-specific
  features (pg_trgm `similarity`, tsvector `@@`)
- Use **`@Modifying` + `@Transactional`** for bulk updates/deletes — never load entities just to delete them
- Always add database indexes for: foreign keys, columns in `WHERE` / `ORDER BY` / `JOIN` clauses, unique constraints

## Connection pool (HikariCP)
Tune for your workload — defaults are often wrong:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10        # narzędzie lokalne — mały pool wystarcza
      minimum-idle: 2
      connection-timeout: 3000     # 3s — fail fast
      max-lifetime: 1800000        # 30min — less than DB connection timeout
      leak-detection-threshold: 5000
```

## Pagination
- Never return unbounded lists from the API — always paginate with `Pageable`
- Default page size: **20**, max: **100** — validate and cap in the controller
- Offset pagination wystarcza dla ~2500 utworów; keyset (cursor) dopiero gdyby biblioteka
  urosła o rząd wielkości

## Caching
- Use Spring Cache abstraction (`@Cacheable`, `@CacheEvict`) — do not hardcode cache calls in services
- Cache read-heavy, rarely-changing data (np. wyniki lookupów MusicBrainz ISRC→MBID — cache
  trwały w bazie zgodnie z D6, nie in-memory)
- Always set a TTL — never cache indefinitely (wyjątek: fakty deterministyczne cache'owane w bazie)
- Cache keys must include all parameters that affect the result
- Test cache eviction explicitly — missing eviction is a common bug
