package auyesbay.dev.eventservice.domain;

public enum EventStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED,
    COMPLETED;

    public boolean canTransitionTo(EventStatus next) {
        return switch (this) {
            case DRAFT -> next == PUBLISHED || next == CANCELLED;
            case PUBLISHED -> next == COMPLETED || next == CANCELLED;
            case CANCELLED -> false; // Из отмененного нельзя никуда перейти
            case COMPLETED -> false; // Из успешного нельзя никуда перейти
        };
    }
}
