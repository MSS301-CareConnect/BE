package mss301.paymentservice.client.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

// Body POST /api/v1/bookings/{id}/deposit-paid và /remaining-paid của BookingService — mã giao dịch chỉ ghi vào lịch sử.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DepositPaidRequest {
    String paymentReference;

    // deposit-paid: khoản đầu đã phủ toàn bộ tiền booking (SHIFT 100 %) — Booking đánh dấu đã trả đủ.
    Boolean fullyPaid;
}
