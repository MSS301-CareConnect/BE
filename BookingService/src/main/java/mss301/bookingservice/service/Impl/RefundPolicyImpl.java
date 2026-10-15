package mss301.bookingservice.service.Impl;

import mss301.bookingservice.dto.response.RefundQuote;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.enums.CancelledBy;
import mss301.bookingservice.service.RefundPolicy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
public class RefundPolicyImpl implements RefundPolicy {

    static int FULL_REFUND_PERCENT = 100;
    static BigDecimal HUNDRED = BigDecimal.valueOf(100);
    static int MONEY_SCALE = 2;

    @Override
    public RefundQuote quote(Booking booking, CancelledBy cancelledBy, Instant cancelledAt) {
        Integer refundPercent = switch (cancelledBy) {
            // Trung tâm hủy / hệ thống hết hạn: không do lỗi khách → hoàn toàn bộ tiền đã trả.
            case CENTER, SYSTEM -> FULL_REFUND_PERCENT;
            // TODO(chính sách hoàn tiền): % theo khoảng cách cancelledAt ↔ buổi đầu — chưa chốt, không tự đặt số.
            case CUSTOMER -> null;
        };
        BigDecimal refundAmount = calculateAmount(booking.getDepositAmount(), refundPercent);
        return RefundQuote.builder()
                .cancelledBy(cancelledBy)
                .refundPercent(refundPercent)
                .refundAmount(refundAmount)
                .build();
    }

    // Số tiền NULL khi chưa biết tiền cọc (chưa nối Catalog / Payment) hoặc chưa biết %.
    private BigDecimal calculateAmount(BigDecimal paidAmount, Integer refundPercent) {
        if (paidAmount == null || refundPercent == null) {
            return null;
        }
        return paidAmount.multiply(BigDecimal.valueOf(refundPercent))
                .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
