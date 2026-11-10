package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

// Hàng đợi booking của trung tâm theo trạng thái (thẻ "Cần xác nhận / Cần phân công…" và số trên tab).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingStatusCountsResponse {
    List<BookingStatusCountResponse> statuses;

    // Cửa sổ "sắp quá hạn" (phút) dùng để đếm dueSoonCount.
    long dueSoonMinutes;

    Instant generatedAt;
}
