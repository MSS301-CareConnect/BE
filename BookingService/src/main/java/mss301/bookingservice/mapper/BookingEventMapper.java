package mss301.bookingservice.mapper;

import mss301.bookingservice.dto.response.BookingHistoryResponse;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingEvent;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingEventType;
import mss301.bookingservice.enums.OccurrenceStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface BookingEventMapper {

    // Sự kiện đổi trạng thái một buổi (nhận ca, check-in, check-out, hủy…). Bảng chỉ ghi thêm → ignore id tại chỗ.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "booking", source = "occurrence.booking")
    @Mapping(target = "occurrenceId", source = "occurrence.id")
    @Mapping(target = "oldCaregiverId", ignore = true)
    @Mapping(target = "newCaregiverId", ignore = true)
    @Mapping(target = "createdAt", source = "occurredAt")
    BookingEvent toStatusEvent(BookingOccurrence occurrence, BookingEventType eventType, OccurrenceStatus fromStatus,
                               OccurrenceStatus toStatus, UUID changedBy, String reason, Instant occurredAt);

    // Đổi caregiver của một buổi (BK-05 dùng cho lịch sử).
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "booking", source = "booking")
    @Mapping(target = "eventType", constant = "CAREGIVER_CHANGED")
    @Mapping(target = "fromStatus", ignore = true)
    @Mapping(target = "toStatus", ignore = true)
    @Mapping(target = "createdAt", source = "occurredAt")
    BookingEvent toCaregiverChangedEvent(Booking booking, UUID occurrenceId, UUID oldCaregiverId, UUID newCaregiverId,
                                         UUID changedBy, String reason, Instant occurredAt);

    // BK-05: sự kiện mức buổi trong API lịch sử.
    @Mapping(target = "changedAt", source = "createdAt")
    BookingHistoryResponse toHistoryResponse(BookingEvent event);

    List<BookingHistoryResponse> toHistoryResponseList(List<BookingEvent> events);
}
