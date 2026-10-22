package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

// BK-05: một dòng lịch sử booking — gộp booking_status_history (eventType BOOKING_STATUS_CHANGED) và booking_events
// (sự kiện mức buổi: nhận ca, đổi trạng thái buổi, đổi caregiver, báo vắng).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingHistoryResponse {
    String eventType;

    UUID occurrenceId;

    String fromStatus;

    String toStatus;

    UUID oldCaregiverId;

    UUID newCaregiverId;

    UUID changedBy;

    String reason;

    Instant changedAt;
}
