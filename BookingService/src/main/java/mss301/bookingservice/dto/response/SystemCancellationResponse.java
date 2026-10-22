package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

// BK-04: số booking / buổi bị hủy (CENTER_LOCKED) hoặc được đánh dấu cần đổi caregiver (CAREGIVER_DEACTIVATED) ở lần
// gọi này — gọi lại trả 0 vì đã xử lý.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SystemCancellationResponse {
    int affectedBookings;

    int affectedOccurrences;

    public static SystemCancellationResponse of(int affectedBookings, int affectedOccurrences) {
        return new SystemCancellationResponse(affectedBookings, affectedOccurrences);
    }
}
