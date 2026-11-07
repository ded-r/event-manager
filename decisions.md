# Design Decisions (Iteration 1)

Each decision: what we chose, why, and what to remember. If you change your mind later, add a new Liquibase changeset. Never edit one that already ran.

---

## 1. IDs: `BIGINT` with a sequence

**Choice:** every table gets a `BIGINT` id that comes from a database sequence.

**Why:**
- We have one write database, so there is no problem of two servers creating the same id. That is the main reason people pick UUID, and we don't have it.
- A `BIGINT` takes 8 bytes. A UUID takes 16. Ids are copied into every index and every foreign key, so the small size makes the whole database smaller and faster.
- Random UUIDs are inserted all over the index, not at the end. This makes inserts slower on big tables.
- "Ids are easy to guess" is not a real problem if we check permissions properly. Iteration 2 does that.

**Detail to remember:** use `GenerationType.SEQUENCE` with `allocationSize = 50`, not `IDENTITY`. `IDENTITY` stops Hibernate from sending many inserts in one batch. In Liquibase the sequence must use `incrementBy: 50`. If the two numbers don't match, ids can collide or `validate` fails.

**Exception:** Kafka messages (Iteration 4) get a UUID `messageId`. That id is created in the code, not by one database, and it is used to detect duplicates.

---

## 2. Time: `Instant` in Java, `TIMESTAMPTZ` in PostgreSQL

**Choice:** `Instant` for `start_time`, `end_time`, `created_at`, `updated_at`. In the database, the type is `TIMESTAMPTZ`.

**Why:**
- An `Instant` is one exact moment in time (like a Unix timestamp). It does not depend on a time zone, so there is no confusion between users in different countries.
- `LocalDateTime` has no zone. "19:00" could be anywhere. Avoid it.
- JSON output looks like `2026-12-01T19:00:00Z`. The client converts it to the user's local time.

**Detail to remember:** set `hibernate.jdbc.time_zone: UTC` in `application.yml` so everything is saved the same way.

---

## 3. Deleting a location that has events: `ON DELETE RESTRICT`

**Choice:** the database refuses to delete a location if any event uses it. The API returns `409 Conflict`.

**Why:**
- `CASCADE` would silently delete all events at that location. In Iteration 3 it would delete their registrations too. This is data loss.
- `SET NULL` does not work, because `location_id` is `NOT NULL`.
- `RESTRICT` is the safe choice. The user must move or delete the events first.

---

## 4. Event size must fit the location: check in the service layer

**Choice:** when creating or updating an event, the service checks `max_participants <= location.capacity`. If not, it returns `400`.

**Why:**
- A database `CHECK` cannot look at another table.
- A trigger would work, but it hides business logic inside the database where it is hard to test and review.

**Known weakness:** if someone lowers a location's capacity at the same moment someone creates an event there, the rule can be broken. To reduce this, the location update must reject a capacity that is smaller than any active event's `max_participants`. We accept the tiny remaining risk in Iteration 1.

*(This rule is our own addition. CLAUDE.md does not ask for it.)*

---

## 5. Event status: `VARCHAR(20)` + a named `CHECK` constraint

**Choice:** store status as text (`DRAFT`, `PUBLISHED`, `CANCELLED`, `COMPLETED`). The constraint is named `ck_events_status`. In Java: `@Enumerated(EnumType.STRING)`.

**Why:**
- To add a new status later (for example `POSTPONED`), a new changeset drops the old constraint and creates a new one. That is easy.
- A PostgreSQL `ENUM` type is hard to change. You can add values, but you cannot remove them.
- A separate lookup table is too much for four values.
- Never use `EnumType.ORDINAL`. If you reorder the enum, all old data silently changes meaning.

**Allowed status changes (write these as a Java test, Step 1.10):**

| From | To |
|---|---|
| `DRAFT` | `PUBLISHED`, `CANCELLED` |
| `PUBLISHED` | `COMPLETED`, `CANCELLED` |
| `CANCELLED` | nothing (final) |
| `COMPLETED` | nothing (final) |

---

## 6. Timestamps: set by the database and by JPA

**Choice:** `created_at` and `updated_at` are `NOT NULL` with `DEFAULT now()` in the database. JPA auditing also fills them in.

**Why:** the default protects us if someone inserts a row with plain SQL or a migration script. JPA auditing keeps `updated_at` correct on every change made through the app.

---

## 7. Version column on every table

**Choice:** `version BIGINT NOT NULL DEFAULT 0` on `locations` and `events`, mapped with `@Version`.

**Why:** two people editing the same row at the same time will not overwrite each other silently. The second save fails with an optimistic-lock error, and the API returns `409`. We need the same column in Iteration 3 for registrations.

---

## 8. Database rules, always

- `spring.jpa.hibernate.ddl-auto` is `validate`. Hibernate never changes the schema.
- Every schema change is a Liquibase changeset with a `rollback` block.
- Every foreign key column gets an index. PostgreSQL does not do this automatically.
- Important rules live in the database too (`CHECK`, `UNIQUE`, `NOT NULL`), not only in Java validation. Java validation only protects the API. The database protects the data.
