package auyesbay.dev.eventservice.dto.event;

import auyesbay.dev.eventservice.dto.EventStartEndTime;
import jakarta.validation.constraints.*;

import java.time.Instant;

public record EventCreateRequest(
        @NotBlank(message = "Title cannot be blank")
        @Size(max = 255)
        String title,

        String description,

        @NotNull
        Long locationId,

        @NotNull(message = "Start time cannot be null")
        @Future
        Instant startTime,

        @NotNull(message = "End time cannot be null")
        Instant endTime,

        @NotNull(message = "Participants number cannot be null")
        @Positive
        Integer maxParticipants
) implements EventStartEndTime {
}
