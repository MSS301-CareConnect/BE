package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.RefundQuote;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.enums.CancelledBy;
import mss301.bookingservice.service.Impl.RefundPolicyImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// Trung tâm / hệ thống hủy → hoàn 100%; khách hủy → chưa tính (TODO % theo mốc ngày).
class RefundPolicyTests {

    RefundPolicy refundPolicy = new RefundPolicyImpl();

    @Test
    void centerCancellationRefundsEverything() {
        Booking booking = new Booking();
        booking.setDepositAmount(new BigDecimal("300000.00"));
        RefundQuote quote = refundPolicy.quote(booking, CancelledBy.CENTER, Instant.now());
        assertThat(quote.getCancelledBy()).isEqualTo(CancelledBy.CENTER);
        assertThat(quote.getRefundPercent()).isEqualTo(100);
        assertThat(quote.getRefundAmount()).isEqualByComparingTo("300000.00");
    }

    @Test
    void systemExpiryRefundsEverythingButAmountUnknownWithoutDeposit() {
        RefundQuote quote = refundPolicy.quote(new Booking(), CancelledBy.SYSTEM, Instant.now());
        assertThat(quote.getRefundPercent()).isEqualTo(100);
        assertThat(quote.getRefundAmount()).isNull();
    }

    @Test
    void customerCancellationIsNotCalculatedYet() {
        Booking booking = new Booking();
        booking.setDepositAmount(new BigDecimal("300000.00"));
        RefundQuote quote = refundPolicy.quote(booking, CancelledBy.CUSTOMER, Instant.now());
        assertThat(quote.getRefundPercent()).isNull();
        assertThat(quote.getRefundAmount()).isNull();
    }
}
