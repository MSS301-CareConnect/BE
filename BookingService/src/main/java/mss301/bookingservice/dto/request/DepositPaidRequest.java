package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

// TODO(Payment): body tạm của /internal/bookings/{id}/deposit-paid — mã giao dịch chỉ ghi vào lịch sử trạng thái.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DepositPaidRequest {
    @Size(max = 100, message = "TEXT_TOO_LONG")
    String paymentReference;
}
