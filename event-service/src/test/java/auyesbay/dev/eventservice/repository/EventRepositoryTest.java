package auyesbay.dev.eventservice.repository;

import auyesbay.dev.eventservice.config.JpaAuditingConfig;
import auyesbay.dev.eventservice.domain.EventStatus;
import auyesbay.dev.eventservice.entity.Event;
import auyesbay.dev.eventservice.entity.Location;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Testcontainers
@AutoConfigureTestDatabase(replace = NONE)
@Import(JpaAuditingConfig.class)
public class EventRepositoryTest {

    @Container
    @ServiceConnection
    public static final PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("postgres:16");

    private static final Instant BASE = Instant.parse("2026-12-01T10:00:00Z");

    public final EventRepository eventRepository;
    public final TestEntityManager testEntityManager;

    @Autowired
    public EventRepositoryTest(EventRepository eventRepository, TestEntityManager testEntityManager) {
        this.eventRepository = eventRepository;
        this.testEntityManager = testEntityManager;
    }

    private Location saveLocation(String name) {
        Location location = new Location();
        location.setName(name);
        location.setAddress("Address");
        location.setCity("City");
        location.setCountry("Country");
        location.setCapacity(100);

        testEntityManager.persist(location);

        return location;
    }

    private Event saveEvent(Location location, String title, Instant startTime, EventStatus eventStatus) {
        Event event = new Event();
        event.setTitle(title);
        event.setDescription(title);
        event.setLocation(location);
        event.setStartTime(startTime);
        event.setEndTime(startTime.plus(1, ChronoUnit.HOURS));
        event.setMaxParticipants(100);
        event.setStatus(eventStatus);

        testEntityManager.persist(event);

        return event;
    }

    // Test 1
    @Test
    void findByStatusReturnsOnlyThatStatus() {
        saveEvent(saveLocation("Location 1"), "Event1", BASE, EventStatus.DRAFT);
        saveEvent(saveLocation("Location 2"), "Event2", BASE, EventStatus.DRAFT);
        saveEvent(saveLocation("Location 3"), "Event3", BASE, EventStatus.PUBLISHED);
        saveEvent(saveLocation("Location 4"), "Event4", BASE, EventStatus.PUBLISHED);
        saveEvent(saveLocation("Location 5"), "Event5", BASE, EventStatus.CANCELLED);

        testEntityManager.flush();
        testEntityManager.clear();

        Page<Event> foundEvents = eventRepository.findByStatus(EventStatus.PUBLISHED, PageRequest.of(0, 10));
        assertThat(foundEvents.getContent()).extracting(Event::getTitle).containsExactlyInAnyOrder("Event3",
                "Event4");
        assertEquals(2, foundEvents.getTotalElements());
    }

    // Test 2
    @Test
    void findByStatusRespectsPaging() {
        saveEvent(saveLocation("Location 1"), "Event1", BASE, EventStatus.DRAFT);
        saveEvent(saveLocation("Location 2"), "Event2", BASE, EventStatus.DRAFT);
        saveEvent(saveLocation("Location 3"), "Event3", BASE, EventStatus.DRAFT);
        saveEvent(saveLocation("Location 4"), "Event4", BASE, EventStatus.DRAFT);
        saveEvent(saveLocation("Location 5"), "Event5", BASE, EventStatus.DRAFT);

        testEntityManager.flush();
        testEntityManager.clear();

        Page<Event> foundEvents = eventRepository.findByStatus(EventStatus.DRAFT, PageRequest.of(0, 2));

        assertThat(foundEvents.getTotalElements()).isEqualTo(5);
        assertThat(foundEvents.getContent().size()).isEqualTo(2);
        assertThat(foundEvents.getTotalPages()).isEqualTo(3);
    }

