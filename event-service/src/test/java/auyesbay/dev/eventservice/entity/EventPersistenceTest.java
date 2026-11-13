package auyesbay.dev.eventservice.entity;

import auyesbay.dev.eventservice.config.JpaAuditingConfig;
import auyesbay.dev.eventservice.domain.EventStatus;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import org.hibernate.Hibernate;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
public class EventPersistenceTest {

    @Container
    @ServiceConnection
    public static final PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("postgres:16");

    public final TestEntityManager testEntityManager;

    @Autowired
    public EventPersistenceTest(TestEntityManager testEntityManager) {
        this.testEntityManager = testEntityManager;
    }

    Location createValidLocationForEventTests() {
        Location location = new Location();
        location.setName("Name");
        location.setAddress("Address");
        location.setCity("City");
        location.setCountry("Country");
        location.setCapacity(100);

        return location;
    }

    Location createValidLocationForEventTests(String name, String address) {
        Location location = new Location();
        location.setName(name);
        location.setAddress(address);
        location.setCity("City");
        location.setCountry("Country");
        location.setCapacity(100);

        return location;
    }

    // Test 1
    @Test
    void shouldPersistAndReloadEvent() {
        Location location = testEntityManager.persistAndFlush(createValidLocationForEventTests());

        Event event = new Event();
        event.setTitle("Title");
        event.setDescription("Description");
        event.setLocation(location);
        event.setStartTime(Instant.parse("2026-11-12T10:15:30.00Z"));
        event.setEndTime(Instant.parse("2026-11-12T10:16:30.00Z"));
        event.setMaxParticipants(100);
        event.setStatus(EventStatus.DRAFT);

        testEntityManager.persistAndFlush(event);
        testEntityManager.clear();

        Event foundEvent = testEntityManager.find(Event.class, event.getId());

        assertEquals(foundEvent.getTitle(), event.getTitle());
        assertEquals(foundEvent.getStatus(), event.getStatus());
        assertEquals(foundEvent.getMaxParticipants(), event.getMaxParticipants());
        assertEquals(foundEvent.getLocation().getId(), event.getLocation().getId());
        assertEquals(foundEvent.getStartTime(), event.getStartTime());
        assertEquals(foundEvent.getEndTime(), event.getEndTime());
    }

    // Test 2
    @Test
    void shouldStoreStatusAsText() {
        Location location = testEntityManager.persistAndFlush(createValidLocationForEventTests());

        Event event = new Event();
        event.setTitle("Title");
        event.setDescription("Description");
        event.setLocation(location);
        event.setStartTime(Instant.parse("2026-11-12T10:15:30.00Z"));
        event.setEndTime(Instant.parse("2026-11-12T10:16:30.00Z"));
        event.setMaxParticipants(100);
        event.setStatus(EventStatus.PUBLISHED);

        testEntityManager.persistAndFlush(event);

        Query query = testEntityManager.getEntityManager().createNativeQuery(
                "select status from events where id = ?1");
        query.setParameter(1, event.getId());
        var savedStatus = query.getSingleResult().toString();

        assertEquals("PUBLISHED", savedStatus);
    }

    // Test 3
    @Test
    void shouldRejectEndBeforeStart() {
        Location location = testEntityManager.persistAndFlush(createValidLocationForEventTests());

        Event event = new Event();
        event.setTitle("Title");
        event.setDescription("Description");
        event.setLocation(location);
        event.setStartTime(Instant.parse("2026-11-12T10:16:30.00Z"));
        event.setEndTime(Instant.parse("2026-11-12T10:15:30.00Z"));
        event.setMaxParticipants(100);
        event.setStatus(EventStatus.DRAFT);

        assertThatThrownBy(() -> testEntityManager.persistAndFlush(event))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("ck_events_end_time_bigger");
    }

    // Test 4
    @ParameterizedTest()
    @ValueSource(ints = {0, -1})
    void shouldRejectNonPositiveMaxParticipants(int maxParticipant) {
        Location location = testEntityManager.persistAndFlush(createValidLocationForEventTests());

        Event event = new Event();
        event.setTitle("Title");
        event.setDescription("Description");
        event.setLocation(location);
        event.setStartTime(Instant.parse("2026-11-12T10:15:30.00Z"));
        event.setEndTime(Instant.parse("2026-11-12T10:16:30.00Z"));
        event.setMaxParticipants(maxParticipant);
        event.setStatus(EventStatus.DRAFT);

        assertThatThrownBy(() -> testEntityManager.persistAndFlush(event))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("ck_events_max_participants_positive");
    }

