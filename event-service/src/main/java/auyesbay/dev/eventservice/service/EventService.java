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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final LocationRepository locationRepository;
    private final EventMapper eventMapper;

    public EventService(EventRepository eventRepository, LocationRepository locationRepository, EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.locationRepository = locationRepository;
        this.eventMapper = eventMapper;
    }

    @Transactional
    public EventResponse createEvent(EventCreateRequest eventCreateRequest) {
        Location foundLocation = locationRepository.findById(eventCreateRequest.locationId()).orElseThrow(
                () -> new ResourceNotFoundException("Location not found")
        );

        if (eventCreateRequest.maxParticipants() > foundLocation.getCapacity()) {
            throw new BusinessRuleException("Max participants exceeded");
        }

        Event event = eventMapper.toEntity(eventCreateRequest);

        event.setLocation(foundLocation);
        event.setStatus(EventStatus.DRAFT);

        eventRepository.save(event);

        return eventMapper.toResponse(event);
    }

    public EventResponse getById(Long id) {
        Event event = eventRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));

        return eventMapper.toResponse(event);
    }

    public Page<EventResponse> list(EventStatus status, Long locationId, Instant from, Instant to, Pageable pageable) {
        Page<Event> foundEvents = null;

        if (status != null) {
            foundEvents = eventRepository.findByStatus(status, pageable);
        } else if (locationId != null) {
            foundEvents = eventRepository.findByLocationId(locationId, pageable);
        } else if (from != null && to != null) {
            foundEvents = eventRepository.findByStartTimeBetween(from, to, pageable);
        } else {
            foundEvents = eventRepository.findAll(pageable);
        }

        return foundEvents.map(eventMapper::toResponse);
    }

    @Transactional
    public EventResponse update(Long id, EventUpdateRequest eventUpdateRequest) {
        Event event = eventRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Event not found with id: " + id)
        );

        Location location = locationRepository.findById(eventUpdateRequest.locationId()).orElseThrow(() -> new ResourceNotFoundException("Location not found"));

        if (eventUpdateRequest.maxParticipants() > location.getCapacity()) {
            throw new BusinessRuleException("Max participants exceeded");
        }

        event.setLocation(location);

        eventMapper.updateEntity(eventUpdateRequest, event);

        return eventMapper.toResponse(event);
    }

    @Transactional
    public EventResponse changeStatus(Long id, EventStatus eventStatus) {
        Event event = eventRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));

        if (event.getStatus().canTransitionTo(eventStatus)) {
            event.setStatus(eventStatus);
        } else {
            throw new ConflictException("Cannot change status from " + event.getStatus() + " to " + eventStatus);
        }

        return eventMapper.toResponse(event);
    }

    @Transactional
    public void delete(Long id) {
        Event event = eventRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Event not found with id: " + id)
        );

        if (event.getStatus() == EventStatus.DRAFT) {
            eventRepository.delete(event);
        } else {
            throw new ConflictException("Only draft events can be deleted");
        }
    }
}
