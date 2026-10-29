package mss301.paymentservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.CancelledBy;
import mss301.paymentservice.enums.RefundReason;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Hợp đồng C2.3 — tên field trùng RefundCreateRequest của BookingService. Payment tự tính số tiền, không nhận số tiền.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefundCreateRequest {
    @NotNull(message = "BOOKING_ID_REQUIRED")
    UUID bookingId;

    @NotNull(message = "OCCURRENCES_REQUIRED")
    List<@NotNull(message = "OCCURRENCE_ID_REQUIRED") UUID> occurrenceIds;

    @NotNull(message = "REFUND_REASON_REQUIRED")
    RefundReason reason;

    CancelledBy cancelledBy;

    @NotNull(message = "CANCELLED_AT_REQUIRED")
    Instant cancelledAt;

    Instant firstUpcomingStartAt;

    // NULL = hệ thống (job MISSED, hết hạn).
    UUID requestedBy;

    // Booking gửi cả ở header Idempotency-Key — header được ưu tiên.
    @Size(max = 150, message = "TEXT_TOO_LONG")
    String idempotencyKey;
}
