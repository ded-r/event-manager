package auyesbay.dev.eventservice.dto.event;

import auyesbay.dev.eventservice.domain.EventStatus;
import auyesbay.dev.eventservice.dto.location.LocationResponse;

import java.time.Instant;

public record EventResponse(
        Long id,
        String title,
        String description,
        LocationResponse location,
        Instant startTime,
        Instant endTime,
        Integer maxParticipants,
        EventStatus status,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
