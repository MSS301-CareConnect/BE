package mss301.paymentservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.OccurrenceStatus;

import java.util.List;
import java.util.UUID;

// Hợp đồng C2.5 — tên field trùng RemainingInvoiceRequest của BookingService (gửi khi booking COMPLETED).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RemainingInvoiceRequest {
    @NotNull(message = "BOOKING_ID_REQUIRED")
    UUID bookingId;

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

        @NotNull(message = "OCCURRENCE_STATUS_REQUIRED")
        OccurrenceStatus finalStatus;
    }
}
