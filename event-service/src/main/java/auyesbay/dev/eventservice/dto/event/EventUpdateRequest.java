package auyesbay.dev.eventservice.dto.event;

import auyesbay.dev.eventservice.dto.EventStartEndTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record EventUpdateRequest(
        @NotBlank
        @Size(max = 255)
        String title,

        String description,

        @NotNull
        Long locationId,

        @NotNull
        Instant startTime,

        @NotNull
        Instant endTime,

        @NotNull
        @Positive
        Integer maxParticipants
) implements EventStartEndTime {
}