    // Test 3
    @Test
    void findByLocationIdReturnsOnlyThatLocation() {
        Location a = saveLocation("A");
        Location b = saveLocation("B");
        saveEvent(a, "A1", BASE, EventStatus.DRAFT);
        saveEvent(a, "A2", BASE, EventStatus.DRAFT);
        saveEvent(b, "B1", BASE, EventStatus.DRAFT);
        saveEvent(b, "B2", BASE, EventStatus.DRAFT);
        saveEvent(b, "B3", BASE, EventStatus.DRAFT);
        testEntityManager.flush();
        testEntityManager.clear();

        Page<Event> found = eventRepository.findByLocationId(a.getId(), PageRequest.of(0, 10));

        assertThat(found.getContent()).extracting(Event::getTitle)
                .containsExactlyInAnyOrder("A1", "A2");
        assertThat(eventRepository.findByLocationId(b.getId(), PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(3);
    }

    // Test 4
    @Test
    void findByStartTimeBetweenIncludesBothEnds() {
        Location l = saveLocation("L");
        saveEvent(l, "E0", BASE, EventStatus.DRAFT);
        saveEvent(l, "E1", BASE.plus(1, ChronoUnit.HOURS), EventStatus.DRAFT);
        saveEvent(l, "E2", BASE.plus(2, ChronoUnit.HOURS), EventStatus.DRAFT);
        saveEvent(l, "E3", BASE.plus(3, ChronoUnit.HOURS), EventStatus.DRAFT);
        testEntityManager.flush();
        testEntityManager.clear();

        Page<Event> found = eventRepository.findByStartTimeBetween(
                BASE.plus(1, ChronoUnit.HOURS), BASE.plus(2, ChronoUnit.HOURS), PageRequest.of(0, 10));

        assertThat(found.getContent()).extracting(Event::getTitle)
                .containsExactlyInAnyOrder("E1", "E2");
    }

    // Test 5
    @Test
    void existsByLocationIdTrueAndFalse() {
        Location used = saveLocation("Used");
        Location unused = saveLocation("Unused");
        saveEvent(used, "E", BASE, EventStatus.DRAFT);
        testEntityManager.flush();
        testEntityManager.clear();


        assertThat(eventRepository.existsByLocationId(used.getId())).isTrue();
        assertThat(eventRepository.existsByLocationId(unused.getId())).isFalse();
        assertThat(eventRepository.existsByLocationId(999_999L)).isFalse();
    }

    // helpers for tests 7 and 8
    private Statistics statistics() {
        return testEntityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
    }

    private void seedEventsEachWithOwnLocation(int n) {
        for (int i = 0; i < n; i++) {
            saveEvent(saveLocation("Loc " + i), "Event " + i, BASE, EventStatus.DRAFT);
        }
        testEntityManager.flush();
        testEntityManager.clear();
    }

    // Test 7
    @ParameterizedTest
    @ValueSource(ints = {10, 20})
    void listQueryCountIsConstantWithEntityGraph(int n) {
        seedEventsEachWithOwnLocation(n);
        Statistics stats = statistics();
        stats.clear();                                   // after the setup

        Page<Event> page = eventRepository.findByStatus(EventStatus.DRAFT, PageRequest.of(0, 5));
        page.getContent().forEach(e -> e.getLocation().getName());   // touch the lazy field

        assertThat(stats.getPrepareStatementCount()).isEqualTo(2);   // page + count
    }

    // Test 8: findAll is overridden with @EntityGraph, so it no longer causes N+1.
    // (Before the override this exact test measured 7 statements: page + count + 5 locations.)
    @Test
    void findAllUsesEntityGraphToAvoidNPlusOne() {
        seedEventsEachWithOwnLocation(10);
        Statistics stats = statistics();
        stats.clear();

        Page<Event> page = eventRepository.findAll(PageRequest.of(0, 5));
        page.getContent().forEach(e -> e.getLocation().getName());

        assertThat(stats.getPrepareStatementCount()).isEqualTo(2);   // page (with join) + count
    }

    // Test 9
    @Test
    void findByStatusSortsWhenAsked() {
        Location l = saveLocation("L");
        saveEvent(l, "Late", BASE.plus(2, ChronoUnit.HOURS), EventStatus.DRAFT);
        saveEvent(l, "Early", BASE, EventStatus.DRAFT);
        saveEvent(l, "Mid", BASE.plus(1, ChronoUnit.HOURS), EventStatus.DRAFT);
        testEntityManager.flush();
        testEntityManager.clear();

        Page<Event> found = eventRepository.findByStatus(
                EventStatus.DRAFT, PageRequest.of(0, 10, Sort.by("startTime")));

        assertThat(found.getContent()).extracting(Event::getTitle)
                .containsExactly("Early", "Mid", "Late");
    }
}
