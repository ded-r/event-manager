# EventManager — Execution Roadmap

Daily implementation checklist. Check items off in order. Every step ends with a **✅ Verify** command (DoD).
Rules (from CLAUDE.md): `ddl-auto` = `validate`/`none`; every DDL change is a Liquibase changeset with rollback; no work in `notification-service` before Iteration 4; ask for `REVEAL` if you want full code from me.

Conventions used below:
- Base package: `auyesbay.dev.eventservice` (event-service), `auyesbay.dev.notificationservice` (notification-service) — matches the `auyesbay.dev` groupId already in the poms. Sub-packages used in steps: `domain`, `dto`, `mapper`, `repository`, `service`, `web`, `config`.
- API prefix: `/api/v1`
- Verify commands assume services on `localhost:8080` (event-service) and `localhost:8081` (notification-service).

### Spring Boot 4.1.x notes (this project is on Boot 4, not 3.3.5)
Where a step below says "Boot 3 style", check the Boot 4 equivalent first — verify against the Boot 4 migration guide and the release notes, not memory:
- **Modular starters / test starters**: you already use `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-liquibase-test` etc. Each feature has its own `-test` starter; add the matching one when you add a feature (e.g. security, kafka, redis).
- **Test annotation packages moved** (`@DataJpaTest`, `@WebMvcTest`, `@AutoConfigureMockMvc` live in new per-module packages). Let the IDE import them; don't copy imports from Boot 3 tutorials.
- **Jackson 3** (`tools.jackson.*` packages) is the default; many Boot 3 blog snippets for `ObjectMapper`, serializers (Redis, Kafka JSON) and JWT libraries won't compile as-is. Search: `spring boot 4 jackson 3 migration`.
- **Testcontainers**: `@ServiceConnection` still applies, but check the Testcontainers version Boot 4 manages (artifact names changed in 2.x).
- **Spring Security 7, Spring Kafka 4, Spring Data Redis 4** arrive in Iterations 2/4/5 — read each migration note before the iteration starts.
- **Hibernate 7** under JPA 3.2: re-check any HQL/`@Lock` hints you copy from older material.
- **SpringDoc 3.x** is the Boot 4 line (your pom has 3.2.0); Swagger UI path is still `/swagger-ui.html`.

---

# Iteration 1 — Core Foundation & Persistence (`event-service` only)

### Phase 1A — Decisions & Infrastructure

- [ ] **Step 1.0: Settle schema decisions** and write them in a `docs/decisions.md` (or comments in changesets): ID strategy (`BIGINT` identity vs `UUID`), `TIMESTAMPTZ` ↔ `Instant`/`OffsetDateTime`, `ON DELETE` for `events.location_id`, where `max_participants <= locations.capacity` is enforced, status as `CHECK` vs native enum.
  - ✅ Verify: file exists and each of the 5 questions has an answer + one-line reason.

- [ ] **Step 1.1: Create root `docker-compose.yaml`** with a `postgres:16` service: container `eventmanager-postgres`, port `5432:5432`, env `POSTGRES_USER`/`POSTGRES_PASSWORD`/`POSTGRES_DB=events_db`, named volume `pgdata`, healthcheck via `pg_isready`.
  - ✅ Verify: `docker compose up -d && docker compose ps` shows `healthy`; `docker exec -it eventmanager-postgres psql -U <user> -d events_db -c '\l'`.

- [ ] **Step 1.2: Add DB init script** `infra/postgres/init/01-create-databases.sql` mounted to `/docker-entrypoint-initdb.d/` that also creates `notifications_db` (unused until Iteration 4, but keeps infra stable).
  - ✅ Verify: `docker compose down -v && docker compose up -d`, then `psql -U <user> -c '\l'` lists both DBs.

- [ ] **Step 1.3: Create parent `pom.xml`** (packaging `pom`, Spring Boot 3.3.5 parent, Java 21, `<modules>`: `event-service`, `notification-service`, dependency management for MapStruct 1.6.3, SpringDoc 2.6.0, `lombok-mapstruct-binding` 0.2.0).
  - ✅ Verify: `mvn -q validate` succeeds from root.

- [ ] **Step 1.4: Create `event-service` module** pom with: `web`, `data-jpa`, `validation`, `liquibase-core`, `postgresql`, `lombok`, `mapstruct` (+ processor, annotation-processor ordering with Lombok), `springdoc-openapi-starter-webmvc-ui`, `spring-boot-starter-test`, Testcontainers (`postgresql`, `junit-jupiter`). Add `EventServiceApplication`.
  - Keep `notification-service` as an empty placeholder module only (pom + no code) **or** comment it out of `<modules>` until Iteration 4 (your call; do not add source files).
  - ✅ Verify: `mvn clean compile -pl event-service`.

- [ ] **Step 1.5: Configure `application.yaml`** (profile `local`): datasource → `jdbc:postgresql://localhost:5432/events_db`, `spring.jpa.hibernate.ddl-auto: validate`, `spring.jpa.open-in-view: false`, `spring.liquibase.change-log: classpath:db/changelog/db.changelog-master.yaml`, `hibernate.jdbc.time_zone: UTC`, show-sql off by default.
  - ✅ Verify: app starts and logs `Liquibase ... ran successfully` (after 1B) and no Hibernate schema-generation output.

> 💡 **Deep Dive & Theory — Why Liquibase instead of `ddl-auto=update`**
> - **Concept**: `update` diffs entities against the live schema on startup and applies what it can. It is non-deterministic, never drops/renames, can't express data migrations, has no history, and differs per environment. Liquibase records every change as a versioned changeset in `DATABASECHANGELOG` with checksums, so the schema is reproducible and reviewable in git.
> - **Pitfalls**: editing an already-applied changeset (checksum mismatch → startup failure); forgetting `rollback`; mixing `ddl-auto=update` with Liquibase (silent drift); long-running DDL locking tables in prod; missing indexes on FK columns because Hibernate never created them for you.
> - **Search**: `liquibase changeset checksum validation failed`, `liquibase rollback tag vs count`, `hibernate ddl-auto validate vs none`, `flyway vs liquibase`, `expand contract migration pattern`.

### Phase 1B — Liquibase Schema

- [ ] **Step 1.6: Create master changelog** `db/changelog/db.changelog-master.yaml` that only `include`s files from `db/changelog/changes/` (no inline changesets).
  - ✅ Verify: `mvn spring-boot:run -pl event-service` boots; `psql ... -c 'select id, filename from databasechangelog;'`.

- [ ] **Step 1.7: Changeset `001-create-locations`**: table `locations` (`id`, `name`, `address`, `city`, `country`, `capacity` + `CHECK capacity > 0`, `created_at`, `updated_at`, `version` default 0), `UNIQUE (name, address)`. Add explicit `rollback`.
  - ✅ Verify: `psql ... -c '\d locations'`; then `mvn liquibase:rollback` (or Liquibase CLI `rollbackCount 1`) and re-apply.

