package mss301.paymentservice.client.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

// Body POST /api/v1/bookings/{id}/deposit-paid của BookingService — mã giao dịch chỉ ghi vào lịch sử trạng thái.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DepositPaidRequest {
    String paymentReference;
}
