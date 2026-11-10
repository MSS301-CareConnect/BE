package mss301.bookingservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

// M-07: CareRecipientService hỏi trước khi tạm ngưng / lưu trữ hồ sơ người bệnh.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecipientActivityResponse {
    UUID recipientId;
    // Booking PENDING / AWAITING_DEPOSIT / CONFIRMED / IN_PROGRESS của người bệnh.
    long unfinishedBookings;
}
