package mss301.bookingservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.CancelledBy;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.RefundReason;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Bản sao request C2.2 POST /api/v1/refund-quotes của PaymentService — preview số tiền hoàn, Payment không ghi DB.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefundQuoteRequest {
    UUID bookingId;

    BookingStatus bookingStatus;

    CancelledBy cancelledBy;

    RefundReason reason;

    Instant cancelledAt;

    Instant firstUpcomingStartAt;

    List<Occurrence> occurrences;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Occurrence {
        UUID occurrenceId;

        Instant startAt;

        OccurrenceStatus status;

        boolean hasSession;
    }
}
