package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CancelledBy;

import java.math.BigDecimal;

// Kết quả RefundPolicy. refundPercent / refundAmount NULL = chưa tính được (TODO chính sách % theo mốc ngày).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefundQuote {
    CancelledBy cancelledBy;

    Integer refundPercent;

    BigDecimal refundAmount;
}
