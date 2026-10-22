package mss301.bookingservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CancelledBy;
import mss301.bookingservice.enums.RefundReason;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Bản sao request C2.3 POST /api/v1/refunds của PaymentService — Payment tính số tiền theo chính sách đã snapshot.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefundCreateRequest {
    UUID bookingId;

    List<UUID> occurrenceIds;

    RefundReason reason;

    CancelledBy cancelledBy;

    Instant cancelledAt;

    Instant firstUpcomingStartAt;

    // NULL = hệ thống (job MISSED, hết hạn).
    UUID requestedBy;

    String idempotencyKey;
}
