package auyesbay.dev.eventservice.service;

import auyesbay.dev.eventservice.dto.location.LocationCreateRequest;
import auyesbay.dev.eventservice.dto.location.LocationResponse;
import auyesbay.dev.eventservice.dto.location.LocationUpdateRequest;
import auyesbay.dev.eventservice.entity.Location;
import auyesbay.dev.eventservice.exception.ConflictException;
import auyesbay.dev.eventservice.exception.DuplicateResourceException;
import auyesbay.dev.eventservice.exception.ResourceNotFoundException;
import auyesbay.dev.eventservice.mapper.LocationMapper;
import auyesbay.dev.eventservice.mapper.LocationMapperImpl;
import auyesbay.dev.eventservice.repository.EventRepository;
import auyesbay.dev.eventservice.repository.LocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private EventRepository eventRepository;

    // a real mapper: no need to stub field copying
    @Spy
    private LocationMapper locationMapper = new LocationMapperImpl();

    @InjectMocks
    private LocationService locationService;

    private Location existingLocation(Long id, String name, String address) {
        Location location = new Location();
        location.setName(name);
        location.setAddress(address);
        location.setCity("City");
        location.setCountry("KZ");
        location.setCapacity(100);
        ReflectionTestUtils.setField(location, "id", id);
        return location;
    }

    // Test 1
    @Test
    void createSavesAndReturnsResponse() {
        LocationCreateRequest request = new LocationCreateRequest("Name", "Address", "City", "KZ", 100);
        when(locationRepository.existsByNameAndAddress("Name", "Address")).thenReturn(false);
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocationResponse response = locationService.create(request);

        assertThat(response.name()).isEqualTo("Name");
        assertThat(response.address()).isEqualTo("Address");
        assertThat(response.capacity()).isEqualTo(100);
        verify(locationRepository).save(any(Location.class));
    }

    // Test 2
    @Test
    void createRejectsDuplicateNameAndAddress() {
        LocationCreateRequest request = new LocationCreateRequest("Name", "Address", "City", "KZ", 100);
        when(locationRepository.existsByNameAndAddress("Name", "Address")).thenReturn(true);

        assertThatThrownBy(() -> locationService.create(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(locationRepository, never()).save(any(Location.class));
    }

    // Test 3
    @Test
    void getByIdReturnsResponse() {
        when(locationRepository.findById(1L)).thenReturn(Optional.of(existingLocation(1L, "Name", "Address")));

        LocationResponse response = locationService.getById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Name");
    }

    // Test 4
    @Test
    void getByIdThrowsWhenMissing() {
        when(locationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> locationService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Test 5
    @Test
    void listMapsEntitiesToResponses() {
        Pageable pageable = PageRequest.of(0, 10);
        when(locationRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(existingLocation(1L, "Name", "Address")), pageable, 1));

        Page<LocationResponse> page = locationService.list(pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(LocationResponse::name).containsExactly("Name");
    }

    // Test 6
    @Test
    void updateChangesFieldsOfExistingLocation() {
        Location location = existingLocation(1L, "Old name", "Old address");
        LocationUpdateRequest request = new LocationUpdateRequest("New name", "New address", "New city", "KG", 200);
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));
        when(locationRepository.existsByNameAndAddress("New name", "New address")).thenReturn(false);

        LocationResponse response = locationService.update(1L, request);

        assertThat(response.name()).isEqualTo("New name");
        assertThat(location.getAddress()).isEqualTo("New address");
        assertThat(location.getCapacity()).isEqualTo(200);
    }

    // Test 6b: editing only the capacity must not be reported as a duplicate of itself
    @Test
    void updateAllowsUnchangedNameAndAddress() {
        Location location = existingLocation(1L, "Name", "Address");
        LocationUpdateRequest request = new LocationUpdateRequest("Name", "Address", "City", "KZ", 200);
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));

        LocationResponse response = locationService.update(1L, request);

        assertThat(response.capacity()).isEqualTo(200);
        verify(locationRepository, never()).existsByNameAndAddress(any(), any());
    }

    // Test 7
    @Test
    void updateRejectsNameAndAddressOfAnotherLocation() {
        Location location = existingLocation(1L, "Old name", "Old address");
        LocationUpdateRequest request = new LocationUpdateRequest("Taken", "Taken address", "City", "KZ", 100);
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));
        when(locationRepository.existsByNameAndAddress("Taken", "Taken address")).thenReturn(true);

        assertThatThrownBy(() -> locationService.update(1L, request))
                .isInstanceOf(DuplicateResourceException.class);

        assertThat(location.getName()).isEqualTo("Old name");
    }

    // Test 8
    @Test
    void updateThrowsWhenMissing() {
        LocationUpdateRequest request = new LocationUpdateRequest("Name", "Address", "City", "KZ", 100);
        when(locationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> locationService.update(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Test 9
    @Test
    void deleteRejectsLocationUsedByEvents() {
        Location location = existingLocation(1L, "Name", "Address");
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));
        when(eventRepository.existsByLocationId(1L)).thenReturn(true);

        assertThatThrownBy(() -> locationService.delete(1L))
                .isInstanceOf(ConflictException.class);

        verify(locationRepository, never()).delete(any(Location.class));
    }

    // Test 10
    @Test
    void deleteRemovesFreeLocation() {
        Location location = existingLocation(1L, "Name", "Address");
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));
        when(eventRepository.existsByLocationId(1L)).thenReturn(false);

        locationService.delete(1L);

        verify(locationRepository).delete(location);
    }

    // Test 11
    @Test
    void deleteThrowsWhenMissing() {
        when(locationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> locationService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(locationRepository, never()).delete(any(Location.class));
    }
}
