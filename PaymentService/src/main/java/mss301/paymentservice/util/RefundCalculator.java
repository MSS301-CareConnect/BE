package mss301.paymentservice.util;

import mss301.paymentservice.dto.response.PolicySnapshot;
import mss301.paymentservice.dto.response.RefundTier;
import mss301.paymentservice.enums.OccurrenceStatus;
import mss301.paymentservice.enums.RefundReason;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bộ tính phí hủy / tiền hoàn theo bảng GAP_ANALYSIS §1.2 (PAY-09) trên chính sách đã snapshot lúc cọc. Hàm thuần —
 * dùng chung cho preview (refund-quotes) và lệnh hoàn thật (refunds) để hai nơi luôn ra cùng một số.
 * paid_i = cọc_i + đã thu thêm_i − đã hoàn_i; phí không bao giờ vượt số khách đã trả cho buổi đó.
 */
public final class RefundCalculator {

    public static final String NOT_PAID = "NOT_PAID";
    public static final String NOT_CANCELLABLE = "NOT_CANCELLABLE";
    public static final String COOLING_OFF = "COOLING_OFF";
    public static final String FULL_REFUND_CENTER = "FULL_REFUND_CENTER";
    public static final String FULL_REFUND_MISSED = "FULL_REFUND_MISSED";
    public static final String FULL_REFUND_SYSTEM = "FULL_REFUND_SYSTEM";
    static final String TIER_PREFIX = "TIER_";
    static final String TIER_INFINITY = "INF";
    static final long SECONDS_PER_HOUR = 3600;
    static final long SECONDS_PER_MINUTE = 60;

    private RefundCalculator() {
    }

    public static List<Line> calculate(Context context, List<OccurrenceInput> occurrences) {
        List<Line> lines = new ArrayList<>();
        for (OccurrenceInput occurrence : occurrences) {
            lines.add(calculateOne(context, occurrence));
        }
        return lines;
    }

    private static Line calculateOne(Context context, OccurrenceInput occurrence) {
        BigDecimal price = occurrence.priceAmount() == null ? BigDecimal.ZERO : occurrence.priceAmount();
        if (!context.paid() || context.policy() == null) {
            return new Line(occurrence.occurrenceId(), price, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, NOT_PAID);
        }
        BigDecimal paid = VndMoney.nonNegative(occurrence.paidAmount() == null ? BigDecimal.ZERO : occurrence.paidAmount());
        return switch (context.reason()) {
            case CENTER_REJECT, CENTER_CANCEL, CENTER_LOCKED -> fullRefund(occurrence, price, paid, FULL_REFUND_CENTER);
            case CAREGIVER_MISSED -> fullRefund(occurrence, price, paid, FULL_REFUND_MISSED);
            case CUSTOMER_CANCEL -> customerCancel(context, occurrence, price, paid);
            default -> paid.signum() > 0
                    ? fullRefund(occurrence, price, paid, FULL_REFUND_SYSTEM)
                    : new Line(occurrence.occurrenceId(), price, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, NOT_PAID);
        };
    }

    private static Line customerCancel(Context context, OccurrenceInput occurrence, BigDecimal price, BigDecimal paid) {
        // Buổi đã bắt đầu (có session) hoặc không còn ở trạng thái chưa bắt đầu: khách không hủy được, buổi tính đủ tiền.
        boolean started = occurrence.hasSession()
                || (occurrence.status() != null && !OccurrenceStatus.NOT_STARTED.contains(occurrence.status()));
        if (started) {
            return notCancellable(occurrence, price, paid);
        }
        if (isCoolingOff(context)) {
            return fullRefund(occurrence, price, paid, COOLING_OFF);
        }
        long leadSeconds = Duration.between(context.cancelledAt(), occurrence.startAt()).getSeconds();
        RefundTier tier = findTier(context.policy().getRefundTiers(), leadSeconds);
        if (tier == null) {
            return notCancellable(occurrence, price, paid);
        }
        BigDecimal fee = VndMoney.percentOf(tier.getFeePercent(), price).min(paid);
        return new Line(occurrence.occurrenceId(), price, paid, fee, paid.subtract(fee), tierRule(tier));
    }

    // Thời gian cân nhắc: hủy trong coolingOffMinutes sau khi cọc VÀ buổi sắp tới gần nhất còn ≥ coolingOffMinLeadHours.
    private static boolean isCoolingOff(Context context) {
        if (context.depositPaidAt() == null || context.firstUpcomingStartAt() == null) {
            return false;
        }
        PolicySnapshot policy = context.policy();
        long sincePaidSeconds = Duration.between(context.depositPaidAt(), context.cancelledAt()).getSeconds();
        long leadSeconds = Duration.between(context.cancelledAt(), context.firstUpcomingStartAt()).getSeconds();
        return sincePaidSeconds >= 0
                && sincePaidSeconds <= policy.getCoolingOffMinutes() * SECONDS_PER_MINUTE
                && leadSeconds >= policy.getCoolingOffMinLeadHours() * SECONDS_PER_HOUR;
    }

    // min ≤ lead < max, tính theo giờ thực (giây) — đúng 24h thuộc mốc [24, 72), đúng 72h thuộc mốc [72, ∞).
    private static RefundTier findTier(List<RefundTier> tiers, long leadSeconds) {
        if (tiers == null || leadSeconds < 0) {
            return null;
        }
        for (RefundTier tier : tiers) {
            boolean aboveMin = leadSeconds >= tier.getMinLeadHours() * SECONDS_PER_HOUR;
            boolean belowMax = tier.getMaxLeadHours() == null || leadSeconds < tier.getMaxLeadHours() * SECONDS_PER_HOUR;
            if (aboveMin && belowMax) {
                return tier;
            }
        }
        return null;
    }

    private static String tierRule(RefundTier tier) {
        String max = tier.getMaxLeadHours() == null ? TIER_INFINITY : String.valueOf(tier.getMaxLeadHours());
        return TIER_PREFIX + tier.getMinLeadHours() + "_" + max;
    }

    private static Line fullRefund(OccurrenceInput occurrence, BigDecimal price, BigDecimal paid, String rule) {
        return new Line(occurrence.occurrenceId(), price, paid, BigDecimal.ZERO, paid, rule);
    }

    private static Line notCancellable(OccurrenceInput occurrence, BigDecimal price, BigDecimal paid) {
        return new Line(occurrence.occurrenceId(), price, paid, BigDecimal.ZERO, BigDecimal.ZERO, NOT_CANCELLABLE);
    }

    /**
     * @param paid                 booking đã đặt cọc (chưa trả / chưa có dữ liệu → mọi buổi NOT_PAID)
     * @param firstUpcomingStartAt buổi chưa diễn ra gần nhất (mốc thời gian cân nhắc), có thể null
     */
    public record Context(boolean paid, PolicySnapshot policy, RefundReason reason, Instant cancelledAt,
                          Instant firstUpcomingStartAt, Instant depositPaidAt) {
    }

    // status null = không biết trạng thái phía Booking (lệnh hoàn thật: Booking đã hủy buổi trước khi gửi).
    public record OccurrenceInput(UUID occurrenceId, Instant startAt, OccurrenceStatus status, boolean hasSession,
                                  BigDecimal priceAmount, BigDecimal paidAmount) {
    }

    public record Line(UUID occurrenceId, BigDecimal priceAmount, BigDecimal paidAmount, BigDecimal feeAmount,
                       BigDecimal refundAmount, String rule) {
    }
}
