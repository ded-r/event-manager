package auyesbay.dev.eventservice.dto;

import auyesbay.dev.eventservice.validation.ValidTimeRange;

import java.time.Instant;

@ValidTimeRange
public interface EventStartEndTime {
    Instant startTime();

    Instant endTime();
}
