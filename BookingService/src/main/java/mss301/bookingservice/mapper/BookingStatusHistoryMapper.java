package mss301.bookingservice.mapper;

import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingStatusHistory;
import mss301.bookingservice.enums.BookingStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface BookingStatusHistoryMapper {

    // Bảng audit không kế thừa BaseEntity, chỉ có method tạo này → ignore id tại chỗ.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "booking", source = "booking")
    BookingStatusHistory toHistory(Booking booking, BookingStatus fromStatus, BookingStatus toStatus, UUID changedBy,
                                   String reason, Instant changedAt);
}