- [ ] **Step 1.8: Changeset `002-create-events`**: table `events` (`id`, `title`, `description`, `location_id` FK → `locations`, `start_time`, `end_time`, `max_participants` + `CHECK > 0`, `status` + `CHECK IN ('DRAFT','PUBLISHED','CANCELLED','COMPLETED')`, `created_at`, `updated_at`, `version`), `CHECK end_time > start_time`, FK with your chosen `ON DELETE`.
  - ✅ Verify: `psql ... -c '\d events'`; try violating each constraint manually with `INSERT` and confirm Postgres rejects it.

- [ ] **Step 1.9: Changeset `003-create-indexes`**: `idx_events_location_id (location_id)`, `idx_events_status_start_time (status, start_time)`, `idx_locations_city (city)`. Rollback = `dropIndex`.
  - ✅ Verify: `psql ... -c '\di'`; `EXPLAIN SELECT * FROM events WHERE status='PUBLISHED' ORDER BY start_time LIMIT 20;` (with test data) uses the index.

### Phase 1C — Domain Model

- [ ] **Step 1.10: Enum `EventStatus`** (`DRAFT, PUBLISHED, CANCELLED, COMPLETED`) in `...domain`. Decide and document allowed transitions (e.g., `DRAFT→PUBLISHED→COMPLETED`, `*→CANCELLED` except `COMPLETED`).
  - ✅ Verify: unit test `EventStatusTest` covering allowed/forbidden transitions: `mvn test -pl event-service -Dtest=EventStatusTest`.

- [ ] **Step 1.11: JPA Entity `Location`** (`@Entity @Table(name="locations")`) with fields matching 1.7, `@Version`, auditing timestamps. Equality based on id (see theory).
  - ✅ Verify: app boots with `ddl-auto=validate` (schema/entity mismatch fails fast).

- [ ] **Step 1.12: JPA Entity `Event`** (`@Table(name="events")`) with `@ManyToOne(fetch = LAZY) Location location`, `@Enumerated(EnumType.STRING) EventStatus status`, `@Version`, auditing timestamps.
  - ✅ Verify: app boots with `validate`; `@DataJpaTest` + Testcontainers persists and reloads an `Event`.

> 💡 **Deep Dive & Theory — Lombok `@Data` on JPA entities**
> - **Concept**: `@Data` generates `equals/hashCode/toString` over **all** fields. On entities this breaks: (1) `hashCode` changes when the generated `id` is assigned after `persist` → entity "disappears" from `HashSet`s; (2) `toString`/`hashCode` touching a LAZY association triggers extra queries or `LazyInitializationException`; (3) bidirectional relations cause `StackOverflowError`; (4) Hibernate proxies are subclasses, so `getClass()`-based `equals` fails.
> - **Safe approach**: use `@Getter/@Setter` (+ `@NoArgsConstructor(access = PROTECTED)`), exclude associations from `toString`, implement `equals/hashCode` on the id (with `Hibernate.getClass`/`instanceof` handling proxies) and a constant `hashCode` (`getClass().hashCode()`), or use a business/natural key.
> - **Pitfalls**: `@Builder` without `@NoArgsConstructor`; `@Setter` on id and version; `@EqualsAndHashCode` on entities with lazy fields.
> - **Search**: `Vlad Mihalcea best way to implement equals hashCode JPA`, `lombok data jpa entity pitfalls`, `hibernate proxy getClass equals`, `LazyInitializationException`.

> 💡 **Deep Dive & Theory — Entity vs DTO separation**
> - **Concept**: entities model persistence (managed state, lazy proxies, dirty checking); DTOs model the API contract. Exposing entities leaks internal columns (`version`, internal IDs), couples the API to the schema, serializes lazy proxies (`LazyInitializationException`/N+1 inside Jackson), and enables **mass assignment** (client sets `status` or `id`).
> - **Pitfalls**: reusing one DTO for create/update/response; returning entities from controllers; mapping inside controllers; `open-in-view=true` hiding lazy-loading problems until prod.
> - **Search**: `entity vs dto spring boot`, `mass assignment vulnerability`, `open-in-view anti-pattern`, `jackson hibernate lazy serialization`.

### Phase 1D — DTOs & Mappers

- [ ] **Step 1.13: Location DTOs**: `LocationCreateRequest`, `LocationUpdateRequest`, `LocationResponse` (records). Validation: `@NotBlank` + `@Size` on text, `@Positive` on `capacity`.
  - ✅ Verify: unit test with `Validator` for each constraint.

- [ ] **Step 1.14: Event DTOs**: `EventCreateRequest` (`title`, `description`, `locationId` `@NotNull`, `startTime` `@NotNull @Future`, `endTime` `@NotNull`, `maxParticipants` `@NotNull @Positive`), `EventUpdateRequest`, `EventResponse` (embeds `LocationResponse` or `locationId` — decide), plus a class-level validator for `endTime > startTime` (custom annotation `@ValidTimeRange`).
  - ✅ Verify: `mvn test -pl event-service -Dtest=EventDtoValidationTest`.

- [ ] **Step 1.15: MapStruct mappers** `LocationMapper`, `EventMapper` (`componentModel = "spring"`; ignore `id`, `version`, timestamps on create; `@MappingTarget` for update; `unmappedTargetPolicy = ERROR`).
  - ✅ Verify: `mvn clean compile -pl event-service` generates `*MapperImpl` in `target/generated-sources`; mapper unit tests pass.

### Phase 1E — Repositories & Services

- [ ] **Step 1.16: Repositories** `LocationRepository`, `EventRepository` (Spring Data JPA). Add `Page<Event> findByStatus(...)` and a query that `JOIN FETCH`es `location` (or `@EntityGraph`) for list endpoints.
  - ✅ Verify: `@DataJpaTest` with Testcontainers; assert query count (enable `hibernate.generate_statistics` or use `datasource-proxy`) is constant regardless of page size.

> 💡 **Deep Dive & Theory — N+1 queries**
> - **Concept**: loading N events then touching `event.getLocation()` fires N extra selects. Fix with `JOIN FETCH`/`@EntityGraph`, or a DTO projection selecting only needed columns.
> - **Pitfalls**: `JOIN FETCH` + `Pageable` on collections → in-memory pagination (`HHH000104`); here `@ManyToOne` is safe. `EAGER` is not a fix (it just hides N+1 and applies everywhere).
> - **Search**: `HHH000104 firstResult maxResults in memory`, `EntityGraph vs join fetch`, `hibernate statistics query count test`.

- [ ] **Step 1.17: `LocationService`** (`@Transactional` at class level with `readOnly = true` on reads): `create`, `getById`, `list(Pageable)`, `update`, `delete`. Throw `ResourceNotFoundException`, `DuplicateResourceException`, `ConflictException` (location in use).
  - ✅ Verify: unit tests (Mockito) for happy path + not found + duplicate.

