package auyesbay.dev.eventservice.mapper;

import auyesbay.dev.eventservice.dto.event.EventCreateRequest;
import auyesbay.dev.eventservice.dto.event.EventResponse;
import auyesbay.dev.eventservice.dto.event.EventUpdateRequest;
import auyesbay.dev.eventservice.entity.Event;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR, uses = LocationMapper.class)
public interface EventMapper {
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "status", ignore = true)
    Event toEntity(EventCreateRequest request);

    EventResponse toResponse(Event event);

    @Mapping(target = "location", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateEntity(EventUpdateRequest request, @MappingTarget Event event);
}
