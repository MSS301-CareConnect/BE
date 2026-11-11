package mss301.bookingservice.mapper;

import mss301.bookingservice.dto.response.CaregiverAbsenceResponse;
import mss301.bookingservice.dto.response.CaregiverAbsenceStatisticsResponse;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.entity.CaregiverAbsence;
import mss301.bookingservice.enums.CaregiverAbsenceSource;
import mss301.bookingservice.enums.CaregiverAbsenceStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface CaregiverAbsenceMapper {

    // Booking / trung tâm / caregiver lấy từ chính buổi vừa bị đánh CAREGIVER_MISSED (đã khóa); giải trình và kết luận có sau.
    @IgnoreServerManagedFields
    @Mapping(target = "booking", source = "occurrence.booking")
    @Mapping(target = "occurrence", source = "occurrence")
    @Mapping(target = "centerId", source = "occurrence.booking.centerId")
    @Mapping(target = "caregiverId", source = "occurrence.caregiverId")
    @Mapping(target = "caregiverUserId", source = "occurrence.caregiverUserId")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "explanation", ignore = true)
    @Mapping(target = "explainedAt", ignore = true)
    @Mapping(target = "conclusionNote", ignore = true)
    @Mapping(target = "concludedBy", ignore = true)
    @Mapping(target = "concludedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    CaregiverAbsence toCaregiverAbsence(BookingOccurrence occurrence, CaregiverAbsenceSource source,
                                        CaregiverAbsenceStatus status);

    @Mapping(target = "bookingId", source = "booking.id")
    @Mapping(target = "bookingCode", source = "booking.bookingCode")
    @Mapping(target = "recipientName", source = "booking.recipientSnapshot.fullName")
    @Mapping(target = "occurrenceId", source = "occurrence.id")
    @Mapping(target = "occurrenceNo", source = "occurrence.occurrenceNo")
    @Mapping(target = "startAt", source = "occurrence.startAt")
    @Mapping(target = "endAt", source = "occurrence.endAt")
    CaregiverAbsenceResponse toResponse(CaregiverAbsence absence);

    List<CaregiverAbsenceResponse> toResponseList(List<CaregiverAbsence> absences);

    CaregiverAbsenceStatisticsResponse toStatisticsResponse(long pending, long excused, long confirmed, long total);
}