- [ ] **Step 1.18: `EventService`**: `create`, `getById`, `list(Pageable, optional status/city/from/to filters)`, `update`, `changeStatus`, `delete`. Enforce the capacity rule decided in 1.0 and status transitions. Keep transactions short and free of external calls.
  - ✅ Verify: `mvn test -pl event-service -Dtest=EventServiceTest`.

### Phase 1F — REST API & Error Handling

- [ ] **Step 1.19: `LocationController`** — `POST /api/v1/locations`, `GET /api/v1/locations/{id}`, `GET /api/v1/locations?page=&size=&sort=`, `PUT /api/v1/locations/{id}`, `DELETE /api/v1/locations/{id}`.
  - ✅ Verify: `curl -i -X POST localhost:8080/api/v1/locations -H 'Content-Type: application/json' -d '{"name":"Hall A","address":"1 Main St","city":"Almaty","country":"KZ","capacity":500}'` → `201` + `Location` header.

- [ ] **Step 1.20: `EventController`** — `POST /api/v1/events`, `GET /api/v1/events/{id}`, `GET /api/v1/events?status=&city=&page=&size=&sort=startTime,asc`, `PUT /api/v1/events/{id}`, `PATCH /api/v1/events/{id}/status`, `DELETE /api/v1/events/{id}`.
  - ✅ Verify: `curl -s 'localhost:8080/api/v1/events?page=0&size=5&sort=startTime,asc' | jq`.

- [ ] **Step 1.21: `@RestControllerAdvice` `GlobalExceptionHandler`** returning RFC 7807 `ProblemDetail`: `MethodArgumentNotValidException` → 400 with field errors, `ResourceNotFoundException` → 404, `DuplicateResourceException`/`DataIntegrityViolationException` → 409, `ObjectOptimisticLockingFailureException` → 409, fallback → 500 (no stack trace leak).
  - ✅ Verify: `curl -i -X POST localhost:8080/api/v1/events -H 'Content-Type: application/json' -d '{}'` → `400` with per-field errors; `curl -i localhost:8080/api/v1/events/999999` → `404`.

- [ ] **Step 1.22: Configure SpringDoc** (title, version, tags per controller, `@Operation`/`@ApiResponse` on endpoints).
  - ✅ Verify: open `http://localhost:8080/swagger-ui.html` and execute each endpoint.

### Phase 1G — Tests & Iteration DoD

- [ ] **Step 1.23: Integration tests** with `@SpringBootTest` + Testcontainers PostgreSQL (`@ServiceConnection`): full CRUD flow, pagination/sorting, constraint violation → 409, validation → 400.
  - ✅ Verify: `mvn clean test -pl event-service` is green.

- [ ] **Step 1.24: Migration test**: boot against empty DB, assert `databasechangelog` row count, and that Hibernate `validate` passes.
  - ✅ Verify: included in `mvn clean test -pl event-service`.

- [ ] **Step 1.25 (Iteration 1 DoD)**: `docker compose down -v && docker compose up -d && mvn clean test -pl event-service && mvn spring-boot:run -pl event-service` — then manually walk the Swagger flow: create location → create event → list → update → cancel → delete.

---

# Iteration 2 — Security & Access Control (`event-service`)

### Phase 2A — User Model

- [ ] **Step 2.1: Changeset `004-create-users`**: `users` (`id`, `email` UNIQUE NOT NULL (store lower-cased), `password_hash` NOT NULL, `full_name`, `enabled` bool default true, `created_at`, `updated_at`, `version`). Index on `email` is implicit via UNIQUE.
  - ✅ Verify: `psql ... -c '\d users'`.

- [ ] **Step 2.2: Changeset `005-create-user-roles`**: `user_roles` (`user_id` FK, `role` VARCHAR + `CHECK IN ('USER','ORGANIZER','ADMIN')`, PK `(user_id, role)`).
  - ✅ Verify: `\d user_roles`.

- [ ] **Step 2.3: Changeset `006-add-events-organizer`**: `ALTER TABLE events ADD COLUMN organizer_id BIGINT` → backfill (create a seed system user or make nullable first) → `NOT NULL` + FK + index `idx_events_organizer_id`. Use the expand/contract approach in separate changesets.
  - ✅ Verify: `\d events` shows FK + index; rollback + re-apply works.

- [ ] **Step 2.4: Entities** `User`, enum `Role` (`USER, ORGANIZER, ADMIN`), update `Event` with `@ManyToOne(LAZY) User organizer`.
  - ✅ Verify: app boots with `ddl-auto=validate`.

- [ ] **Step 2.5: `UserRepository`** (`Optional<User> findByEmail(String)`, `existsByEmail`), `UserMapper`, DTOs `RegisterRequest` (`@Email`, password `@Size(min=8)`), `LoginRequest`, `AuthResponse`, `UserResponse`.
  - ✅ Verify: `mvn test -pl event-service -Dtest=UserRepositoryTest`.

### Phase 2B — Authentication

- [ ] **Step 2.6: Add dependencies** `spring-boot-starter-security`, `jjwt-api/impl/jackson` (or `spring-security-oauth2-resource-server` — pick one and note why), `spring-security-test`.
  - ✅ Verify: `mvn clean compile -pl event-service`; all endpoints now return `401` (default secure).

- [ ] **Step 2.7: `PasswordEncoder` bean (BCrypt, strength 10–12)**; `CustomUserDetailsService` loading by email.
  - ✅ Verify: unit test: `encoder.matches(raw, hash)`; two encodings of the same password differ (salt).

- [ ] **Step 2.8: `JwtService`**: generate access token (TTL 15 min; claims `sub`=userId, `roles`, `iat`, `exp`, `jti`), validate signature + expiry, extract claims. Secret/key from env (`JWT_SECRET`), never committed.
  - ✅ Verify: unit tests for valid, expired, tampered-signature, and wrong-algorithm (`alg=none`) tokens.

- [ ] **Step 2.9: Refresh tokens**: changeset `007-create-refresh-tokens` (`id`, `user_id` FK, `token_hash` UNIQUE, `expires_at`, `revoked` bool, `replaced_by`, `created_at`; index on `user_id`). Store only a **hash** of the token.
  - ✅ Verify: `\d refresh_tokens`.

