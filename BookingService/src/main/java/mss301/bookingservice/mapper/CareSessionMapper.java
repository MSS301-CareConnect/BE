package mss301.bookingservice.mapper;

import mss301.bookingservice.dto.request.CareSessionStartRequest;
import mss301.bookingservice.dto.response.CareSessionResponse;
import mss301.bookingservice.dto.response.CareTaskResponse;
import mss301.bookingservice.entity.BookingDetail;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.entity.CareSession;
import mss301.bookingservice.entity.CareTask;
import mss301.bookingservice.enums.CareSessionStatus;
import mss301.bookingservice.enums.CareTaskStatus;
import mss301.bookingservice.enums.CareTaskType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.UUID;
import mss301.bookingservice.dto.request.CareTaskCreateRequest;
import mss301.bookingservice.dto.request.SessionNoteRequest;
import mss301.bookingservice.dto.response.SessionNoteResponse;
import mss301.bookingservice.dto.response.SessionUpdateResponse;
import mss301.bookingservice.entity.CareSessionNote;
import java.time.LocalTime;
import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface CareSessionMapper {

    // Check-in (OPS-02): caregiver lấy từ buổi đã khóa (không từ request); trạng thái / giờ do service quyết định.
    @IgnoreServerManagedFields
    @Mapping(target = "occurrence", source = "occurrence")
    @Mapping(target = "booking", source = "occurrence.booking")
    @Mapping(target = "caregiverId", source = "occurrence.caregiverId")
    @Mapping(target = "caregiverUserId", source = "occurrence.caregiverUserId")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "checkInLatitude", source = "request.latitude")
    @Mapping(target = "checkInLongitude", source = "request.longitude")
    @Mapping(target = "checkOutAt", ignore = true)
    @Mapping(target = "checkOutLatitude", ignore = true)
    @Mapping(target = "checkOutLongitude", ignore = true)
    @Mapping(target = "endedBy", ignore = true)
    @Mapping(target = "endReason", ignore = true)
    @Mapping(target = "overdueRemindedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    CareSession toCareSession(CareSessionStartRequest request, BookingOccurrence occurrence, CareSessionStatus status,
                              Instant checkInAt);

    // Việc theo dịch vụ của booking: tiêu đề = tên dịch vụ lúc đặt.
    @IgnoreServerManagedFields
    @Mapping(target = "session", source = "session")
    @Mapping(target = "bookingDetailId", source = "detail.id")
    @Mapping(target = "careServiceId", source = "detail.careServiceId")
    @Mapping(target = "title", source = "detail.serviceName")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "scheduledTime", ignore = true)
    @Mapping(target = "skipReason", ignore = true)
    @Mapping(target = "note", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    CareTask toServiceTask(BookingDetail detail, CareSession session, CareTaskType taskType, CareTaskStatus status,
                           Integer sortOrder);

    // Việc cho uống thuốc theo giờ (tiêu đề do service soạn từ snapshot).
    @IgnoreServerManagedFields
    // session và status trùng tên với thuộc tính của session → chỉ rõ nguồn là tham số.
    @Mapping(target = "session", source = "session")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "bookingDetailId", ignore = true)
    @Mapping(target = "careServiceId", ignore = true)
    @Mapping(target = "skipReason", ignore = true)
    @Mapping(target = "note", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    CareTask toMedicationTask(String title, LocalTime scheduledTime, CareSession session, CareTaskType taskType,
                              CareTaskStatus status, Integer sortOrder);

    @Mapping(target = "sessionId", source = "session.id")
    @Mapping(target = "occurrenceId", source = "session.occurrence.id")
    @Mapping(target = "bookingId", source = "session.booking.id")
    @Mapping(target = "caregiverId", source = "session.caregiverId")
    @Mapping(target = "status", source = "session.status")
    @Mapping(target = "tasks", source = "tasks")
    CareSessionResponse toResponse(CareSession session, List<CareTask> tasks);

    CareTaskResponse toTaskResponse(CareTask task);

    @IgnoreServerManagedFields
    @Mapping(target = "session", source = "session")
    CareSessionNote toCareSessionNote(SessionNoteRequest request, CareSession session, UUID createdBy);

    @Mapping(target = "sessionId", source = "session.id")
    SessionNoteResponse toNoteResponse(CareSessionNote note);

    List<SessionNoteResponse> toNoteResponseList(List<CareSessionNote> notes);

    SessionUpdateResponse toSessionUpdateResponse(UUID sessionId, UUID taskId, CareTaskStatus status, UUID noteId);

}
