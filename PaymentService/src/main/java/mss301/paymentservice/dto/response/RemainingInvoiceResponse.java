package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RemainingInvoiceResponse {
    UUID id;

    UUID bookingId;

    UUID customerId;

    UUID centerId;

    BigDecimal amount;

    Instant dueAt;

    InvoiceStatus status;

    UUID paymentId;

    Instant paidAt;

    UUID waivedBy;

    String waiveReason;

    Instant createdAt;
}
