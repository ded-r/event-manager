package auyesbay.dev.eventservice.validation;

import auyesbay.dev.eventservice.dto.EventStartEndTime;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DateRangeValidator implements ConstraintValidator<ValidTimeRange, EventStartEndTime> {
    @Override
    public boolean isValid(EventStartEndTime dto, ConstraintValidatorContext context) {
        if (dto.startTime() == null || dto.endTime() == null) {
            return true;
        }
        return dto.startTime().isBefore(dto.endTime());
    }
}