- [ ] **Step 2.10: `AuthService` + `AuthController`**:
  - `POST /api/v1/auth/register` (default role `USER`; ignore any role in payload)
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/refresh` (rotation: old token revoked, new one issued; reuse of revoked token → revoke the whole family)
  - `POST /api/v1/auth/logout` (revoke refresh token)
  - ✅ Verify:
    ```
    curl -i -X POST localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' -d '{"email":"a@b.com","password":"Passw0rd!","fullName":"A B"}'
    curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"email":"a@b.com","password":"Passw0rd!"}' | jq
    ```

> 💡 **Deep Dive & Theory — JWT stateless authentication**
> - **Concept**: server signs a token (header.payload.signature) containing identity + roles; each request carries it in `Authorization: Bearer ...`; the server verifies signature + `exp` with no session lookup. Payload is **base64, not encrypted** — never put secrets in it.
> - **Pitfalls**: `alg=none` / algorithm confusion attacks; weak or hard-coded HS256 secret; no `exp`; huge payloads; no way to revoke an access token before expiry (hence short TTL + refresh); storing tokens in `localStorage` (XSS) vs httpOnly cookies (CSRF).
> - **Search**: `JWT alg none attack`, `HS256 vs RS256`, `JWT revocation strategies`, `JWT localStorage vs httpOnly cookie`, `OWASP JWT cheat sheet`.

> 💡 **Deep Dive & Theory — Access vs Refresh token rotation**
> - **Concept**: access token = short-lived (5–15 min), sent on every call; refresh token = long-lived (days), used only at `/auth/refresh`, **stored server-side (hashed)**, single-use. Rotation: every refresh returns a new pair and invalidates the old refresh token. If an already-used refresh token shows up again → assume theft and revoke the entire token family.
> - **Pitfalls**: race when two tabs refresh simultaneously (grace window or per-client family handling); not hashing refresh tokens in DB; no cleanup job for expired tokens; refresh endpoint not rate-limited.
> - **Search**: `refresh token rotation reuse detection`, `Auth0 refresh token rotation`, `token family revocation`.

### Phase 2C — Filter Chain & RBAC

- [ ] **Step 2.11: `JwtAuthenticationFilter extends OncePerRequestFilter`**: read header → validate → build `UsernamePasswordAuthenticationToken` with authorities `ROLE_*` → set `SecurityContextHolder`. On failure, don't set context and let the entry point return 401.
  - ✅ Verify: `curl -i localhost:8080/api/v1/events -H 'Authorization: Bearer garbage'` → `401`.

- [ ] **Step 2.12: `SecurityConfig` `SecurityFilterChain`**: stateless sessions, CSRF disabled (justify), permit `/api/v1/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `GET /api/v1/events/**` & `/locations/**` (or authenticated — decide), all else authenticated; register JWT filter **before** `UsernamePasswordAuthenticationFilter`; custom `AuthenticationEntryPoint` (401 ProblemDetail) and `AccessDeniedHandler` (403 ProblemDetail).
  - ✅ Verify: unauthenticated `POST /api/v1/events` → `401`; authenticated `USER` → `403`.

> 💡 **Deep Dive & Theory — Spring Security filter chain order**
> - **Concept**: Security is a chain of servlet filters (`DelegatingFilterProxy` → `FilterChainProxy` → `SecurityFilterChain`). Order matters: `SecurityContextHolderFilter` → … → `UsernamePasswordAuthenticationFilter` → … → `ExceptionTranslationFilter` → `AuthorizationFilter`. Your JWT filter must run before authorization decisions and must populate the `SecurityContext`.
> - **Pitfalls**: filter registered twice (as `@Component` *and* in the chain); exceptions thrown inside filters bypass `@RestControllerAdvice` (use entry point/handler); `permitAll` still runs your filter; `SecurityContext` is thread-bound (careful with `@Async`).
> - **Search**: `spring security filter chain order debug`, `logging.level.org.springframework.security=TRACE`, `OncePerRequestFilter shouldNotFilter`, `ExceptionTranslationFilter`.

- [ ] **Step 2.13: Method-level RBAC**: `@EnableMethodSecurity`; `@PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")` on create/update/delete event and location endpoints; ownership check — only the event's organizer or ADMIN can modify it (`@PreAuthorize("@eventSecurity.isOwner(#id, authentication)")`).
  - ✅ Verify: `USER` token → `POST /api/v1/events` = `403`; `ORGANIZER` A editing organizer B's event = `403`; `ADMIN` = `200`.

- [ ] **Step 2.14: Set `organizer_id` from the authenticated principal**, never from the request body.
  - ✅ Verify: creating an event with a spoofed `organizerId` in JSON is ignored (test asserts stored organizer = token subject).

- [ ] **Step 2.15: Admin bootstrap**: seed an admin via changeset or a `CommandLineRunner` guarded by profile/env; `PATCH /api/v1/admin/users/{id}/roles` (ADMIN only).
  - ✅ Verify: admin promotes a user to `ORGANIZER`; the new token reflects the role.

- [ ] **Step 2.16: Security tests** with `@SpringBootTest` + MockMvc + `spring-security-test`: matrix of (anonymous / USER / ORGANIZER / ADMIN) × (endpoints). Plus expired/tampered token tests, refresh rotation + reuse detection test.
  - ✅ Verify: `mvn clean test -pl event-service`.

- [ ] **Step 2.17 (Iteration 2 DoD)**: Swagger UI "Authorize" works with a Bearer token; full flow register → login → create event as ORGANIZER → refresh → logout.

---

# Iteration 3 — Business Logic & Concurrency Control (`event-service`)

### Phase 3A — Registration Domain

- [ ] **Step 3.1: Changeset `008-create-event-registrations`**: `event_registrations` (`id`, `event_id` FK, `user_id` FK, `status` `CHECK IN ('CONFIRMED','CANCELLED')`, `idempotency_key` nullable, `created_at`, `version`), **`UNIQUE (event_id, user_id)`** (prevents double registration at DB level), indexes `(event_id, status)`, `(user_id)`.
  - ✅ Verify: `\d event_registrations`; two manual identical `INSERT`s → second fails.

- [ ] **Step 3.2: Add counter to events**: changeset `009-add-events-registered-count` → `registered_count INTEGER NOT NULL DEFAULT 0`, `CHECK (registered_count >= 0 AND registered_count <= max_participants)`.
  - ✅ Verify: manual `UPDATE events SET registered_count = max_participants + 1 ...` is rejected by Postgres.

- [ ] **Step 3.3: Entity `EventRegistration`**, enum `RegistrationStatus`, `EventRegistrationRepository` (`existsByEventIdAndUserId`, `findByEventIdAndUserId`, `countByEventIdAndStatus`).
  - ✅ Verify: app boots with `validate`.

- [ ] **Step 3.4: DTOs** `RegistrationResponse`, `RegistrationListItem`; **endpoints**:
  - `POST /api/v1/events/{eventId}/registrations` (header `Idempotency-Key`)
  - `DELETE /api/v1/events/{eventId}/registrations` (cancel own)
  - `GET /api/v1/events/{eventId}/registrations` (ORGANIZER/ADMIN, paginated)
  - `GET /api/v1/users/me/registrations`
  - ✅ Verify: `curl -i -X POST localhost:8080/api/v1/events/1/registrations -H "Authorization: Bearer $TOKEN" -H 'Idempotency-Key: 1b9d...'` → `201`.

