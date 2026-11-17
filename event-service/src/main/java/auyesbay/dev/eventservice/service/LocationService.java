package auyesbay.dev.eventservice.service;

import auyesbay.dev.eventservice.dto.location.LocationCreateRequest;
import auyesbay.dev.eventservice.dto.location.LocationResponse;
import auyesbay.dev.eventservice.entity.Location;
import auyesbay.dev.eventservice.exception.ResourceNotFoundException;
import auyesbay.dev.eventservice.mapper.LocationMapper;
import auyesbay.dev.eventservice.repository.LocationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LocationService {

    private final LocationRepository locationRepository;
    private final LocationMapper locationMapper;

    public LocationService(LocationRepository locationRepository, LocationMapper locationMapper) {
        this.locationRepository = locationRepository;
        this.locationMapper = locationMapper;
    }

    LocationResponse create(LocationCreateRequest locationCreateRequest) {
        Location location = new Location();
        location.setName(locationCreateRequest.name());
        location.setAddress(locationCreateRequest.address());
        location.setCity(locationCreateRequest.city());
        location.setCountry(locationCreateRequest.country());
        location.setCapacity(locationCreateRequest.capacity());

        locationRepository.save(location);

        return locationMapper.toResponse(location);
    }

    LocationResponse getById(Long id) {
        Location foundLocation = locationRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Location not found with id: " + id)
        );
        return locationMapper.toResponse(foundLocation);
    }

    Page<Location> list(Pageable pageable) {
        return locationRepository.findAll(PageRequest.of(0, 10));
    }

}
