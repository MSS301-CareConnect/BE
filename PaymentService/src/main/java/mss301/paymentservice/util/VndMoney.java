package mss301.paymentservice.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Làm tròn tiền VND ở MỘT chỗ duy nhất: tới đồng (scale 0), HALF_UP. Mọi phép tính % (cọc, phí hủy, phí vắng mặt)
 * đi qua {@link #percentOf} để hai nơi tính cùng một số không bao giờ lệch nhau một đồng.
 */
public final class VndMoney {

    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private VndMoney() {
    }

    public static BigDecimal round(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP);
    }

    // round(percent % × amount); chia cho 100 luôn hữu hạn nên không cần MathContext.
    public static BigDecimal percentOf(BigDecimal percent, BigDecimal amount) {
        return round(amount.multiply(percent).divide(HUNDRED));
    }

    // Số tiền dạng "đơn vị nhỏ nhất × 100" mà VNPay yêu cầu ở vnp_Amount.
    public static String toVnPayAmount(BigDecimal amount) {
        return round(amount).movePointRight(2).toPlainString();
    }

    public static BigDecimal nonNegative(BigDecimal amount) {
        return amount.signum() < 0 ? BigDecimal.ZERO : amount;
    }
}
