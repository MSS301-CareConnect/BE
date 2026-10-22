package mss301.bookingservice.mapper;

import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingStatusHistory;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.dto.response.BookingHistoryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface BookingStatusHistoryMapper {

    // Bảng audit không kế thừa BaseEntity, chỉ có method tạo này → ignore id tại chỗ.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "booking", source = "booking")
    BookingStatusHistory toHistory(Booking booking, BookingStatus fromStatus, BookingStatus toStatus, UUID changedBy,
                                   String reason, Instant changedAt);

    // BK-05: dòng đổi trạng thái booking trong API lịch sử (không gắn buổi / caregiver).
    @Mapping(target = "eventType", constant = "BOOKING_STATUS_CHANGED")
    @Mapping(target = "occurrenceId", ignore = true)
    @Mapping(target = "oldCaregiverId", ignore = true)
    @Mapping(target = "newCaregiverId", ignore = true)
    BookingHistoryResponse toHistoryResponse(BookingStatusHistory history);

    List<BookingHistoryResponse> toHistoryResponseList(List<BookingStatusHistory> histories);
}
