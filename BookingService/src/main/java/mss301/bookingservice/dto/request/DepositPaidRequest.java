package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

// Body /bookings/{id}/deposit-paid và /remaining-paid (PaymentService) — mã giao dịch chỉ ghi vào lịch sử trạng thái.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DepositPaidRequest {
    @Size(max = 100, message = "TEXT_TOO_LONG")
    String paymentReference;

    // deposit-paid: khoản đầu đã phủ toàn bộ tiền booking (SHIFT 100 %) → booking đã trả đủ.
    Boolean fullyPaid;
}
