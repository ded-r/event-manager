package auyesbay.dev.eventservice.dto;

import auyesbay.dev.eventservice.dto.event.EventCreateRequest;
import auyesbay.dev.eventservice.dto.event.EventUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class EventDtoValidatorTest {
    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    // Test 1
    @Test
    void testValidEventCreate() {
        EventCreateRequest eventCreateRequest = new EventCreateRequest(
                "Title",
                "Description",
                1L,
                Instant.now().plus(5, ChronoUnit.HOURS),
                Instant.now().plus(7, ChronoUnit.HOURS),
                100
        );

        Set<ConstraintViolation<EventCreateRequest>> constraintViolations =
                validator.validate(eventCreateRequest);

        assertThat(constraintViolations.size()).isZero();
    }

    // Test 2
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void testBlankTitle(String title) {
        EventCreateRequest eventCreateRequest = new EventCreateRequest(
                title,
                "Description",
                1L,
                Instant.now().plus(5, ChronoUnit.HOURS),
                Instant.now().plus(7, ChronoUnit.HOURS),
                100
        );

        Set<ConstraintViolation<EventCreateRequest>> constraintViolations =
                validator.validate(eventCreateRequest);

        assertThat(constraintViolations).extracting(c -> c.getPropertyPath().toString())
                .containsExactly("title");
    }

    // Test 3
    @Test
    void testTitleTooLong() {
        String title = "a".repeat(256);
        EventCreateRequest eventCreateRequest = new EventCreateRequest(
                title,
                "Description",
                1L,
                Instant.now().plus(5, ChronoUnit.HOURS),
                Instant.now().plus(7, ChronoUnit.HOURS),
                100
        );

        Set<ConstraintViolation<EventCreateRequest>> constraintViolations =
                validator.validate(eventCreateRequest);

        assertThat(constraintViolations).extracting(c -> c.getPropertyPath().toString())
                .containsExactly("title");
    }

    // Test 4
    @Test
    void testNullLocationId() {
        EventCreateRequest eventCreateRequest = new EventCreateRequest(
                "Title",
                "Description",
                null,
                Instant.now().plus(5, ChronoUnit.HOURS),
                Instant.now().plus(7, ChronoUnit.HOURS),
                100
        );

        Set<ConstraintViolation<EventCreateRequest>> constraintViolations =
                validator.validate(eventCreateRequest);

        assertThat(constraintViolations).extracting(c -> c.getPropertyPath().toString())
                .containsExactly("locationId");
    }

    // Test 5
    @Test
    void testPastStartTime() {
        EventCreateRequest eventCreateRequest = new EventCreateRequest(
                "Title",
                "Description",
                1L,
                Instant.now().minus(5, ChronoUnit.HOURS),
                Instant.now().plus(7, ChronoUnit.HOURS),
                100
        );

        Set<ConstraintViolation<EventCreateRequest>> constraintViolations =
                validator.validate(eventCreateRequest);

        assertThat(constraintViolations).extracting(c -> c.getPropertyPath().toString())
                .containsExactly("startTime");
    }

    // Test 6
    @Test
    void testNullStartTime() {
        EventCreateRequest eventCreateRequest = new EventCreateRequest(
                "Title",
                "Description",
                1L,
                null,
                Instant.now().plus(7, ChronoUnit.HOURS),
                100
        );

        Set<ConstraintViolation<EventCreateRequest>> constraintViolations =
                validator.validate(eventCreateRequest);

        assertThat(constraintViolations).extracting(c -> c.getPropertyPath().toString())
                .containsExactly("startTime");
    }

    @Test
    void testNullEndTime() {
        EventCreateRequest eventCreateRequest = new EventCreateRequest(
                "Title",
                "Description",
                1L,
                Instant.now().plus(5, ChronoUnit.HOURS),
                null,
                100
        );

        Set<ConstraintViolation<EventCreateRequest>> constraintViolations =
                validator.validate(eventCreateRequest);

        assertThat(constraintViolations).extracting(c -> c.getPropertyPath().toString())
                .containsExactly("endTime");
    }

    // Test 7
    @Test
    void testEqualStartEndTime() {
        Instant startTime = Instant.now().plus(5, ChronoUnit.HOURS);

        EventCreateRequest eventCreateRequest = new EventCreateRequest(
                "Title",
                "Description",
                1L,
                startTime,
                startTime,
                100
        );

        Set<ConstraintViolation<EventCreateRequest>> constraintViolations =
                validator.validate(eventCreateRequest);

        assertThat(constraintViolations).extracting(ConstraintViolation::getMessage)
                .containsExactly("Start date must be before end date");
    }

    // Test 8
    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void testMaxParticipants(Integer maxParticipants) {
        EventCreateRequest eventCreateRequest = new EventCreateRequest(
                "Title",
                "Description",
                1L,
                Instant.now().plus(5, ChronoUnit.HOURS),
                Instant.now().plus(7, ChronoUnit.HOURS),
                maxParticipants
        );

        Set<ConstraintViolation<EventCreateRequest>> constraintViolations =
                validator.validate(eventCreateRequest);

        assertThat(constraintViolations).extracting(c -> c.getPropertyPath().toString())
                .containsExactly("maxParticipants");
    }

    // Test 9
    @Test
    void testNullDescription() {
        EventCreateRequest eventCreateRequest = new EventCreateRequest(
                "Title",
                null,
                1L,
                Instant.now().plus(5, ChronoUnit.HOURS),
                Instant.now().plus(7, ChronoUnit.HOURS),
                100
        );

        Set<ConstraintViolation<EventCreateRequest>> constraintViolations =
                validator.validate(eventCreateRequest);

        assertThat(constraintViolations.size()).isZero();
    }

    // Test 10
    @Test
    void testValidEventUpdate() {
        EventUpdateRequest eventUpdateRequest = new EventUpdateRequest(
                "Title",
                "Description",
                1L,
                Instant.now().plus(5, ChronoUnit.HOURS),
                Instant.now().plus(7, ChronoUnit.HOURS),
                100
        );

        Set<ConstraintViolation<EventUpdateRequest>> constraintViolations =
                validator.validate(eventUpdateRequest);

        assertThat(constraintViolations.size()).isZero();
    }

    // Test 11
    @Test
    void testFutureStartTime() {
        EventUpdateRequest eventUpdateRequest = new EventUpdateRequest(
                "Title",
                "Description",
                1L,
                Instant.now().minus(5, ChronoUnit.HOURS),
                Instant.now().plus(7, ChronoUnit.HOURS),
                100
        );

        Set<ConstraintViolation<EventUpdateRequest>> constraintViolations =
                validator.validate(eventUpdateRequest);

        assertThat(constraintViolations.size()).isZero();
    }

    // Test 12
    @Test
    void testEndTimeBeforeStartTime() {
        EventUpdateRequest eventUpdateRequest = new EventUpdateRequest(
                "Title",
                "Description",
                1L,
                Instant.now().plus(5, ChronoUnit.HOURS),
                Instant.now().plus(4, ChronoUnit.HOURS),
                100
        );

        Set<ConstraintViolation<EventUpdateRequest>> constraintViolations =
                validator.validate(eventUpdateRequest);

        assertThat(constraintViolations).extracting(ConstraintViolation::getMessage)
                .containsExactly("Start date must be before end date");
    }
}
