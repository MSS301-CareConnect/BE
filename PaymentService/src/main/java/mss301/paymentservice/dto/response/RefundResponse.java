package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.CancelledBy;
import mss301.paymentservice.enums.RefundReason;
import mss301.paymentservice.enums.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefundResponse {
    UUID id;

    UUID bookingId;

    UUID occurrenceId;

    UUID paymentId;

    UUID attemptId;

    UUID customerId;

    UUID centerId;

    UUID complaintId;

    BigDecimal amount;

    BigDecimal feeAmount;

    RefundReason reason;

    CancelledBy cancelledBy;

    RefundStatus status;

    int attempts;

    Instant nextAttemptAt;

    String lastError;

    String providerResponseCode;

    Instant refundedAt;

    Instant createdAt;
}