    // Test 5
    @Test
    void shouldRejectUnknownStatusInDatabase() {
        // Save the location so it exists in the database and has an ID
        Location location = testEntityManager.persistAndFlush(createValidLocationForEventTests());

        Query query = testEntityManager.getEntityManager().createNativeQuery("""
                        insert into events (id, title, description, location_id, start_time, end_time, max_participants, status)
                        values (nextval('events_seq'), :title, :description, :locationId, :startTime, :endTime, :maxParticipants, :status)
                        """)
                .setParameter("title", "Title")
                .setParameter("description", "Description")
                .setParameter("locationId", location.getId())
                .setParameter("startTime", Timestamp.from(Instant.parse("2026-11-12T10:15:30.00Z")))
                .setParameter("endTime", Timestamp.from(Instant.parse("2026-11-12T10:16:30.00Z")))
                .setParameter("maxParticipants", 100)
                .setParameter("status", "BOGUS");

        assertThatThrownBy(query::executeUpdate)
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("ck_events_status");
    }

    // Test 6
    @Test
    void shouldRejectUnknownLocation() {
        Query query = testEntityManager.getEntityManager().createNativeQuery("""
                        insert into events (id, title, description, location_id, start_time, end_time, max_participants, status)
                        values (nextval('events_seq'), :title, :description, :locationId, :startTime, :endTime, :maxParticipants, :status)
                        """)
                .setParameter("title", "Title")
                .setParameter("description", "Description")
                .setParameter("locationId", 999999L)
                .setParameter("startTime", Timestamp.from(Instant.parse("2026-11-12T10:15:30.00Z")))
                .setParameter("endTime", Timestamp.from(Instant.parse("2026-11-12T10:16:30.00Z")))
                .setParameter("maxParticipants", 5)
                .setParameter("status", "DRAFT");

        assertThatThrownBy(query::executeUpdate)
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("fk_events_locations");
    }

    // Test 7
    @Test
    void shouldNotDeleteLocationInUse() {
        Location location = testEntityManager.persistAndFlush(createValidLocationForEventTests());

        Event event = new Event();
        event.setTitle("Title");
        event.setDescription("Description");
        event.setLocation(location);
        event.setStartTime(Instant.parse("2026-11-12T10:15:30.00Z"));
        event.setEndTime(Instant.parse("2026-11-12T10:16:30.00Z"));
        event.setMaxParticipants(100);
        event.setStatus(EventStatus.DRAFT);

        testEntityManager.persistAndFlush(event);
        testEntityManager.clear();

        Location foundLocation = testEntityManager.find(Location.class, location.getId());

        assertThatThrownBy(() -> {
            testEntityManager.remove(foundLocation);
            testEntityManager.flush();
        })
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("fk_events_locations");
    }

    // Test 8
    @Test
    void shouldLoadLocationLazily() {
        Location location = testEntityManager.persistAndFlush(createValidLocationForEventTests());

        Event event = new Event();
        event.setTitle("Title");
        event.setDescription("Description");
        event.setLocation(location);
        event.setStartTime(Instant.parse("2026-11-12T10:15:30.00Z"));
        event.setEndTime(Instant.parse("2026-11-12T10:16:30.00Z"));
        event.setMaxParticipants(100);
        event.setStatus(EventStatus.DRAFT);

        testEntityManager.persistAndFlush(event);
        testEntityManager.clear();

        Event foundEvent = testEntityManager.find(Event.class, event.getId());
        assertFalse(Hibernate.isInitialized(foundEvent.getLocation()));
        String dummy = foundEvent.getLocation().getName();
        assertTrue(Hibernate.isInitialized(foundEvent.getLocation()));
    }

    // Test 9
    @Test
    void shouldAllowNullDescription() {
        Location location = testEntityManager.persistAndFlush(createValidLocationForEventTests());

        Event event = new Event();
        event.setTitle("Title");
        event.setDescription(null);
        event.setLocation(location);
        event.setStartTime(Instant.parse("2026-11-12T10:15:30.00Z"));
        event.setEndTime(Instant.parse("2026-11-12T10:16:30.00Z"));
        event.setMaxParticipants(100);
        event.setStatus(EventStatus.DRAFT);

        testEntityManager.persistAndFlush(event);
        testEntityManager.clear();

        Event foundEvent = testEntityManager.find(Event.class, event.getId());

        assertNull(foundEvent.getDescription());
    }

    // Test 10
    @Test
    void shouldBumpVersionOnUpdate() {
        Location location = testEntityManager.persistAndFlush(createValidLocationForEventTests());

        Event event = new Event();
        event.setTitle("Title");
        event.setDescription("");
        event.setLocation(location);
        event.setStartTime(Instant.parse("2026-11-12T10:15:30.00Z"));
        event.setEndTime(Instant.parse("2026-11-12T10:16:30.00Z"));
        event.setMaxParticipants(100);
        event.setStatus(EventStatus.DRAFT);

        testEntityManager.persistAndFlush(event);

        Long oldVersion = event.getVersion();

        event.setTitle("New Title");
        testEntityManager.flush();

        assertEquals(oldVersion + 1, event.getVersion());
    }

