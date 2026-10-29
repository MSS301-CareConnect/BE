package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Kết quả C2.5: status = trạng thái hóa đơn, hoặc NOT_REQUIRED khi không phải thu thêm (invoiceId null, amount 0).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RemainingInvoiceResultResponse {
    UUID invoiceId;

    UUID bookingId;

    BigDecimal amount;

    String status;

    Instant dueAt;
}
