package mss301.paymentservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

// Hợp đồng C2.4 — tên field trùng ComplaintRefundRequest của BookingService (admin quyết số tiền hoàn).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ComplaintRefundRequest {
    @NotNull(message = "BOOKING_ID_REQUIRED")
    UUID bookingId;

    @NotNull(message = "COMPLAINT_ID_REQUIRED")
    UUID complaintId;

    @NotNull(message = "REFUND_AMOUNT_INVALID")
    @Positive(message = "REFUND_AMOUNT_INVALID")
    BigDecimal amount;

    UUID decidedBy;

    @Size(max = 2000, message = "TEXT_TOO_LONG")
    String note;
}
