package auyesbay.dev.eventservice.dto.location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LocationCreateRequest(
        @NotBlank(message = "Name cannot be blank")
        @Size(max = 255)
        String name,

        @NotBlank(message = "Address cannot be blank")
        @Size(max = 500)
        String address,

        @NotBlank(message = "City cannot be blank")
        @Size(max = 100)
        String city,

        @NotBlank(message = "Country cannot be blank")
        @Size(max = 100)
        String country,

        @NotNull(message = "Capacity cannot be null")
        @Positive(message = "Capacity must be greater than zero")
        Integer capacity
) {
}