    // Tests 11
    @Test
    void shouldNotBeEqualWhenBothEventsAreUnsaved() {
        Location location = testEntityManager.persistAndFlush(createValidLocationForEventTests());

        Event event1 = new Event();
        event1.setTitle("Title");
        event1.setDescription("");
        event1.setLocation(location);
        event1.setStartTime(Instant.parse("2026-11-12T10:15:30.00Z"));
        event1.setEndTime(Instant.parse("2026-11-12T10:16:30.00Z"));
        event1.setMaxParticipants(100);
        event1.setStatus(EventStatus.DRAFT);

        Event event2 = new Event();
        event2.setTitle("Title");
        event2.setDescription("");
        event2.setLocation(location);
        event2.setStartTime(Instant.parse("2026-11-12T10:15:30.00Z"));
        event2.setEndTime(Instant.parse("2026-11-12T10:16:30.00Z"));
        event2.setMaxParticipants(100);
        event2.setStatus(EventStatus.DRAFT);

        assertNotEquals(event1, event2);
    }

    @Test
    void shouldBeEqualToItself() {
        Location location = testEntityManager.persistAndFlush(createValidLocationForEventTests());

        Event event = new Event();
        event.setTitle("Title");
        event.setDescription("");
        event.setLocation(location);
        event.setStartTime(Instant.parse("2026-11-12T10:15:30.00Z"));
        event.setEndTime(Instant.parse("2026-11-12T10:16:30.00Z"));
        event.setMaxParticipants(100);
        event.setStatus(EventStatus.DRAFT);

        assertEquals(event, event);
    }

    @Test
    void shouldBeEqualWhenSameRowIsLoadedTwice() {
        Location location = testEntityManager.persistAndFlush(createValidLocationForEventTests());

        Event event = new Event();
        event.setTitle("Title");
        event.setDescription("");
        event.setLocation(location);
        event.setStartTime(Instant.parse("2026-11-12T10:15:30.00Z"));
        event.setEndTime(Instant.parse("2026-11-12T10:16:30.00Z"));
        event.setMaxParticipants(100);
        event.setStatus(EventStatus.DRAFT);

        testEntityManager.persistAndFlush(event);
        testEntityManager.clear();

        Event foundEvent1 = testEntityManager.find(Event.class, event.getId());
        testEntityManager.clear();
        Event foundEvent2 = testEntityManager.find(Event.class, event.getId());

        assertNotSame(foundEvent1, foundEvent2);
        assertEquals(foundEvent1, foundEvent2);
    }

    @Test
    void shouldNotBeEqualWhenIdsDiffer() {
        Location location1 = testEntityManager.persistAndFlush(
                createValidLocationForEventTests("Name 1", "Address 1")
        );

        Event event1 = new Event();
        event1.setTitle("Title");
        event1.setDescription("");
        event1.setLocation(location1);
        event1.setStartTime(Instant.parse("2026-11-12T10:15:30.00Z"));
        event1.setEndTime(Instant.parse("2026-11-12T10:16:30.00Z"));
        event1.setMaxParticipants(100);
        event1.setStatus(EventStatus.DRAFT);

        Location location2 = testEntityManager.persistAndFlush(
                createValidLocationForEventTests("Name 2", "Address 2")
        );

        Event event2 = new Event();
        event2.setTitle("Title");
        event2.setDescription("");
        event2.setLocation(location2);
        event2.setStartTime(Instant.parse("2026-11-12T10:15:30.00Z"));
        event2.setEndTime(Instant.parse("2026-11-12T10:16:30.00Z"));
        event2.setMaxParticipants(100);
        event2.setStatus(EventStatus.DRAFT);

        testEntityManager.persistAndFlush(event1);
        testEntityManager.persistAndFlush(event2);

        assertNotEquals(event1, event2);
    }

    @Test
    void shouldStayFindableInHashSetAfterPersist() {
        Location location = testEntityManager.persistAndFlush(createValidLocationForEventTests());

        Event event = new Event();
        event.setTitle("Title");
        event.setDescription("");
        event.setLocation(location);
        event.setStartTime(Instant.parse("2026-11-12T10:15:30.00Z"));
        event.setEndTime(Instant.parse("2026-11-12T10:16:30.00Z"));
        event.setMaxParticipants(100);
        event.setStatus(EventStatus.DRAFT);

        HashSet<Event> hashSet = new HashSet<Event>();
        hashSet.add(event);

        testEntityManager.persistAndFlush(event);

        assertTrue(hashSet.contains(event));
    }

    @Test
    void shouldNotBeEqualToNullOrOtherType() {
        Location location = testEntityManager.persistAndFlush(createValidLocationForEventTests());

        Event event = new Event();
        event.setTitle("Title");
        event.setDescription("");
        event.setLocation(location);
        event.setStartTime(Instant.parse("2026-11-12T10:15:30.00Z"));
        event.setEndTime(Instant.parse("2026-11-12T10:16:30.00Z"));
        event.setMaxParticipants(100);
        event.setStatus(EventStatus.DRAFT);

        assertNotEquals(event, null);
        assertNotEquals(event, location);
    }
}
