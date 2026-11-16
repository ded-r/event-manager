package auyesbay.dev.eventservice.dto.location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LocationUpdateRequest(
        @NotBlank(message = "Name cannot be null")
        @Size(max = 255)
        String name,

        @NotBlank(message = "Address cannot be null")
        @Size(max = 500)
        String address,

        @NotBlank(message = "City cannot be null")
        @Size(max = 100)
        String city,

        @NotBlank(message = "Country cannot be null")
        @Size(max = 100)
        String country,

        @NotNull(message = "Capacity cannot be null")
        @Positive(message = "Capacity must be greater than zero")
        Integer capacity
) {
}
