package auyesbay.dev.eventservice.mapper;

import auyesbay.dev.eventservice.dto.location.LocationCreateRequest;
import auyesbay.dev.eventservice.dto.location.LocationResponse;
import auyesbay.dev.eventservice.dto.location.LocationUpdateRequest;
import auyesbay.dev.eventservice.entity.Location;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class LocationMapperTest {

    private final LocationMapper locationMapper = new LocationMapperImpl();

    // Test 1
    @Test
    void toEntityShouldCopyFiveFields() {
        LocationCreateRequest locationCreateRequest = new LocationCreateRequest(
                "Name",
                "Address",
                "City",
                "KZ",
                100
        );

        Location location = locationMapper.toEntity(locationCreateRequest);

        assertThat(location.getName()).isEqualTo(locationCreateRequest.name());
        assertThat(location.getAddress()).isEqualTo(locationCreateRequest.address());
        assertThat(location.getCity()).isEqualTo(locationCreateRequest.city());
        assertThat(location.getCountry()).isEqualTo(locationCreateRequest.country());
        assertThat(location.getCapacity()).isEqualTo(locationCreateRequest.capacity());
    }

    // Test 2
    @Test
    void toEntityShouldNotFillServerManagedFields() {
        LocationCreateRequest locationCreateRequest = new LocationCreateRequest(
                "Name",
                "Address",
                "City",
                "KZ",
                100
        );

        Location location = locationMapper.toEntity(locationCreateRequest);

        assertNull(location.getId());
        assertNull(location.getVersion());
        assertNull(location.getCreatedAt());
        assertNull(location.getUpdatedAt());
    }

    // Test 3
    @Test
    void toResponseShouldFillAllFields() {
        Location location = new Location();
        location.setName("Name");
        location.setAddress("Address");
        location.setCity("City");
        location.setCountry("KZ");
        location.setCapacity(100);
        ReflectionTestUtils.setField(location, "id", 1L);
        ReflectionTestUtils.setField(location, "createdAt", Instant.now().plus(5, ChronoUnit.HOURS));
        ReflectionTestUtils.setField(location, "updatedAt", Instant.now().plus(6, ChronoUnit.HOURS));
        ReflectionTestUtils.setField(location, "version", 1L);

        LocationResponse locationResponse = locationMapper.toResponse(location);

        assertEquals(locationResponse.id(), location.getId());
        assertEquals(locationResponse.name(), location.getName());
        assertEquals(locationResponse.address(), location.getAddress());
        assertEquals(locationResponse.city(), location.getCity());
        assertEquals(locationResponse.country(), location.getCountry());
        assertEquals(locationResponse.capacity(), location.getCapacity());
        assertEquals(locationResponse.createdAt(), location.getCreatedAt());
        assertEquals(locationResponse.updatedAt(), location.getUpdatedAt());
        assertEquals(locationResponse.version(), location.getVersion());
    }

    // Test 4
    @Test
    void updateEntityShouldOverwriteFieldsOnSameInstance() {

        Instant createdAt = Instant.now().plus(5, ChronoUnit.HOURS);
        Instant updatedAt = Instant.now().plus(6, ChronoUnit.HOURS);

        Location location = new Location();
        location.setName("Name");
        location.setAddress("Address");
        location.setCity("City");
        location.setCountry("KZ");
        location.setCapacity(100);
        ReflectionTestUtils.setField(location, "id", 1L);
        ReflectionTestUtils.setField(location, "createdAt", createdAt);
        ReflectionTestUtils.setField(location, "updatedAt", updatedAt);
        ReflectionTestUtils.setField(location, "version", 1L);

        LocationUpdateRequest locationUpdateRequest = new LocationUpdateRequest(
                "New name",
                "New address",
                "New city",
                "New country",
                200
        );

        locationMapper.updateEntity(locationUpdateRequest, location);

        assertEquals(1L, location.getId());
        assertEquals("New name", location.getName());
        assertEquals("New address", location.getAddress());
        assertEquals("New city", location.getCity());
        assertEquals("New country", location.getCountry());
        assertEquals(200, location.getCapacity());
        assertEquals(createdAt, location.getCreatedAt());
        assertEquals(updatedAt, location.getUpdatedAt());
        assertEquals(1L, location.getVersion());
    }

    // Test 5
    @Test
    void toEntityNullShouldReturnNull() {
        Location location = locationMapper.toEntity(null);

        assertNull(location);
    }

    @Test
    void toResponseNullShouldReturnNull() {
        LocationResponse locationResponse = locationMapper.toResponse(null);

        assertNull(locationResponse);
    }
}
