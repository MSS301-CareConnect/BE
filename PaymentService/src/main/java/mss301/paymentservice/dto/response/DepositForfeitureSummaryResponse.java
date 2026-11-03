package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

// CR-4: tổng cọc bị giữ trong khoảng thời gian — caregiver chỉ có phần của mình (centerAmount = 0).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DepositForfeitureSummaryResponse {
    BigDecimal centerAmount;

    BigDecimal caregiverAmount;

    BigDecimal totalAmount;
}
