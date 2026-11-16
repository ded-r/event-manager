package auyesbay.dev.eventservice.dto;

import auyesbay.dev.eventservice.dto.location.LocationCreateRequest;
import auyesbay.dev.eventservice.dto.location.LocationUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class LocationDtoValidatorTest {

    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    // Test 1
    @Test
    public void testValidCreate() {
        LocationCreateRequest locationCreateRequest = new LocationCreateRequest(
                "Name",
                "Address",
                "City",
                "Country",
                100
        );

        Set<ConstraintViolation<LocationCreateRequest>> constraintViolations =
                validator.validate(locationCreateRequest);

        assertThat(constraintViolations.size()).isZero();
    }

    // Test 2
    @Test
    void testBlankText() {
        LocationCreateRequest locationCreateRequest = new LocationCreateRequest(
                "",
                "",
                "",
                "",
                100
        );

        Set<ConstraintViolation<LocationCreateRequest>> constraintViolations =
                validator.validate(locationCreateRequest);

        assertThat(constraintViolations.size()).isEqualTo(4);
    }

    // Test 3
    @Test
    void testTextTooLong() {
        String longName = "a".repeat(256);
        String longAddress = "a".repeat(501);
        String longCity = "a".repeat(101);
        String longCountry = "a".repeat(101);

        LocationCreateRequest locationCreateRequest = new LocationCreateRequest(
                longName,
                longAddress,
                longCity,
                longCountry,
                100
        );

        Set<ConstraintViolation<LocationCreateRequest>> constraintViolations =
                validator.validate(locationCreateRequest);

        assertThat(constraintViolations.size()).isEqualTo(4);
    }

    // Test 4
    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -5})
    void testCapacity(Integer capacity) {
        LocationCreateRequest locationCreateRequest = new LocationCreateRequest(
                "Name",
                "Address",
                "City",
                "Country",
                capacity
        );

        Set<ConstraintViolation<LocationCreateRequest>> constraintViolations =
                validator.validate(locationCreateRequest);

        assertThat(constraintViolations.size()).isOne();
    }

    // Test 5
    @Test
    void testValidUpdateDto() {
        LocationUpdateRequest locationUpdateRequest = new LocationUpdateRequest(
                "Name",
                "Address",
                "City",
                "Country",
                100
        );

        Set<ConstraintViolation<LocationUpdateRequest>> constraintViolations =
                validator.validate(locationUpdateRequest);

        assertThat(constraintViolations.size()).isZero();
    }

    @Test
    void testInvalidUpdateDto() {
        LocationUpdateRequest locationUpdateRequest = new LocationUpdateRequest(
                "Name",
                "Address",
                "City",
                "Country",
                null
        );

        Set<ConstraintViolation<LocationUpdateRequest>> constraintViolations =
                validator.validate(locationUpdateRequest);

        assertThat(constraintViolations.size()).isOne();
    }
}
