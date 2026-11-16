package auyesbay.dev.eventservice.mapper;

import auyesbay.dev.eventservice.domain.EventStatus;
import auyesbay.dev.eventservice.dto.event.EventCreateRequest;
import auyesbay.dev.eventservice.dto.event.EventResponse;
import auyesbay.dev.eventservice.dto.event.EventUpdateRequest;
import auyesbay.dev.eventservice.entity.Event;
import auyesbay.dev.eventservice.entity.Location;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(classes = {EventMapperImpl.class, LocationMapperImpl.class})
public class EventMapperTest {

    @Autowired
    private EventMapper eventMapper = new EventMapperImpl();

    @Autowired
    private LocationMapper locationMapper = new LocationMapperImpl();

    // Test 1
    @Test
    void toEntityShouldCopyAllFieldsExceptLocation() {
        EventCreateRequest eventCreateRequest = new EventCreateRequest(
                "Event",
                "Description",
                1L,
                Instant.now().plus(5, ChronoUnit.HOURS),
                Instant.now().plus(7, ChronoUnit.HOURS),
                100
        );

        Event event = eventMapper.toEntity(eventCreateRequest);

        assertEquals(eventCreateRequest.title(), event.getTitle());
        assertEquals(eventCreateRequest.description(), event.getDescription());
        assertEquals(eventCreateRequest.startTime(), event.getStartTime());
        assertEquals(eventCreateRequest.endTime(), event.getEndTime());
        assertEquals(eventCreateRequest.maxParticipants(), event.getMaxParticipants());
    }

    // Test 2
    @Test
    void toEntityLeavesLocationAndStatus() {
        EventCreateRequest eventCreateRequest = new EventCreateRequest(
                "Event",
                "Description",
                1L,
                Instant.now().plus(5, ChronoUnit.HOURS),
                Instant.now().plus(7, ChronoUnit.HOURS),
                100
        );

        Event event = eventMapper.toEntity(eventCreateRequest);

        assertNull(event.getLocation());
        assertNull(event.getStatus());
    }

    // Test 3
    @Test
    void toResponseNestsLocationResponse() {
        Location location = new Location();
        ReflectionTestUtils.setField(location, "id", 1L);
        location.setName("Name");
        location.setAddress("Locations test address");
        location.setCity("Almaty");
        location.setCountry("KZ");
        location.setCapacity(100);
        ReflectionTestUtils.setField(location, "createdAt", Instant.now().plus(5, ChronoUnit.HOURS));
        ReflectionTestUtils.setField(location, "updatedAt", Instant.now().plus(7, ChronoUnit.HOURS));
        ReflectionTestUtils.setField(location, "version", 1L);

        Event event = new Event();
        ReflectionTestUtils.setField(event, "id", 1L);
        event.setTitle("Event");
        event.setDescription("Description");
        ReflectionTestUtils.setField(event, "location", location);
        event.setStartTime(Instant.now().plus(5, ChronoUnit.HOURS));
        event.setEndTime(Instant.now().plus(7, ChronoUnit.HOURS));
        event.setMaxParticipants(100);
        ReflectionTestUtils.setField(event, "status", EventStatus.DRAFT);
        ReflectionTestUtils.setField(event, "createdAt", Instant.now());
        ReflectionTestUtils.setField(event, "updatedAt", Instant.now());
        ReflectionTestUtils.setField(event, "version", 1L);

        EventResponse eventResponse = eventMapper.toResponse(event);

        assertEquals(location.getName(), eventResponse.location().name());
        assertEquals(location.getId(), eventResponse.location().id());
        assertEquals(location.getCity(), eventResponse.location().city());
        assertEquals(location.getCapacity(), eventResponse.location().capacity());
    }

    // Test 4
    @Test
    void updateEntityKeepsLocationAndStatusUnchanged() {
        Location location = new Location();
        ReflectionTestUtils.setField(location, "id", 1L);
        location.setName("Name");
        location.setAddress("Locations test address");
        location.setCity("Almaty");
        location.setCountry("KZ");
        location.setCapacity(100);
        ReflectionTestUtils.setField(location, "createdAt", Instant.now().plus(5, ChronoUnit.HOURS));
        ReflectionTestUtils.setField(location, "updatedAt", Instant.now().plus(7, ChronoUnit.HOURS));
        ReflectionTestUtils.setField(location, "version", 1L);

        Event event = new Event();
        ReflectionTestUtils.setField(event, "id", 1L);
        event.setTitle("Event");
        event.setDescription("Description");
        ReflectionTestUtils.setField(event, "location", location);
        event.setStartTime(Instant.now().plus(5, ChronoUnit.HOURS));
        event.setEndTime(Instant.now().plus(7, ChronoUnit.HOURS));
        event.setMaxParticipants(100);
        ReflectionTestUtils.setField(event, "status", EventStatus.DRAFT);
        ReflectionTestUtils.setField(event, "createdAt", Instant.now());
        ReflectionTestUtils.setField(event, "updatedAt", Instant.now());
        ReflectionTestUtils.setField(event, "version", 1L);

        EventUpdateRequest eventUpdateRequest = new EventUpdateRequest(
                "Name 1",
                "Description 1",
                2L,
                Instant.now().plus(5, ChronoUnit.HOURS),
                Instant.now().plus(7, ChronoUnit.HOURS),
                500
        );

        eventMapper.updateEntity(eventUpdateRequest, event);
        assertSame(location, event.getLocation());
        assertEquals(event.getTitle(), eventUpdateRequest.title());
        assertEquals(event.getDescription(), eventUpdateRequest.description());
        assertEquals(event.getStartTime(), eventUpdateRequest.startTime());
        assertEquals(event.getEndTime(), eventUpdateRequest.endTime());
        assertEquals(event.getMaxParticipants(), eventUpdateRequest.maxParticipants());
        assertEquals(EventStatus.DRAFT, event.getStatus());
        assertEquals(1L, event.getId());
        assertEquals(1L, event.getVersion());
    }

}
