package mss301.paymentservice.util;

import mss301.paymentservice.dto.response.PolicySnapshot;
import mss301.paymentservice.enums.OccurrenceStatus;
import mss301.paymentservice.enums.RefundReason;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefundCalculatorTests {

    static final Instant PAID_AT = Instant.parse("2026-11-02T01:00:00Z");
    static final BigDecimal PRICE = new BigDecimal("1000000");
    static final BigDecimal PAID = new BigDecimal("300000");

    @Test
    void customerCancelRefundsHundredFromTheThresholdAndSeventyBelow() {
        Instant cancelledAt = PAID_AT.plus(Duration.ofHours(3));

        // Spec §10.2: còn ≥ 24h → 100 %; < 24h → 70 % của số đã trả cho buổi (M4).
        assertLine(customerCancel(cancelledAt, cancelledAt.plus(Duration.ofHours(72))), "BEFORE_THRESHOLD", "0", "300000");
        assertLine(customerCancel(cancelledAt, cancelledAt.plus(Duration.ofHours(24))), "BEFORE_THRESHOLD", "0", "300000");
        assertLine(customerCancel(cancelledAt, cancelledAt.plus(Duration.ofHours(24)).minusSeconds(1)), "AFTER_THRESHOLD",
                "90000", "210000");
        assertLine(customerCancel(cancelledAt, cancelledAt), "AFTER_THRESHOLD", "90000", "210000");
        assertLine(customerCancel(cancelledAt, cancelledAt.minusSeconds(1)), "NOT_CANCELLABLE", "0", "0");
    }

    @Test
    void percentAppliesToWhatWasPaidForTheOccurrence() {
        Instant cancelledAt = PAID_AT.plus(Duration.ofHours(3));
        RefundCalculator.Line line = RefundCalculator.calculate(context(RefundReason.CUSTOMER_CANCEL, cancelledAt),
                List.of(input(cancelledAt.plus(Duration.ofHours(2)), OccurrenceStatus.SCHEDULED, false,
                        new BigDecimal("1000000")))).getFirst();

        // Đã trả đủ 1.000.000 cho buổi → hoàn 70 %, giữ 300.000.
        assertLine(line, "AFTER_THRESHOLD", "300000", "700000");
        assertThat(line.paidAmount()).isEqualByComparingTo("1000000");
    }

    @Test
    void thresholdAndPercentsComeFromTheSnapshot() {
        PolicySnapshot policy = policy();
        policy.setRefundThresholdMinutes(2880);
        policy.setRefundPercentBeforeThreshold(BigDecimal.valueOf(90));
        policy.setRefundPercentAfterThreshold(BigDecimal.valueOf(50));
        Instant cancelledAt = PAID_AT.plus(Duration.ofHours(3));
        RefundCalculator.Context context = new RefundCalculator.Context(true, policy, RefundReason.CUSTOMER_CANCEL,
                cancelledAt);

        assertLine(RefundCalculator.calculate(context, List.of(input(cancelledAt.plus(Duration.ofHours(48)),
                OccurrenceStatus.SCHEDULED, false, PAID))).getFirst(), "BEFORE_THRESHOLD", "30000", "270000");
        assertLine(RefundCalculator.calculate(context, List.of(input(cancelledAt.plus(Duration.ofHours(30)),
                OccurrenceStatus.SCHEDULED, false, PAID))).getFirst(), "AFTER_THRESHOLD", "150000", "150000");

        // Snapshot cũ chưa có mốc hoàn → không đoán phí, hoàn đủ.
        policy.setRefundThresholdMinutes(null);
        assertLine(RefundCalculator.calculate(context, List.of(input(cancelledAt.plus(Duration.ofHours(1)),
                OccurrenceStatus.SCHEDULED, false, PAID))).getFirst(), "FULL_REFUND_SYSTEM", "0", "300000");
    }

    @Test
    void startedOccurrencesCannotBeCancelledByCustomer() {
        Instant cancelledAt = PAID_AT.plus(Duration.ofHours(3));
        Instant start = cancelledAt.plus(Duration.ofHours(100));
        RefundCalculator.Context context = context(RefundReason.CUSTOMER_CANCEL, cancelledAt);

        assertLine(RefundCalculator.calculate(context, List.of(input(start, OccurrenceStatus.SCHEDULED, true, PAID)))
                .getFirst(), "NOT_CANCELLABLE", "0", "0");
        assertLine(RefundCalculator.calculate(context, List.of(input(start, OccurrenceStatus.COMPLETED, false, PAID)))
                .getFirst(), "NOT_CANCELLABLE", "0", "0");
        // status null = lệnh hoàn thật (Booking đã hủy buổi) → không chặn.
        assertLine(RefundCalculator.calculate(context, List.of(input(start, null, false, PAID))).getFirst(),
                "BEFORE_THRESHOLD", "0", "300000");
    }

    @Test
    void centerMissedAndSystemReasonsRefundEverythingPaid() {
        Instant at = PAID_AT.plus(Duration.ofDays(3));
        Instant start = at.minusSeconds(3600);

        assertLine(calculateOne(RefundReason.CENTER_REJECT, at, start), "FULL_REFUND_CENTER", "0", "300000");
        assertLine(calculateOne(RefundReason.CENTER_CANCEL, at, start), "FULL_REFUND_CENTER", "0", "300000");
        assertLine(calculateOne(RefundReason.CENTER_LOCKED, at, start), "FULL_REFUND_CENTER", "0", "300000");
        assertLine(calculateOne(RefundReason.CAREGIVER_MISSED, at, start), "FULL_REFUND_MISSED", "0", "300000");
        assertLine(calculateOne(RefundReason.EXPIRED, at, start), "FULL_REFUND_SYSTEM", "0", "300000");
        RefundCalculator.Line unpaid = RefundCalculator.calculate(context(RefundReason.EXPIRED, at),
                List.of(input(start, null, false, BigDecimal.ZERO))).getFirst();
        assertLine(unpaid, "NOT_PAID", "0", "0");
    }

    @Test
    void overdueForfeitKeepsThePolicyPercentOfWhatWasPaid() {
        List<RefundCalculator.OccurrenceInput> inputs = List.of(
                input(PAID_AT.plus(Duration.ofDays(3)), null, false, PAID),
                input(PAID_AT.plus(Duration.ofDays(4)), null, false, new BigDecimal("300001")));

        List<RefundCalculator.Line> all = RefundCalculator.forfeit(BigDecimal.valueOf(100), inputs);
        assertLine(all.get(0), "PAYMENT_OVERDUE", "300000", "0");
        assertLine(all.get(1), "PAYMENT_OVERDUE", "300001", "0");

        List<RefundCalculator.Line> part = RefundCalculator.forfeit(BigDecimal.valueOf(40), inputs);
        assertLine(part.get(0), "PAYMENT_OVERDUE", "120000", "180000");
        assertThat(part.get(1).feeAmount().add(part.get(1).refundAmount())).isEqualByComparingTo("300001");

        assertLine(RefundCalculator.forfeit(BigDecimal.ZERO, inputs).getFirst(), "PAYMENT_OVERDUE", "0", "300000");
    }

    @Test
    void unpaidBookingIsAlwaysZero() {
        RefundCalculator.Context unpaid = new RefundCalculator.Context(false, policy(), RefundReason.CENTER_CANCEL,
                PAID_AT);
        RefundCalculator.Line line = RefundCalculator.calculate(unpaid,
                List.of(input(PAID_AT.plus(Duration.ofDays(5)), OccurrenceStatus.SCHEDULED, false, PAID))).getFirst();

        assertLine(line, "NOT_PAID", "0", "0");
        assertThat(line.paidAmount()).isEqualByComparingTo("0");
        assertThat(line.priceAmount()).isEqualByComparingTo(PRICE);
    }

    private RefundCalculator.Line customerCancel(Instant cancelledAt, Instant start) {
        return calculateOne(RefundReason.CUSTOMER_CANCEL, cancelledAt, start);
    }

    private RefundCalculator.Line calculateOne(RefundReason reason, Instant cancelledAt, Instant start) {
        return RefundCalculator.calculate(context(reason, cancelledAt),
                List.of(input(start, OccurrenceStatus.SCHEDULED, false, PAID))).getFirst();
    }

    private RefundCalculator.Context context(RefundReason reason, Instant cancelledAt) {
        return new RefundCalculator.Context(true, policy(), reason, cancelledAt);
    }

    private RefundCalculator.OccurrenceInput input(Instant start, OccurrenceStatus status, boolean hasSession,
                                                   BigDecimal paid) {
        return new RefundCalculator.OccurrenceInput(UUID.randomUUID(), start, status, hasSession, PRICE, paid);
    }

    // Mặc định center booking policy của spec §8 / Q5 / M3.
    private PolicySnapshot policy() {
        return PolicySnapshot.builder()
                .policyId(UUID.randomUUID())
                .depositPercent(BigDecimal.valueOf(30))
                .customerNoShowChargePercent(BigDecimal.valueOf(100))
                .remainingDueHours(48)
                .paymentSessionTtlMinutes(15)
                .refundThresholdMinutes(1440)
                .refundPercentBeforeThreshold(BigDecimal.valueOf(100))
                .refundPercentAfterThreshold(BigDecimal.valueOf(70))
                .overduePaymentForfeitPercent(BigDecimal.valueOf(100))
                .build();
    }

    private void assertLine(RefundCalculator.Line line, String rule, String fee, String refund) {
        assertThat(line.rule()).isEqualTo(rule);
        assertThat(line.feeAmount()).isEqualByComparingTo(fee);
        assertThat(line.refundAmount()).isEqualByComparingTo(refund);
    }
}