### Phase 3B — Naive Version (Prove the Bug First)

- [ ] **Step 3.5: Implement naive `RegistrationService.register`** — read count → check capacity → insert → increment, **no locking**.
  - ✅ Verify: write `OverbookingRaceTest` (ExecutorService with 50 threads + `CountDownLatch` start gate, event capacity 10, Testcontainers). Run: assertion `confirmed <= 10` **fails**. Keep this test; it's your proof.

> 💡 **Deep Dive & Theory — The Overbooking Problem (race condition)**
> - **Concept**: check-then-act is not atomic. Two transactions both read `registered=9`, both pass `9 < 10`, both insert → 11 registrations. Under Postgres default `READ COMMITTED`, neither sees the other's uncommitted insert.
> - **Pitfalls**: a Java `synchronized` block/`ReentrantLock` only works in one JVM (fails with 2+ instances); `@Transactional` alone doesn't prevent it; checking capacity with `COUNT(*)` then inserting; unit tests with mocks never reveal this.
> - **Search**: `check-then-act race condition database`, `postgres read committed lost update`, `overbooking problem inventory`, `isolation levels write skew`.

### Phase 3C — Locking Strategies (implement and compare)

- [ ] **Step 3.6: Strategy A — Pessimistic**: `EventRepository.findByIdForUpdate(Long id)` with `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `@QueryHints(@QueryHint(name="jakarta.persistence.lock.timeout", value="3000"))`; register inside one short transaction: lock event row → validate → insert registration → increment `registered_count`.
  - ✅ Verify: rerun `OverbookingRaceTest` → passes; check SQL log contains `for no key update`/`for update`.

> 💡 **Deep Dive & Theory — Pessimistic Locking (`SELECT ... FOR UPDATE`)**
> - **Concept**: the first transaction locks the event row; others block until commit/rollback, so check-then-act becomes serialized per event. Simple and correct, throughput limited per hot row.
> - **Pitfalls**: lock held during network calls (never call Kafka/HTTP inside); **deadlocks** when locking several rows in inconsistent order (always lock in a deterministic order); missing lock timeout → thread pool exhaustion; `@Transactional` self-invocation means the lock isn't applied in the transaction you think; lock released at commit, so keep the tx short.
> - **Search**: `PESSIMISTIC_WRITE hibernate for update`, `FOR UPDATE SKIP LOCKED`, `postgres row locks FOR NO KEY UPDATE`, `deadlock detected postgres`, `spring transactional self invocation`.

- [ ] **Step 3.7: Strategy B — Optimistic**: rely on `@Version` on `Event`; load event, check capacity, increment `registered_count`, save; catch `ObjectOptimisticLockingFailureException` and **retry** (bounded: 3–5 attempts with jitter) — retry must wrap the transaction from *outside* (separate bean / `@Retryable` on a non-transactional caller).
  - ✅ Verify: rerun `OverbookingRaceTest` → passes; log shows retries; test with 50 threads / 10 seats and count `409`s vs retried successes.

> 💡 **Deep Dive & Theory — Optimistic Locking (`@Version`)**
> - **Concept**: `UPDATE events SET ..., version = version + 1 WHERE id = ? AND version = ?`; 0 rows updated → `OptimisticLockException`. No blocking, great under low contention; under high contention (a flash sale) most attempts fail and retry storms waste work.
> - **Pitfalls**: retrying *inside* the failed transaction (it's already rolled back / marked rollback-only); unbounded retries; swallowing the exception and returning 500 instead of 409/retry; bulk JPQL `UPDATE` bypasses `@Version`; detached entity merges.
> - **Search**: `optimistic vs pessimistic locking when to use`, `spring retry optimistic locking`, `ObjectOptimisticLockingFailureException`, `atomic update where registered_count < max_participants`.

- [ ] **Step 3.8: Strategy C (compare) — Atomic conditional UPDATE**: `UPDATE events SET registered_count = registered_count + 1 WHERE id = :id AND registered_count < max_participants` via `@Modifying @Query`; 0 rows → event full.
  - ✅ Verify: same race test passes; compare p95 latency of A/B/C in a simple benchmark and record in `docs/concurrency-notes.md`.

- [ ] **Step 3.9: Choose one strategy as default** (a `@ConfigurationProperties` flag or just delete the others), documenting the trade-off in `docs/concurrency-notes.md`. Keep the race test for the chosen one as a permanent regression test.
  - ✅ Verify: `mvn test -pl event-service -Dtest=OverbookingRaceTest` green 10 times in a row (`for i in {1..10}; do mvn -q test -pl event-service -Dtest=OverbookingRaceTest || break; done`).

- [ ] **Step 3.10: Cancellation flow**: cancel registration → decrement counter atomically (same locking strategy), idempotent cancel (cancelling twice = same result), disallow cancel for `COMPLETED`/started events.
  - ✅ Verify: race test: 50 concurrent cancels of the same registration → counter decremented exactly once.

- [ ] **Step 3.11: Business rules**: only `PUBLISHED` events accept registration; no registration after `start_time`; organizers can't register for own event (decide); waitlist (optional stretch: `WAITLISTED` status + promotion on cancel).
  - ✅ Verify: unit + integration tests per rule → `400/409` with ProblemDetail.

### Phase 3D — Idempotency

- [ ] **Step 3.12: Idempotency-Key support**: changeset `010-create-idempotency-keys` (`key`, `user_id`, `request_hash`, `response_status`, `response_body`, `created_at`, `UNIQUE (user_id, key)`); same key + same payload → return the **stored** response; same key + different payload → `422`; in-flight duplicate → `409`. TTL cleanup job (`@Scheduled`).
  - ✅ Verify:
    ```
    for i in 1 2 3; do curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8080/api/v1/events/1/registrations -H "Authorization: Bearer $TOKEN" -H 'Idempotency-Key: abc-123'; done
    ```
    → `201`, `200/201 (replayed)`, `200/201 (replayed)`; DB shows exactly one registration.

> 💡 **Deep Dive & Theory — API Idempotency (double-clicks & retries)**
> - **Concept**: an operation is idempotent if repeating it has the same effect as doing it once. `POST` isn't idempotent by nature; clients/gateways retry on timeouts, users double-click. An `Idempotency-Key` header plus a unique constraint lets the server detect duplicates and replay the original result (Stripe-style).
> - **Pitfalls**: checking the key in code but not enforcing with a `UNIQUE` constraint (race between two identical requests); saving the key in a different transaction than the business write (crash between them); not hashing the payload (same key, different body); keys that never expire; using the key only in memory/cache.
> - **Search**: `Stripe idempotency keys`, `idempotency key design postgres unique constraint`, `IETF Idempotency-Key header draft`, `exactly-once vs at-least-once`.

- [ ] **Step 3.13: Concurrency stress on idempotency**: 20 parallel requests with the **same** key → exactly one registration and all responses identical.
  - ✅ Verify: `IdempotencyRaceTest` green.

- [ ] **Step 3.14: Observability for hot paths**: log lock wait time, retry counts; expose Micrometer counters `registration.success`, `registration.full`, `registration.conflict.retries` (Actuator `/actuator/metrics`).
  - ✅ Verify: `curl localhost:8080/actuator/metrics/registration.success`.

- [ ] **Step 3.15 (Iteration 3 DoD)**: `mvn clean test -pl event-service` green incl. race tests; written comparison in `docs/concurrency-notes.md` (when would you pick each strategy? what breaks at 2 instances? what about sharded DBs?).

---

# Iteration 4 — Kafka Integration & Notification Service

> `notification-service` work is **allowed from this iteration only**.

### Phase 4A — Infrastructure

- [ ] **Step 4.1: Extend `docker-compose.yaml`**: Kafka (KRaft mode, `apache/kafka` or `bitnami/kafka`, listeners for host and internal), Kafka-UI (`provectuslabs/kafka-ui`) on port `8089`, healthchecks. Ensure `notifications_db` exists (from 1.2).
  - ✅ Verify: `docker compose up -d`; open `http://localhost:8089` and see the cluster online; `docker exec <kafka> kafka-topics.sh --bootstrap-server localhost:9092 --list`.

