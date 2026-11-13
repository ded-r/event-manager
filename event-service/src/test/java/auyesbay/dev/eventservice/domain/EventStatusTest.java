package auyesbay.dev.eventservice.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventStatusTest {

    @ParameterizedTest(name = "Переход из {0} в {1} должен быть {2}")
    @CsvSource({
            // Из DRAFT
            "DRAFT,     PUBLISHED, true",
            "DRAFT,     CANCELLED, true",
            "DRAFT,     COMPLETED, false",
            "DRAFT,     DRAFT,     false",

            // Из PUBLISHED
            "PUBLISHED, COMPLETED, true",
            "PUBLISHED, CANCELLED, true",
            "PUBLISHED, DRAFT,     false",
            "PUBLISHED, PUBLISHED, false",

            // Из CANCELLED
            "CANCELLED, DRAFT,     false",
            "CANCELLED, PUBLISHED, false",
            "CANCELLED, COMPLETED, false",
            "CANCELLED, CANCELLED, false",

            // Из COMPLETED
            "COMPLETED, DRAFT,     false",
            "COMPLETED, PUBLISHED, false",
            "COMPLETED, CANCELLED, false",
            "COMPLETED, COMPLETED, false",
    })
    void shouldVerifyStatusTransitions(EventStatus from, EventStatus to, boolean expected) {
        // Act & Assert
        boolean actualResult = from.canTransitionTo(to);

        assertEquals(expected, actualResult,
                () -> String.format("Неверный результат для перехода %s -> %s", from, to));
    }
}
