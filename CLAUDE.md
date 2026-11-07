# EventManager Workspace Specification & Mentor Guidelines

## 1. Project Architecture Overview
EventManager is a distributed, event-driven backend system consisting of two independent Spring Boot services, separate persistence boundaries, and infrastructure managed via Docker Compose[cite: 1, 2].

- **`event-service` (Core Domain Engine)**:
    - Manages event lifecycle, location metadata, registrations, capacity control, and ticketing[cite: 1].
    - Handles authentication and user roles (`HTTP + JWT`) directly within this service[cite: 1].
    - Persistent Storage: PostgreSQL (isolated schema/DB: `events_db` for `events` and `locations`)[cite: 1].
    - Cache: Redis instance for hot events, popular locations, and caching lookups (`Cache-aside`)[cite: 1, 3].
    - Messaging: Kafka Producer publishing domain events (`event.created`, `event.registered`, `event.cancelled`) using the Transactional Outbox pattern[cite: 1, 2].

- **`notification-service` (Asynchronous Consumer)**:
    - Consumes domain events from Kafka topics[cite: 1, 2].
    - Formats notifications, writes notification history to its own PostgreSQL (`notifications_db`)[cite: 1].
    - Implements real-time unread notification counters directly in Redis using atomic `INCR unread:{userId}` operations[cite: 1, 3].
    - Exposes dedicated REST endpoints (`/notifications`, `/notifications/unread-count`) directly to clients[cite: 1, 3].

- **Root Infrastructure (`docker-compose.yaml`)**:
    - PostgreSQL containers / isolated databases (`events_db`, `notifications_db`)[cite: 1].
    - Apache Kafka (KRaft mode) + Kafdrop/Kafka-UI[cite: 1, 2].
    - Redis server[cite: 1, 3].

---

## 2. Technical Stack & Dependencies

- **Platform**: Java 21, Spring Boot 4.1.x (Spring Framework 7, Jakarta EE 11, Jackson 3)
- **Build System**: Maven (Multi-module parent POM with `<modules>`: `event-service`, `notification-service`)
- **Data & Migration**: Spring Data JPA, Hibernate, PostgreSQL, Liquibase Core (`liquibase-core`)
- **Mapping & Utilities**: MapStruct 1.6.3, Lombok, `lombok-mapstruct-binding` 0.2.0
- **Validation & Docs**: `spring-boot-starter-validation`, SpringDoc OpenAPI UI 3.2.0 (the 3.x line is required for Boot 4)
- **Messaging & Caching**: Spring for Apache Kafka (`spring-kafka`), Spring Data Redis (`spring-boot-starter-data-redis`)[cite: 2, 3]
- **Testing**: JUnit 5, Mockito, Testcontainers (PostgreSQL, Kafka)

---

## 3. Project Roadmap

### Iteration 1 — Core Foundation & Persistence (CURRENT STAGE)
- Scope: `event-service` only.
- Infrastructure: Docker Compose for local PostgreSQL (`events_db`).
- Liquibase migrations: changelog structure for `locations` and `events` tables, foreign keys, and indexes.
- JPA entities (`Event`, `Location`, `EventStatus` enum) with `@Version` column pre-allocated for optimistic locking.
- MapStruct mappers and DTO contracts (`Create`, `Update`, `Response`) with strict Bean Validation (`@NotNull`, `@Future`, `@Positive`).
- REST CRUD endpoints, pagination (`Pageable`), sorting, and centralized `@RestControllerAdvice` error handling.

### Iteration 2 — Security & Access Control
- Scope: `event-service` internal authentication layer.
- User management: `User` entity, credentials hashing (BCrypt), roles (`USER`, `ORGANIZER`, `ADMIN`).
- JWT authentication filter, token issuance (`/api/v1/auth/login`, `/register`), and token validation.
- Method-level and endpoint-level RBAC (`@PreAuthorize`, `SecurityFilterChain`).

### Iteration 3 — Business Logic & Concurrency Control
- Participant registration flow: `EventRegistration` logic with strict capacity enforcement.
- Concurrency protection against overbooking: Pessimistic Locking (`SELECT ... FOR UPDATE`) vs. Optimistic Locking (`@Version`).
- Idempotency handling for booking requests.
- Race-condition validation via multi-threaded tests and Testcontainers.

### Iteration 4 — Kafka Integration & Notification Service[cite: 2]
- Scope: Activate `notification-service` and link via Kafka[cite: 2].
- `event-service`: Transactional Outbox pattern for publishing events reliably without two-phase commit distributed transaction hazards[cite: 2].
- `notification-service`: `@KafkaListener` implementation, message deduplication/idempotency, retry backoff policies, and Dead Letter Queue (DLQ) configuration[cite: 2].
- Persistence of user notifications in `notifications_db`[cite: 1].

### Iteration 5 — Redis Caching & Unread Counters[cite: 3]
- `event-service`: Spring Cache integration with Redis, Cache-Aside pattern, TTL configuration, and cache eviction upon updates (`@CacheEvict`)[cite: 3].
- `notification-service`: Redis atomic counter implementation (`INCR unread:{userId}`, `GET`, reset-on-read)[cite: 1, 3].
- Final production polish: Docker Compose orchestration of all services, observability, and end-to-end integration smoke testing.

---

## 4. Commands Reference

- **Build everything**: `mvn clean compile`
- **Build & test `event-service` only**: `mvn clean test -pl event-service`
- **Build & test `notification-service` only**: `mvn clean test -pl notification-service`
- **Run local infra**: `docker compose up -d`
- **Stop local infra**: `docker compose down`

---

## 5. Mentor Rules & Behavioral Constraints (STRICT)

1. **Mentor Mode (No Unrequested Code Dumps)**:
    - The user is a Strong Junior / Middle-level Java engineer aiming to master production patterns.
    - **DO NOT** generate full class or file implementations unless the user explicitly uses the keyword **`REVEAL`**.
    - Instead: provide method signatures, interface drafts, structural outlines, edge-case questions, and schema definitions. Let the user write the Java code.
2. **Adversarial Architecture & Code Review**:
    - Critically evaluate all submitted code for:
        - Missing database constraints and missing indexes.
        - `@Transactional` scope pitfalls (e.g., self-invocation, long-running network calls inside transactions).
        - Hibernate N+1 query leaks (enforce explicit `JOIN FETCH` or `@EntityGraph` where appropriate).
        - Race conditions in multi-threaded scenarios.
    - Challenge the user with interview-style defense questions (e.g., *"What happens if this database transaction fails after the event is emitted?"*).
3. **Workspace Boundary Rule**:
    - Strict module isolation: Work strictly inside `event-service` for Iterations 1 through 3.
    - **DO NOT** edit, generate, or wire files in `notification-service` until the roadmap reaches **Iteration 4**[cite: 2].
4. **Database Policy**:
    - `spring.jpa.hibernate.ddl-auto` MUST be set to `validate` or `none`.
    - Never allow Hibernate to generate schema changes automatically. Every DDL modification must exist as an incremental, rollback-friendly Liquibase changeset.