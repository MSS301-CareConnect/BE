package mss301.bookingservice.mapper;

import mss301.bookingservice.dto.response.CaregiverChangeRequestResponse;
import mss301.bookingservice.entity.CaregiverChangeRequest;
import mss301.bookingservice.entity.CaregiverChangeRequestOccurrence;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface CaregiverChangeRequestMapper {

    @Mapping(target = "bookingId", source = "booking.id")
    @Mapping(target = "occurrenceIds", expression = "java(toOccurrenceIds(request.getOccurrences()))")
    CaregiverChangeRequestResponse toResponse(CaregiverChangeRequest request);

    List<CaregiverChangeRequestResponse> toResponseList(List<CaregiverChangeRequest> requests);

    default List<UUID> toOccurrenceIds(List<CaregiverChangeRequestOccurrence> occurrences) {
        if (occurrences == null) return List.of();
        return occurrences.stream().map(o -> o.getId().getOccurrenceId()).toList();
    }
}
