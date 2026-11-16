package auyesbay.dev.eventservice.dto.location;

import java.time.Instant;

public record LocationResponse(
        Long id,
        String name,
        String address,
        String city,
        String country,
        Integer capacity,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
