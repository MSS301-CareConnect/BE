package mss301.paymentservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.BookingStatus;
import mss301.paymentservice.enums.OccurrenceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Bản sao PaymentContextResponse của BookingService (GET /api/v1/bookings/{id}/payment-context, token SERVICE).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingPaymentContextResponse {
    UUID bookingId;

    String bookingCode;

    UUID customerId;

    UUID centerId;

    BookingStatus status;

    BigDecimal totalAmount;

    Instant depositDueAt;

    Instant firstStartAt;

    Instant depositPaidAt;

    List<Occurrence> occurrences;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Occurrence {
        UUID occurrenceId;

        Integer occurrenceNo;

        Instant startAt;

        Instant endAt;

        OccurrenceStatus status;

        BigDecimal priceAmount;

        boolean hasSession;
    }
}
