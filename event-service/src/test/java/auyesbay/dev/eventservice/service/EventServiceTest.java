package auyesbay.dev.eventservice.service;

import auyesbay.dev.eventservice.domain.EventStatus;
import auyesbay.dev.eventservice.dto.event.EventCreateRequest;
import auyesbay.dev.eventservice.dto.event.EventResponse;
import auyesbay.dev.eventservice.dto.event.EventUpdateRequest;
import auyesbay.dev.eventservice.entity.Event;
import auyesbay.dev.eventservice.entity.Location;
import auyesbay.dev.eventservice.exception.BusinessRuleException;
import auyesbay.dev.eventservice.exception.ConflictException;
import auyesbay.dev.eventservice.exception.ResourceNotFoundException;
import auyesbay.dev.eventservice.mapper.EventMapper;
import auyesbay.dev.eventservice.repository.EventRepository;
import auyesbay.dev.eventservice.repository.LocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EventServiceTest {

    private static final Instant BASE = Instant.parse("2026-12-01T10:00:00Z");

    @Mock
    private EventRepository eventRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private EventMapper eventMapper;

    @InjectMocks
    private EventService eventService;

    private Location location(Long id, int capacity) {
        Location location = new Location();
        location.setName("Location " + id);
        location.setAddress("Address");
        location.setCity("City");
        location.setCountry("KZ");
        location.setCapacity(capacity);
        ReflectionTestUtils.setField(location, "id", id);
        return location;
    }

    private Event event(Long id, Location location, EventStatus status) {
        Event event = new Event();
        event.setTitle("Title");
        event.setDescription("Description");
        event.setLocation(location);
        event.setStartTime(BASE);
        event.setEndTime(BASE.plus(1, ChronoUnit.HOURS));
        event.setMaxParticipants(50);
        event.setStatus(status);
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    private EventResponse response() {
        return new EventResponse(1L, "Title", "Description", null, BASE, BASE.plus(1, ChronoUnit.HOURS),
                50, EventStatus.DRAFT, null, null, 0L);
    }

    private EventCreateRequest createRequest(Long locationId, int maxParticipants) {
        return new EventCreateRequest("Title", "Description", locationId, BASE, BASE.plus(1, ChronoUnit.HOURS),
                maxParticipants);
    }

    private EventUpdateRequest updateRequest(Long locationId, int maxParticipants) {
        return new EventUpdateRequest("New title", "New description", locationId, BASE,
                BASE.plus(2, ChronoUnit.HOURS), maxParticipants);
    }

    // Test 1
    @Test
    void createSetsLocationAndDraftStatus() {
        Location location = location(1L, 100);
        EventCreateRequest request = createRequest(1L, 50);
        Event mapped = new Event();
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));
        when(eventMapper.toEntity(request)).thenReturn(mapped);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventMapper.toResponse(any(Event.class))).thenReturn(response());

        EventResponse result = eventService.createEvent(request);

        assertThat(result).isNotNull();
        assertThat(mapped.getLocation()).isSameAs(location);
        assertThat(mapped.getStatus()).isEqualTo(EventStatus.DRAFT);
        verify(eventRepository).save(mapped);
    }

    // Test 2
    @Test
    void createThrowsWhenLocationMissing() {
        when(locationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.createEvent(createRequest(99L, 50)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(eventRepository, never()).save(any(Event.class));
    }

    // Test 3
    @Test
    void createRejectsMoreParticipantsThanCapacity() {
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location(1L, 100)));

        assertThatThrownBy(() -> eventService.createEvent(createRequest(1L, 101)))
                .isInstanceOf(BusinessRuleException.class);

        verify(eventRepository, never()).save(any(Event.class));
    }

    // Test 4
    @Test
    void createAllowsParticipantsEqualToCapacity() {
        EventCreateRequest request = createRequest(1L, 100);
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location(1L, 100)));
        when(eventMapper.toEntity(request)).thenReturn(new Event());
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventMapper.toResponse(any(Event.class))).thenReturn(response());

        assertThatCode(() -> eventService.createEvent(request)).doesNotThrowAnyException();
    }

    // Test 5
    @Test
    void getByIdReturnsResponse() {
        Event event = event(1L, location(1L, 100), EventStatus.DRAFT);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventMapper.toResponse(event)).thenReturn(response());

        assertThat(eventService.getById(1L)).isNotNull();
    }

    // Test 6
    @Test
    void getByIdThrowsWhenMissing() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Test 7
    @Test
    void updateAppliesRequestAndMovesToNewLocation() {
        Event event = event(1L, location(1L, 100), EventStatus.DRAFT);
        Location newLocation = location(2L, 100);
        EventUpdateRequest request = updateRequest(2L, 50);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(locationRepository.findById(2L)).thenReturn(Optional.of(newLocation));
        when(eventMapper.toResponse(event)).thenReturn(response());

        eventService.update(1L, request);

        verify(eventMapper).updateEntity(request, event);
        assertThat(event.getLocation()).isSameAs(newLocation);
    }

    // Test 8
    @Test
    void updateRejectsMoreParticipantsThanNewCapacity() {
        Event event = event(1L, location(1L, 100), EventStatus.DRAFT);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(locationRepository.findById(2L)).thenReturn(Optional.of(location(2L, 40)));

        assertThatThrownBy(() -> eventService.update(1L, updateRequest(2L, 50)))
                .isInstanceOf(BusinessRuleException.class);

        verify(eventMapper, never()).updateEntity(any(EventUpdateRequest.class), any(Event.class));
    }

    // Test 9
    @Test
    void updateThrowsWhenEventMissing() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.update(99L, updateRequest(1L, 50)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Test 10
    @Test
    void changeStatusAppliesAllowedTransition() {
        Event event = event(1L, location(1L, 100), EventStatus.DRAFT);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventMapper.toResponse(event)).thenReturn(response());

        eventService.changeStatus(1L, EventStatus.PUBLISHED);

        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    // Test 11
    @Test
    void changeStatusRejectsForbiddenTransition() {
        Event event = event(1L, location(1L, 100), EventStatus.DRAFT);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.changeStatus(1L, EventStatus.COMPLETED))
                .isInstanceOf(ConflictException.class);

        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    // Test 12
    @Test
    void deleteThrowsWhenMissing() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(eventRepository, never()).delete(any(Event.class));
    }

    // Test 13
    @Test
    void deleteRemovesExistingEvent() {
        Event event = event(1L, location(1L, 100), EventStatus.DRAFT);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        eventService.delete(1L);

        verify(eventRepository).delete(event);
    }

    // Test 13b: only DRAFT events can be deleted
    @Test
    void deleteRejectsNonDraftEvent() {
        Event event = event(1L, location(1L, 100), EventStatus.PUBLISHED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.delete(1L))
                .isInstanceOf(ConflictException.class);

        verify(eventRepository, never()).delete(any(Event.class));
    }

    // Test 14
    @Test
    void listByStatusUsesStatusQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        Event event = event(1L, location(1L, 100), EventStatus.PUBLISHED);
        when(eventRepository.findByStatus(EventStatus.PUBLISHED, pageable))
                .thenReturn(new PageImpl<>(List.of(event), pageable, 1));
        when(eventMapper.toResponse(event)).thenReturn(response());

        Page<EventResponse> page = eventService.list(EventStatus.PUBLISHED, null, null, null, pageable);

        assertThat(page.getContent()).hasSize(1);
        verify(eventRepository).findByStatus(EventStatus.PUBLISHED, pageable);
    }

    // Test 15
    @Test
    void listByLocationUsesLocationQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        Event event = event(1L, location(5L, 100), EventStatus.DRAFT);
        when(eventRepository.findByLocationId(5L, pageable))
                .thenReturn(new PageImpl<>(List.of(event), pageable, 1));
        when(eventMapper.toResponse(event)).thenReturn(response());

        Page<EventResponse> page = eventService.list(null, 5L, null, null, pageable);

        assertThat(page.getContent()).hasSize(1);
        verify(eventRepository).findByLocationId(5L, pageable);
    }

    // Test 16
    @Test
    void listByTimeRangeUsesTimeQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        Instant from = BASE;
        Instant to = BASE.plus(1, ChronoUnit.DAYS);
        Event event = event(1L, location(1L, 100), EventStatus.DRAFT);
        when(eventRepository.findByStartTimeBetween(from, to, pageable))
                .thenReturn(new PageImpl<>(List.of(event), pageable, 1));
        when(eventMapper.toResponse(event)).thenReturn(response());

        Page<EventResponse> page = eventService.list(null, null, from, to, pageable);

        assertThat(page.getContent()).hasSize(1);
        verify(eventRepository).findByStartTimeBetween(from, to, pageable);
    }

    // Test 17
    @Test
    void listWithoutFiltersUsesFindAll() {
        Pageable pageable = PageRequest.of(0, 10);
        Event event = event(1L, location(1L, 100), EventStatus.DRAFT);
        when(eventRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(event), pageable, 1));
        when(eventMapper.toResponse(event)).thenReturn(response());

        Page<EventResponse> page = eventService.list(null, null, null, null, pageable);

        assertThat(page.getContent()).hasSize(1);
        verify(eventRepository).findAll(pageable);
    }

    // Test 18: the first non-null filter wins (status beats location and time range)
    @Test
    void listStatusFilterTakesPriorityOverOtherFilters() {
        Pageable pageable = PageRequest.of(0, 10);
        when(eventRepository.findByStatus(EventStatus.DRAFT, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        eventService.list(EventStatus.DRAFT, 5L, BASE, BASE.plus(1, ChronoUnit.DAYS), pageable);

        verify(eventRepository).findByStatus(EventStatus.DRAFT, pageable);
        verify(eventRepository, never()).findByLocationId(any(), any());
        verify(eventRepository, never()).findByStartTimeBetween(any(), any(), any());
    }
}
