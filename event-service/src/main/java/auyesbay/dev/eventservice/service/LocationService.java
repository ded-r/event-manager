package auyesbay.dev.eventservice.service;

import auyesbay.dev.eventservice.dto.location.LocationCreateRequest;
import auyesbay.dev.eventservice.dto.location.LocationResponse;
import auyesbay.dev.eventservice.dto.location.LocationUpdateRequest;
import auyesbay.dev.eventservice.entity.Location;
import auyesbay.dev.eventservice.exception.ConflictException;
import auyesbay.dev.eventservice.exception.DuplicateResourceException;
import auyesbay.dev.eventservice.exception.ResourceNotFoundException;
import auyesbay.dev.eventservice.mapper.LocationMapper;
import auyesbay.dev.eventservice.repository.EventRepository;
import auyesbay.dev.eventservice.repository.LocationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LocationService {

    private final LocationRepository locationRepository;
    private final LocationMapper locationMapper;
    private final EventRepository eventRepository;

    public LocationService(
            LocationRepository locationRepository,
            LocationMapper locationMapper,
            EventRepository eventRepository
    ) {
        this.locationRepository = locationRepository;
        this.locationMapper = locationMapper;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public LocationResponse create(LocationCreateRequest locationCreateRequest) {
        Location location = locationMapper.toEntity(locationCreateRequest);
        if (locationRepository.existsByNameAndAddress(String.valueOf(locationCreateRequest.name()), locationCreateRequest.address())) {
            throw new DuplicateResourceException("Location already exists");
        }
        Location savedLocation = locationRepository.save(location);
        return locationMapper.toResponse(savedLocation);
    }

    public LocationResponse getById(Long id) {
        Location foundLocation = locationRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Location not found with id: " + id)
        );
        return locationMapper.toResponse(foundLocation);
    }

    public Page<LocationResponse> list(Pageable pageable) {
        return locationRepository.findAll(pageable).map(locationMapper::toResponse);
    }

    @Transactional
    public LocationResponse update(Long id, LocationUpdateRequest locationUpdateRequest) {
        Location location = locationRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Location not found with id: " + id)
        );

        if (!(location.getName().equals(locationUpdateRequest.name()))
                || !(location.getAddress().equals(locationUpdateRequest.address()))
        ) {
            if (locationRepository.existsByNameAndAddress(
                    locationUpdateRequest.name(), locationUpdateRequest.address())
            ) {
                throw new DuplicateResourceException("Location with that name and address already exists");
            }
        }

        locationMapper.updateEntity(locationUpdateRequest, location);

        return locationMapper.toResponse(location);
    }

    @Transactional
    public void delete(Long id) {
        Location location = locationRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Location not found with id: " + id)
        );

        if (eventRepository.existsByLocationId(location.getId())) {
            throw new ConflictException("There is already an event with this location");
        }
        locationRepository.delete(location);
    }
}
