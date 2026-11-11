package mss301.paymentservice.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class VndMoney {

    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    // Lớp tiện ích tĩnh, không cho khởi tạo.
    private VndMoney() {
    }

    // Làm tròn số tiền VND về đồng (HALF_UP).
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

    // Số âm thì trả 0.
    public static BigDecimal nonNegative(BigDecimal amount) {
        return amount.signum() < 0 ? BigDecimal.ZERO : amount;
    }
}