- [ ] **Step 4.2: Topics**: `event.created`, `event.registered`, `event.cancelled`, plus DLQs `event.created.DLT`, `event.registered.DLT`, `event.cancelled.DLT`; partitions = 3, key = `eventId` (or `userId` — decide and justify ordering needs). Declare via `NewTopic` beans or init container.
  - ✅ Verify: `kafka-topics.sh --describe --topic event.registered`.

### Phase 4B — Transactional Outbox (event-service)

- [ ] **Step 4.3: Changeset `011-create-outbox-events`**: `outbox_events` (`id` UUID PK, `aggregate_type`, `aggregate_id`, `event_type`, `payload` JSONB, `created_at`, `status` (`NEW`,`SENT`,`FAILED`), `attempts` int, `sent_at`), partial index `WHERE status='NEW'` on `created_at`.
  - ✅ Verify: `\d outbox_events`.

> 💡 **Deep Dive & Theory — The Dual-Write Problem**
> - **Concept**: "save to DB, then publish to Kafka" are two systems with no shared transaction. If the DB commits and the publish fails (or the app crashes between), the event is lost; if you publish first and the DB then rolls back, you emit a **phantom event** for something that never happened. Two-phase commit across them is impractical.
> - **Pitfalls**: `@TransactionalEventListener(AFTER_COMMIT)` + `kafkaTemplate.send` still loses events on crash; sending inside the transaction (phantom events + holding DB connections while waiting on network); ignoring `send()` futures' failures.
> - **Search**: `dual write problem microservices`, `transactional outbox pattern`, `Chris Richardson microservices.io outbox`, `kafka transactions vs outbox`.

> 💡 **Deep Dive & Theory — Transactional Outbox (polling vs CDC)**
> - **Concept**: write the business change **and** an `outbox_events` row in the *same* DB transaction. A separate relay publishes outbox rows to Kafka and marks them sent → at-least-once delivery. **Polling publisher**: `@Scheduled` job selecting `NEW` rows `FOR UPDATE SKIP LOCKED` in batches. **CDC** (Debezium reading the WAL) avoids polling latency/load but adds infrastructure.
> - **Pitfalls**: multiple relay instances double-publishing (use `SKIP LOCKED`); ordering per aggregate (publish in `created_at` order, key by aggregate id); unbounded outbox growth (cleanup job); marking `SENT` before the broker ack; relay running inside a long transaction holding the lock during `send`.
> - **Search**: `outbox pattern polling publisher skip locked`, `debezium outbox event router`, `kafka producer acks=all enable.idempotence`, `at-least-once delivery`.

- [ ] **Step 4.4: Outbox write path**: in `EventService.create`, `RegistrationService.register`, and cancel flows, save the outbox row in the **same transaction**. Event payload DTOs: `EventCreatedMessage`, `EventRegisteredMessage`, `EventCancelledMessage` each with `messageId` (UUID), `occurredAt`, `eventId`, `userId`, `schemaVersion`.
  - ✅ Verify: integration test: after `POST /events` → exactly one `NEW` outbox row; when the business tx rolls back → zero rows.

- [ ] **Step 4.5: Outbox relay** `OutboxPublisher` (`@Scheduled(fixedDelay=...)`): fetch batch via native query `FOR UPDATE SKIP LOCKED`, `kafkaTemplate.send(...)`, wait for ack, mark `SENT`; on failure increment `attempts`, backoff, mark `FAILED` after N. Producer config: `acks=all`, `enable.idempotence=true`, `JsonSerializer` with type headers disabled or a defined contract.
  - ✅ Verify: create an event, then `docker exec <kafka> kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic event.created --from-beginning --max-messages 1`.

- [ ] **Step 4.6: Resilience test**: stop Kafka, create 5 events (API still returns 201), start Kafka → all 5 messages arrive.
  - ✅ Verify: `docker compose stop kafka` → create events → `docker compose start kafka` → Kafka-UI shows 5 messages.

- [ ] **Step 4.7: Outbox cleanup job**: delete `SENT` rows older than N days (batched).
  - ✅ Verify: integration test with a clock override.

### Phase 4C — notification-service

- [ ] **Step 4.8: Module setup** `notification-service`: pom (`web`, `data-jpa`, `validation`, `liquibase-core`, `postgresql`, `spring-kafka`, `lombok`, `mapstruct`, `springdoc`, test deps), `application.yaml` → `notifications_db`, port `8081`, `ddl-auto: validate`.
  - ✅ Verify: `mvn clean compile -pl notification-service`.

- [ ] **Step 4.9: Liquibase**: master changelog; `001-create-notifications` (`id`, `user_id`, `type`, `title`, `body`, `payload` JSONB, `read` bool default false, `created_at`; index `(user_id, created_at DESC)`, index `(user_id) WHERE read=false`); `002-create-processed-messages` (`message_id` UUID PK, `consumer_group`, `processed_at`).
  - ✅ Verify: app boots; `\d notifications` / `\d processed_messages`.

- [ ] **Step 4.10: Entities/Repos/Mappers**: `Notification`, `ProcessedMessage`, `NotificationType` enum (`EVENT_CREATED`, `EVENT_REGISTERED`, `EVENT_CANCELLED`), `NotificationRepository`, `ProcessedMessageRepository`.
  - ✅ Verify: `@DataJpaTest`.

