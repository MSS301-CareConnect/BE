package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.BookingStatus;

import java.time.Instant;

// Một trạng thái trong hàng đợi trung tâm: số booking, hạn gần nhất, số sắp quá hạn / đã quá hạn (chờ scheduler đóng).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingStatusCountResponse {
    BookingStatus status;

    long count;

    Instant nearestDueAt;

    long dueSoonCount;

    long overdueCount;
}
