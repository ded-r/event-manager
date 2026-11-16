package auyesbay.dev.eventservice.mapper;

import auyesbay.dev.eventservice.dto.location.LocationCreateRequest;
import auyesbay.dev.eventservice.dto.location.LocationResponse;
import auyesbay.dev.eventservice.dto.location.LocationUpdateRequest;
import auyesbay.dev.eventservice.entity.Location;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface LocationMapper {

    Location toEntity(LocationCreateRequest request);

    LocationResponse toResponse(Location location);

    void updateEntity(LocationUpdateRequest request, @MappingTarget Location location);
}
