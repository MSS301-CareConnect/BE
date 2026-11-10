package mss301.paymentservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.BookingStatus;
import mss301.paymentservice.enums.CancelledBy;
import mss301.paymentservice.enums.OccurrenceStatus;
import mss301.paymentservice.enums.RefundReason;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Hợp đồng C2.2 — tên field trùng RefundQuoteRequest của BookingService (fail-on-unknown-properties đang bật).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefundQuoteRequest {
    @NotNull(message = "BOOKING_ID_REQUIRED")
    UUID bookingId;

    BookingStatus bookingStatus;

    CancelledBy cancelledBy;

    @NotNull(message = "REFUND_REASON_REQUIRED")
    RefundReason reason;

    @NotNull(message = "CANCELLED_AT_REQUIRED")
    Instant cancelledAt;

    Instant firstUpcomingStartAt;

    @NotNull(message = "OCCURRENCES_REQUIRED")
    List<@Valid Occurrence> occurrences;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Occurrence {
        @NotNull(message = "OCCURRENCE_ID_REQUIRED")
        UUID occurrenceId;

        Instant startAt;

        OccurrenceStatus status;

        boolean hasSession;
    }
}
