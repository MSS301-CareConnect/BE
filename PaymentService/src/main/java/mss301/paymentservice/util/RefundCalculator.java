package mss301.paymentservice.util;

import mss301.paymentservice.dto.response.PolicySnapshot;
import mss301.paymentservice.enums.OccurrenceStatus;
import mss301.paymentservice.enums.RefundReason;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bộ tính tiền hoàn theo spec booking v1.0 §10.2 (Q5, M4) trên center booking policy đã snapshot lúc thanh toán khoản
 * đầu. Hàm thuần — dùng chung cho preview (refund-quotes) và lệnh hoàn thật (refunds) để hai nơi luôn ra cùng một số.
 * paid_i = cọc_i + đã thu thêm_i − đã hoàn_i; % hoàn tính trên paid_i, phần không hoàn là phí (tiền khách mất).
 */
public final class RefundCalculator {

    public static final String NOT_PAID = "NOT_PAID";
    public static final String NOT_CANCELLABLE = "NOT_CANCELLABLE";
    public static final String BEFORE_THRESHOLD = "BEFORE_THRESHOLD";
    public static final String AFTER_THRESHOLD = "AFTER_THRESHOLD";
    public static final String FULL_REFUND_CENTER = "FULL_REFUND_CENTER";
    public static final String FULL_REFUND_MISSED = "FULL_REFUND_MISSED";
    public static final String FULL_REFUND_SYSTEM = "FULL_REFUND_SYSTEM";
    public static final String PAYMENT_OVERDUE = "PAYMENT_OVERDUE";
    static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private RefundCalculator() {
    }

    public static List<Line> calculate(Context context, List<OccurrenceInput> occurrences) {
        List<Line> lines = new ArrayList<>();
        for (OccurrenceInput occurrence : occurrences) {
            lines.add(calculateOne(context, occurrence));
        }
        return lines;
    }

    // M3: quá hạn trả phần còn lại → giữ forfeitPercent của số đã trả mỗi buổi, hoàn phần dư.
    public static List<Line> forfeit(BigDecimal forfeitPercent, List<OccurrenceInput> occurrences) {
        List<Line> lines = new ArrayList<>();
        for (OccurrenceInput occurrence : occurrences) {
            BigDecimal price = occurrence.priceAmount() == null ? BigDecimal.ZERO : occurrence.priceAmount();
            BigDecimal paid = paidOf(occurrence);
            BigDecimal fee = VndMoney.percentOf(forfeitPercent, paid).min(paid);
            lines.add(new Line(occurrence.occurrenceId(), price, paid, fee, paid.subtract(fee), PAYMENT_OVERDUE));
        }
        return lines;
    }

    private static Line calculateOne(Context context, OccurrenceInput occurrence) {
        BigDecimal price = occurrence.priceAmount() == null ? BigDecimal.ZERO : occurrence.priceAmount();
        if (!context.paid() || context.policy() == null) {
            return new Line(occurrence.occurrenceId(), price, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, NOT_PAID);
        }
        BigDecimal paid = paidOf(occurrence);
        return switch (context.reason()) {
            case CENTER_REJECT, CENTER_CANCEL, CENTER_LOCKED -> fullRefund(occurrence, price, paid, FULL_REFUND_CENTER);
            case CAREGIVER_MISSED -> fullRefund(occurrence, price, paid, FULL_REFUND_MISSED);
            case CUSTOMER_CANCEL -> customerCancel(context, occurrence, price, paid);
            default -> paid.signum() > 0
                    ? fullRefund(occurrence, price, paid, FULL_REFUND_SYSTEM)
                    : new Line(occurrence.occurrenceId(), price, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, NOT_PAID);
        };
    }

    // Spec §10.2: còn ≥ refundThresholdMinutes tới giờ buổi → % trước mốc (100), ít hơn → % sau mốc (70).
    private static Line customerCancel(Context context, OccurrenceInput occurrence, BigDecimal price, BigDecimal paid) {
        // Buổi đã bắt đầu (có session) hoặc không còn ở trạng thái chưa bắt đầu: khách không hủy được, buổi tính đủ tiền.
        boolean started = occurrence.hasSession()
                || (occurrence.status() != null && !OccurrenceStatus.NOT_STARTED.contains(occurrence.status()));
        long leadSeconds = Duration.between(context.cancelledAt(), occurrence.startAt()).getSeconds();
        if (started || leadSeconds < 0) {
            return notCancellable(occurrence, price, paid);
        }
        PolicySnapshot policy = context.policy();
        if (policy.getRefundThresholdMinutes() == null) {
            // Snapshot cũ chưa có policy hoàn theo mốc (change script 2026-11-02 đã bổ sung) — không đoán phí, hoàn đủ.
            return fullRefund(occurrence, price, paid, FULL_REFUND_SYSTEM);
        }
        boolean beforeThreshold = leadSeconds >= Duration.ofMinutes(policy.getRefundThresholdMinutes()).getSeconds();
        BigDecimal percent = beforeThreshold
                ? policy.getRefundPercentBeforeThreshold()
                : policy.getRefundPercentAfterThreshold();
        BigDecimal refund = VndMoney.percentOf(percent.min(ONE_HUNDRED), paid).min(paid);
        return new Line(occurrence.occurrenceId(), price, paid, paid.subtract(refund), refund,
                beforeThreshold ? BEFORE_THRESHOLD : AFTER_THRESHOLD);
    }

    private static BigDecimal paidOf(OccurrenceInput occurrence) {
        return VndMoney.nonNegative(occurrence.paidAmount() == null ? BigDecimal.ZERO : occurrence.paidAmount());
    }

    private static Line fullRefund(OccurrenceInput occurrence, BigDecimal price, BigDecimal paid, String rule) {
        return new Line(occurrence.occurrenceId(), price, paid, BigDecimal.ZERO, paid, rule);
    }

    private static Line notCancellable(OccurrenceInput occurrence, BigDecimal price, BigDecimal paid) {
        return new Line(occurrence.occurrenceId(), price, paid, BigDecimal.ZERO, BigDecimal.ZERO, NOT_CANCELLABLE);
    }

    /**
     * @param paid booking đã trả khoản đầu (chưa trả / chưa có dữ liệu → mọi buổi NOT_PAID)
     */
    public record Context(boolean paid, PolicySnapshot policy, RefundReason reason, Instant cancelledAt) {
    }

    // status null = không biết trạng thái phía Booking (lệnh hoàn thật: Booking đã hủy buổi trước khi gửi).
    public record OccurrenceInput(UUID occurrenceId, Instant startAt, OccurrenceStatus status, boolean hasSession,
                                  BigDecimal priceAmount, BigDecimal paidAmount) {
    }

    public record Line(UUID occurrenceId, BigDecimal priceAmount, BigDecimal paidAmount, BigDecimal feeAmount,
                       BigDecimal refundAmount, String rule) {
    }
}