- [ ] **Step 4.11: `@KafkaListener`** consumers for 3 topics (`groupId=notification-service`), manual or batch-safe ack mode, deserialization with `ErrorHandlingDeserializer`, `ConsumerFactory` config (`auto.offset.reset=earliest`, `enable.auto.commit=false`).
  - ✅ Verify: event-service creates an event → a `notifications` row appears: `psql -d notifications_db -c 'select * from notifications;'`.

- [ ] **Step 4.12: `NotificationFormatter`** (message text per type) and `NotificationService.handle(message)` — **single transaction**: insert `processed_messages(message_id)` first (PK violation = duplicate → skip), then insert the notification.
  - ✅ Verify: send the same message twice (`kafka-console-producer`) → exactly one notification row.

> 💡 **Deep Dive & Theory — Consumer Idempotency & Deduplication**
> - **Concept**: Kafka + outbox gives **at-least-once** delivery — duplicates are normal (relay retry, consumer rebalance, redelivery after crash before offset commit). Consumers must be idempotent: store processed `messageId` with a unique constraint in the **same transaction** as the side effect (Inbox pattern), or make the operation naturally idempotent (upsert).
> - **Pitfalls**: dedup by checking a cache then writing (race); committing the offset before the DB commit (message loss) or after (duplicates — acceptable if idempotent); using Kafka `offset` as the business key; dedup table growing forever.
> - **Search**: `idempotent consumer pattern`, `inbox pattern`, `kafka exactly-once semantics myths`, `kafka rebalance duplicate processing`, `commit offset after processing spring kafka AckMode`.

- [ ] **Step 4.13: Retry with exponential backoff + DLQ**: `DefaultErrorHandler` with `ExponentialBackOff` (initial 1s, multiplier 2, max 30s, max elapsed/attempts 5) + `DeadLetterPublishingRecoverer` → `<topic>.DLT`; classify non-retryable exceptions (`DeserializationException`, `IllegalArgumentException`) to skip retries.
  - ✅ Verify: publish a poison message (invalid JSON) → lands in `event.created.DLT` immediately; publish a message that triggers a transient failure (kill `notifications_db` briefly) → retries visible in logs with growing delays, then success after DB returns.

> 💡 **Deep Dive & Theory — Retry Backoff & Dead Letter Queue**
> - **Concept**: transient failures (DB down, timeouts) deserve retries with **exponential backoff + jitter** to avoid hammering a recovering dependency; permanent failures (bad payload) must not block the partition → after N attempts, route the record to a DLQ with error headers for inspection/replay.
> - **Pitfalls**: blocking retries stall the whole partition (head-of-line blocking) — consider non-blocking `@RetryableTopic`; retrying non-retryable errors; DLQ with no alerting or replay tooling; losing original headers/key; infinite retry loops.
> - **Search**: `spring kafka DefaultErrorHandler ExponentialBackOff`, `@RetryableTopic non-blocking retries`, `poison pill message kafka`, `DLQ replay strategy`.

- [ ] **Step 4.14: REST API** — `GET /api/v1/notifications?page=&size=` (current user, newest first), `PATCH /api/v1/notifications/{id}/read`, `POST /api/v1/notifications/read-all`. **Auth**: validate the same JWT (shared secret/public key via config) with a lightweight resource-server filter; user id comes from the token.
  - ✅ Verify: `curl -s localhost:8081/api/v1/notifications -H "Authorization: Bearer $TOKEN" | jq`.

- [ ] **Step 4.15: DLQ inspection tooling** (stretch): `scripts/replay-dlq.sh` or an admin endpoint to re-publish a DLT message.
  - ✅ Verify: replay a poison message after fixing the cause → notification created.

- [ ] **Step 4.16: End-to-end test** (Testcontainers: Postgres ×2 + Kafka): register for an event via event-service → notification visible via notification-service.
  - ✅ Verify: `mvn clean test` (root) green.

- [ ] **Step 4.17 (Iteration 4 DoD)**: kill the consumer, produce 100 events, restart → all 100 processed once; kill the producer mid-relay → no lost/duplicated notifications; write `docs/messaging-notes.md` answering: *"What happens if the DB commit succeeds and the app dies before Kafka send?"*, *"What if the consumer processes but dies before committing the offset?"*

---

# Iteration 5 — Redis Caching, Unread Counters & Production Polish

### Phase 5A — Redis Infrastructure

- [ ] **Step 5.1: Add Redis to `docker-compose.yaml`** (`redis:7`, port `6379`, healthcheck `redis-cli ping`, optional `maxmemory` + `allkeys-lru` policy for the cache).
  - ✅ Verify: `docker exec <redis> redis-cli ping` → `PONG`.

### Phase 5B — event-service Caching

- [ ] **Step 5.2: Dependencies/config**: `spring-boot-starter-data-redis`, `spring-boot-starter-cache`, `@EnableCaching`, `RedisCacheManager` with per-cache TTLs (`events` 10 min, `locations` 30 min, `event-lists` 1 min), JSON serializer (`GenericJackson2JsonRedisSerializer`/typed), key prefix `em:event:`.
  - ✅ Verify: app boots; `redis-cli --scan --pattern 'em:*'`.

- [ ] **Step 5.3: Cache reads**: `@Cacheable("events", key="#id")` on `EventService.getById` (cache **DTOs**, not entities); `@Cacheable("locations")` on `LocationService.getById`.
  - ✅ Verify: call `GET /api/v1/events/1` twice; second call has no SQL in logs; `redis-cli get em:event:1` / `redis-cli ttl em:event:1`.

- [ ] **Step 5.4: Eviction on writes**: `@CacheEvict`/`@CachePut` on update/delete/status change; evict list caches; evict on registration changes if counts are exposed. Evict **after commit** (see pitfall).
  - ✅ Verify: `PUT /events/1`, then `GET /events/1` shows new data (no stale read); `redis-cli ttl` resets.

> 💡 **Deep Dive & Theory — Cache-Aside vs Read-Through / Write-Through**
> - **Concept**: **Cache-Aside** (lazy loading): app checks cache → on miss loads DB → populates cache; writes go to DB and invalidate/update the cache. App controls everything; cache failures can degrade gracefully. **Read-Through**: cache layer itself loads from the DB on miss. **Write-Through**: writes go to the cache which synchronously writes to the DB (consistent, slower writes). **Write-Behind**: async DB writes (fast, risk of data loss).
> - **Pitfalls**: evicting *before* the DB commit (another request re-caches the old value → stale forever until TTL); caching entities with lazy proxies; unbounded key cardinality for list/filter caches; no TTL; treating the cache as the source of truth; cache outage taking the API down (add fallback/`CacheErrorHandler`).
> - **Search**: `cache aside vs read through vs write through`, `spring cache redis ttl per cache`, `cache invalidation race condition`, `TransactionAwareCacheManagerProxy`, `CacheErrorHandler`.

