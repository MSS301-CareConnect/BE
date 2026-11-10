package mss301.bookingservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.OccurrenceStatus;

import java.util.List;
import java.util.UUID;

// Bản sao request C2.5 POST /api/v1/remaining-invoices của PaymentService — gửi khi booking COMPLETED (OPS-04).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RemainingInvoiceRequest {
    UUID bookingId;

    List<Occurrence> occurrences;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Occurrence {
        UUID occurrenceId;

        OccurrenceStatus finalStatus;
    }
}
