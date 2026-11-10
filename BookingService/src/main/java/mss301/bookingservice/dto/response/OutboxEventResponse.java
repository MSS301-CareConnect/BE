package mss301.bookingservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.OutboxEventType;
import mss301.bookingservice.enums.OutboxStatus;

import java.time.Instant;
import java.util.UUID;

// H-08: một dòng booking_outbox cho màn hình sự cố admin — không trả payload (có thể chứa số tiền / ghi chú khách).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OutboxEventResponse {
    UUID id;
    OutboxEventType eventType;
    UUID aggregateId;
    String idempotencyKey;
    OutboxStatus status;
    Integer attempts;
    Instant nextAttemptAt;
    String lastError;
    Instant sentAt;
    Instant createdAt;
    Instant updatedAt;
}