- [ ] **Step 5.5: TTL jitter**: randomize TTL (±10–20%) via custom `RedisCacheWriter`/`RedisCacheConfiguration` wrapper.
  - ✅ Verify: warm 100 keys, `redis-cli --scan --pattern 'em:event:*' | xargs -I{} redis-cli ttl {}` shows varied TTLs.

- [ ] **Step 5.6: Stampede protection** for hot keys: `@Cacheable(sync = true)` (single-JVM) and/or distributed lock (`SET lock:key NX PX 5000`) / request coalescing; consider caching negative results briefly (null object) against cache penetration.
  - ✅ Verify: load test `hey -n 2000 -c 100 http://localhost:8080/api/v1/events/1` right after `redis-cli flushall` → DB query count for that event = 1 (check logs/metrics).

> 💡 **Deep Dive & Theory — Cache Stampede / Thundering Herd**
> - **Concept**: a hot key expires and hundreds of concurrent requests all miss and hit the DB simultaneously. Mitigations: **TTL jitter** (spread expirations), **single-flight/locking** (one loader, others wait), **stale-while-revalidate**/early refresh, and cache warming.
> - **Pitfalls**: `sync=true` only protects within one JVM; distributed lock without a timeout/fencing (dead holder blocks everyone); same TTL for all keys created at deploy time; cache penetration (requests for non-existent ids bypass the cache) — use negative caching/Bloom filter.
> - **Search**: `cache stampede thundering herd`, `redis distributed lock redlock criticism`, `probabilistic early expiration`, `cache penetration vs breakdown vs avalanche`.

- [ ] **Step 5.7: Cache metrics**: hit/miss counters (Micrometer `cache.gets`), log cache errors.
  - ✅ Verify: `curl localhost:8080/actuator/metrics/cache.gets?tag=result:hit`.

### Phase 5C — notification-service Unread Counters

- [ ] **Step 5.8: Redis config in notification-service**: `StringRedisTemplate`; key format `unread:{userId}`.
  - ✅ Verify: `redis-cli ping` from the service's health endpoint (`/actuator/health`).

- [ ] **Step 5.9: Increment on new notification**: in the consumer, **after** the DB transaction commits (dedup already ensures once-per-message), `INCR unread:{userId}`.
  - ✅ Verify: produce a notification → `redis-cli get unread:42` → `1`.

- [ ] **Step 5.10: `GET /api/v1/notifications/unread-count`** → read Redis `GET unread:{userId}` (missing key → fallback `SELECT count(*) WHERE read=false` and re-seed Redis).
  - ✅ Verify: `curl -s localhost:8081/api/v1/notifications/unread-count -H "Authorization: Bearer $TOKEN"` → `{"count":N}`.

- [ ] **Step 5.11: Decrement/reset**: mark-one-read → `DECR` (never below 0 — Lua script or guard), read-all → `SET unread:{userId} 0` / `DEL`.
  - ✅ Verify: `PATCH /notifications/{id}/read` → counter decreases; `POST /notifications/read-all` → `0`.

- [ ] **Step 5.12: Reconciliation job** (`@Scheduled`): recompute counters from DB for drift repair (Redis restart, partial failure).
  - ✅ Verify: `redis-cli flushall` → `GET unread-count` still returns the correct value.

> 💡 **Deep Dive & Theory — Redis atomic counters vs transactional DB counting**
> - **Concept**: `INCR`/`DECR` are atomic and O(1) in Redis's single-threaded executor — no read-modify-write race, ideal for high-frequency counters and instant reads. A DB `COUNT(*)` is always consistent but slower at scale and contends with writes.
> - **Pitfalls**: Redis and Postgres can **diverge** (INCR succeeds but the DB tx rolled back, or vice versa; Redis restarts without persistence) → need reconciliation + DB as source of truth; `DECR` going negative; non-atomic `GET` then `SET`; incrementing before commit; missing TTL/cleanup for inactive users; hot-key for a single user is rarely an issue, but mass keys need memory planning.
> - **Search**: `redis INCR atomic counter`, `redis lua script atomic decrement floor zero`, `redis persistence RDB AOF`, `dual write redis postgres consistency`, `redis counter vs database count`.

### Phase 5D — Production Polish

- [ ] **Step 5.13: Dockerfiles** (multi-stage, JRE 21, non-root user, `HEALTHCHECK`) for both services.
  - ✅ Verify: `docker build -t event-service -f event-service/Dockerfile .`

- [ ] **Step 5.14: Full `docker-compose.yaml`**: services `postgres`, `kafka`, `kafka-ui`, `redis`, `event-service`, `notification-service` with `depends_on: condition: service_healthy`, env-based config (`.env` file, gitignored; `.env.example` committed), restart policies, shared network.
  - ✅ Verify: `docker compose down -v && docker compose up -d --build && docker compose ps` — all healthy.

- [ ] **Step 5.15: Observability**: Actuator (`health`, `metrics`, `prometheus`), Micrometer Prometheus registry, structured logs with correlation id (MDC `traceId`, propagated via Kafka headers), optional Prometheus + Grafana in compose.
  - ✅ Verify: `curl localhost:8080/actuator/prometheus | head`; a request's `traceId` appears in both services' logs.

- [ ] **Step 5.16: Config hygiene**: Spring profiles (`local`, `docker`, `test`), no secrets in git, `JWT_SECRET` from env, connection pool sizing (HikariCP `maximum-pool-size`), Kafka/Redis timeouts.
  - ✅ Verify: `grep -rn "password" --include=*.yaml .` shows only env placeholders.

- [ ] **Step 5.17: End-to-end smoke test script** `scripts/smoke.sh`: register → login → create location → create event → publish → second user registers → poll notifications → check unread-count = 1 → mark read → count = 0.
  - ✅ Verify: `bash scripts/smoke.sh` exits `0`.

- [ ] **Step 5.18: CI pipeline** (GitHub Actions): `mvn -B clean verify` with Testcontainers, cache `~/.m2`.
  - ✅ Verify: green pipeline on a PR.

- [ ] **Step 5.19 (Final DoD)**: README with architecture diagram, run instructions, API overview; `docs/` notes complete; you can answer from memory: overbooking prevention, outbox vs 2PC, consumer idempotency, cache stampede, Redis/DB drift.
  - ✅ Verify: fresh clone → `docker compose up -d --build` → `bash scripts/smoke.sh` passes.

---

## Progress tracker

| Iteration | Steps | Status |
|---|---|---|
| 1 — Foundation & Persistence | 1.0 – 1.25 | ☐ |
| 2 — Security & RBAC | 2.1 – 2.17 | ☐ |
| 3 — Concurrency & Business Logic | 3.1 – 3.15 | ☐ |
| 4 — Kafka & Notifications | 4.1 – 4.17 | ☐ |
| 5 — Redis & Polish | 5.1 – 5.19 | ☐ |
