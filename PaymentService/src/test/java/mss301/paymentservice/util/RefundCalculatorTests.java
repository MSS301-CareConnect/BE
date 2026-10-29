package mss301.paymentservice.util;

import mss301.paymentservice.dto.response.PolicySnapshot;
import mss301.paymentservice.dto.response.RefundTier;
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
    void customerCancelTiersUseHalfOpenIntervals() {
        Instant cancelledAt = PAID_AT.plus(Duration.ofHours(3));

        assertLine(customerCancel(cancelledAt, cancelledAt.plus(Duration.ofHours(72))), "TIER_72_INF", "0", "300000");
        assertLine(customerCancel(cancelledAt, cancelledAt.plus(Duration.ofHours(72)).minusSeconds(1)), "TIER_24_72",
                "100000", "200000");
        assertLine(customerCancel(cancelledAt, cancelledAt.plus(Duration.ofHours(24))), "TIER_24_72", "100000", "200000");
        assertLine(customerCancel(cancelledAt, cancelledAt.plus(Duration.ofHours(24)).minusSeconds(1)), "TIER_0_24",
                "300000", "0");
        assertLine(customerCancel(cancelledAt, cancelledAt), "TIER_0_24", "300000", "0");
        assertLine(customerCancel(cancelledAt, cancelledAt.minusSeconds(1)), "NOT_CANCELLABLE", "0", "0");
    }

    @Test
    void feeNeverExceedsWhatWasPaid() {
        Instant cancelledAt = PAID_AT.plus(Duration.ofHours(3));
        RefundCalculator.Line line = RefundCalculator.calculate(context(RefundReason.CUSTOMER_CANCEL, cancelledAt, null),
                List.of(input(cancelledAt.plus(Duration.ofHours(30)), OccurrenceStatus.SCHEDULED, false,
                        new BigDecimal("50000")))).getFirst();

        // Phí 10 % × 1.000.000 = 100.000 nhưng khách chỉ còn 50.000 đã trả cho buổi này.
        assertLine(line, "TIER_24_72", "50000", "0");
    }

    @Test
    void coolingOffNeedsBothShortTimeSincePaymentAndLongLeadTime() {
        Instant start = PAID_AT.plus(Duration.ofHours(40));
        Instant inWindow = PAID_AT.plus(Duration.ofMinutes(60));

        assertLine(calculateOne(RefundReason.CUSTOMER_CANCEL, inWindow, start, start), "COOLING_OFF", "0", "300000");
        assertLine(calculateOne(RefundReason.CUSTOMER_CANCEL, inWindow.plusSeconds(1), start, start), "TIER_24_72",
                "100000", "200000");
        Instant firstSoon = inWindow.plus(Duration.ofHours(24)).minusSeconds(1);
        assertLine(calculateOne(RefundReason.CUSTOMER_CANCEL, inWindow, firstSoon, start), "TIER_24_72", "100000",
                "200000");
        assertLine(calculateOne(RefundReason.CUSTOMER_CANCEL, inWindow, inWindow.plus(Duration.ofHours(24)), start),
                "COOLING_OFF", "0", "300000");
    }

    @Test
    void startedOccurrencesCannotBeCancelledByCustomer() {
        Instant cancelledAt = PAID_AT.plus(Duration.ofHours(3));
        Instant start = cancelledAt.plus(Duration.ofHours(100));
        RefundCalculator.Context context = context(RefundReason.CUSTOMER_CANCEL, cancelledAt, null);

        assertLine(RefundCalculator.calculate(context, List.of(input(start, OccurrenceStatus.SCHEDULED, true, PAID)))
                .getFirst(), "NOT_CANCELLABLE", "0", "0");
        assertLine(RefundCalculator.calculate(context, List.of(input(start, OccurrenceStatus.COMPLETED, false, PAID)))
                .getFirst(), "NOT_CANCELLABLE", "0", "0");
        // status null = lệnh hoàn thật (Booking đã hủy buổi) → không chặn.
        assertLine(RefundCalculator.calculate(context, List.of(input(start, null, false, PAID))).getFirst(),
                "TIER_72_INF", "0", "300000");
    }

    @Test
    void centerMissedAndSystemReasonsRefundEverythingPaid() {
        Instant at = PAID_AT.plus(Duration.ofDays(3));
        Instant start = at.minusSeconds(3600);

        assertLine(calculateOne(RefundReason.CENTER_REJECT, at, null, start), "FULL_REFUND_CENTER", "0", "300000");
        assertLine(calculateOne(RefundReason.CENTER_CANCEL, at, null, start), "FULL_REFUND_CENTER", "0", "300000");
        assertLine(calculateOne(RefundReason.CENTER_LOCKED, at, null, start), "FULL_REFUND_CENTER", "0", "300000");
        assertLine(calculateOne(RefundReason.CAREGIVER_MISSED, at, null, start), "FULL_REFUND_MISSED", "0", "300000");
        assertLine(calculateOne(RefundReason.EXPIRED, at, null, start), "FULL_REFUND_SYSTEM", "0", "300000");
        RefundCalculator.Line unpaid = RefundCalculator.calculate(context(RefundReason.EXPIRED, at, null),
                List.of(input(start, null, false, BigDecimal.ZERO))).getFirst();
        assertLine(unpaid, "NOT_PAID", "0", "0");
    }

    @Test
    void unpaidBookingIsAlwaysZero() {
        RefundCalculator.Context unpaid = new RefundCalculator.Context(false, policy(), RefundReason.CENTER_CANCEL,
                PAID_AT, null, null);
        RefundCalculator.Line line = RefundCalculator.calculate(unpaid,
                List.of(input(PAID_AT.plus(Duration.ofDays(5)), OccurrenceStatus.SCHEDULED, false, PAID))).getFirst();

        assertLine(line, "NOT_PAID", "0", "0");
        assertThat(line.paidAmount()).isEqualByComparingTo("0");
        assertThat(line.priceAmount()).isEqualByComparingTo(PRICE);
    }

    private RefundCalculator.Line customerCancel(Instant cancelledAt, Instant start) {
        return calculateOne(RefundReason.CUSTOMER_CANCEL, cancelledAt, null, start);
    }

    private RefundCalculator.Line calculateOne(RefundReason reason, Instant cancelledAt, Instant firstUpcoming,
                                               Instant start) {
        return RefundCalculator.calculate(context(reason, cancelledAt, firstUpcoming),
                List.of(input(start, OccurrenceStatus.SCHEDULED, false, PAID))).getFirst();
    }

    private RefundCalculator.Context context(RefundReason reason, Instant cancelledAt, Instant firstUpcoming) {
        return new RefundCalculator.Context(true, policy(), reason, cancelledAt, firstUpcoming, PAID_AT);
    }

    private RefundCalculator.OccurrenceInput input(Instant start, OccurrenceStatus status, boolean hasSession,
                                                   BigDecimal paid) {
        return new RefundCalculator.OccurrenceInput(UUID.randomUUID(), start, status, hasSession, PRICE, paid);
    }

    private PolicySnapshot policy() {
        return PolicySnapshot.builder()
                .policyId(UUID.randomUUID())
                .depositPercent(BigDecimal.valueOf(30))
                .coolingOffMinutes(60)
                .coolingOffMinLeadHours(24)
                .refundTiers(List.of(new RefundTier(0, 24, BigDecimal.valueOf(30)),
                        new RefundTier(24, 72, BigDecimal.TEN),
                        new RefundTier(72, null, BigDecimal.ZERO)))
                .customerNoShowChargePercent(BigDecimal.valueOf(100))
                .remainingDueHours(48)
                .paymentSessionTtlMinutes(15)
                .build();
    }

    private void assertLine(RefundCalculator.Line line, String rule, String fee, String refund) {
        assertThat(line.rule()).isEqualTo(rule);
        assertThat(line.feeAmount()).isEqualByComparingTo(fee);
        assertThat(line.refundAmount()).isEqualByComparingTo(refund);
    }
}
