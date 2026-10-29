package mss301.paymentservice;

import mss301.paymentservice.entity.BookingFinancial;
import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.entity.PaymentAttempt;
import mss301.paymentservice.entity.PaymentOutbox;
import mss301.paymentservice.entity.Refund;
import mss301.paymentservice.enums.BookingFinancialStatus;
import mss301.paymentservice.enums.OutboxEventType;
import mss301.paymentservice.enums.OutboxStatus;
import mss301.paymentservice.enums.PaymentStatus;
import mss301.paymentservice.enums.PaymentType;
import mss301.paymentservice.enums.RefundMode;
import mss301.paymentservice.enums.RefundReason;
import mss301.paymentservice.enums.RefundStatus;
import mss301.paymentservice.gateway.VnPaySigner;
import mss301.paymentservice.repository.BookingFinancialRepository;
import mss301.paymentservice.repository.OccurrenceFinancialRepository;
import mss301.paymentservice.repository.PaymentAttemptRepository;
import mss301.paymentservice.repository.PaymentOutboxRepository;
import mss301.paymentservice.repository.PaymentRepository;
import mss301.paymentservice.repository.RefundRepository;
import mss301.paymentservice.service.PaymentOutboxService;
import mss301.paymentservice.service.PaymentService;
import mss301.paymentservice.service.RefundDispatchService;
import mss301.paymentservice.support.PaymentIntegrationTest;
import mss301.paymentservice.support.PaymentTestServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đợt 1b — an toàn thu tiền: outbox xác nhận cọc, Booking từ chối → hoàn, trả trễ / trả trùng, job phiên quá hạn +
 * querydr.
 */
class PaymentSafetyTests extends PaymentIntegrationTest {

    static final long PRICE = 1_000_000L;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    PaymentOutboxRepository paymentOutboxRepository;

    @Autowired
    BookingFinancialRepository bookingFinancialRepository;

    @Autowired
    OccurrenceFinancialRepository occurrenceFinancialRepository;

    @Autowired
    RefundRepository refundRepository;

    @Autowired
    PaymentOutboxService paymentOutboxService;

    @Autowired
    PaymentService paymentService;

    @Autowired
    RefundDispatchService refundDispatchService;

    @Test
    void outboxRetriesWithBackoffUntilBookingRecovers() throws Exception {
        BookingSetup booking = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE);
        SERVER.setDepositPaidStatus(503);
        payDeposit(booking);

        PaymentOutbox outbox = outboxOf(booking);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getAttempts()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(BASE.plusSeconds(30));
        assertThat(paymentOutboxService.findDueIds()).doesNotContain(outbox.getId());

        clock.advance(Duration.ofSeconds(30));
        dispatchOutbox();
        PaymentOutbox second = outboxOf(booking);
        assertThat(second.getAttempts()).isEqualTo(2);
        // Backoff lũy thừa: lần 2 chờ 60s.
        assertThat(second.getNextAttemptAt()).isEqualTo(clock.instant().plusSeconds(60));

        SERVER.setDepositPaidStatus(200);
        clock.advance(Duration.ofSeconds(60));
        dispatchOutbox();
        assertThat(outboxOf(booking).getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(SERVER.depositPaidCalls()).containsExactly(booking.bookingId());
    }

    @Test
    void bookingRejectingDepositRefundsWholeDeposit() throws Exception {
        BookingSetup booking = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE, PRICE);
        SERVER.setDepositPaidStatus(409);
        payDeposit(booking);

        assertThat(outboxOf(booking).getStatus()).isEqualTo(OutboxStatus.FAILED);
        Refund refund = refundRepository.findAllByBookingIdOrderByCreatedAtAsc(booking.bookingId()).getFirst();
        assertThat(refund.getReason()).isEqualTo(RefundReason.BOOKING_NOT_CONFIRMABLE);
        assertThat(refund.getAmount()).isEqualByComparingTo("600000");
        assertThat(refund.getAttemptId()).isNull();
        BookingFinancial financial = bookingFinancialRepository.findById(booking.bookingId()).orElseThrow();
        assertThat(financial.getStatus()).isEqualTo(BookingFinancialStatus.CLOSED);
        assertThat(occurrenceFinancialRepository.findById(booking.occurrenceIds().get(0)).orElseThrow()
                .getRefundedAmount()).isEqualByComparingTo("300000");

        dispatchRefunds();
        Payment payment = paymentRepository.findById(refund.getPaymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("600000");
        // Lệnh hoàn sau (Booking hủy) không hoàn thêm.
        JsonNode again = body(mockMvc.perform(post("/api/v1/refunds").header("Authorization", service())
                        .header("Idempotency-Key", "REFUND:LATER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("bookingId", booking.bookingId().toString(),
                                "occurrenceIds", List.of(booking.occurrenceIds().get(0).toString()),
                                "reason", "CENTER_CANCEL", "cancelledAt", BASE.toString()))))
                .andExpect(status().isOk())).get("result");
        assertThat(again.get("items")).isEmpty();
    }

    @Test
    void secondPaymentOnSupersededSessionIsRefundedThroughThatSession() throws Exception {
        paymentProperties.getRefund().setMode(RefundMode.VNPAY);
        BookingSetup booking = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE);
        JsonNode first = checkout(booking);
        JsonNode second = checkout(booking);
        ipn(second, "00").andExpect(jsonPath("$.RspCode").value("00"));

        // Khách vẫn trả xong trên tab cũ: VNPay báo thành công cho phiên đã bị thay.
        ipn(first, "00").andExpect(jsonPath("$.RspCode").value("00"));
        ipn(first, "00").andExpect(jsonPath("$.RspCode").value("02"));

        String firstReference = checkoutParams(first).get("vnp_TxnRef");
        PaymentAttempt duplicateAttempt = paymentAttemptRepository.findByReference(firstReference).orElseThrow();
        assertThat(duplicateAttempt.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        List<Refund> refunds = refundRepository.findAllByBookingIdOrderByCreatedAtAsc(booking.bookingId());
        assertThat(refunds).hasSize(1);
        assertThat(refunds.getFirst().getReason()).isEqualTo(RefundReason.DUPLICATE);
        assertThat(refunds.getFirst().getAttemptId()).isEqualTo(duplicateAttempt.getId());
        assertThat(refunds.getFirst().getAmount()).isEqualByComparingTo("300000");

        dispatchRefunds();
        assertThat(refundRepository.findById(refunds.getFirst().getId()).orElseThrow().getStatus())
                .isEqualTo(RefundStatus.REFUNDED);
        JsonNode request = SERVER.vnPayRequests().getLast();
        assertThat(request.get("vnp_TxnRef").asString()).isEqualTo(firstReference);
        assertThat(request.get("vnp_TransactionType").asString()).isEqualTo("02");
        // Tiền trùng nằm ngoài payment: payment vẫn 300.000 đã thu, chưa hoàn gì.
        Payment payment = paymentRepository.findByBookingIdAndPaymentType(booking.bookingId(), PaymentType.DEPOSIT)
                .orElseThrow();
        assertThat(payment.getAmount()).isEqualByComparingTo("300000");
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("0");
        assertThat(SERVER.depositPaidCalls()).containsExactly(booking.bookingId());
    }

    @Test
    void expiredSessionPaidLateIsAcceptedWhileBookingStillAwaitsDeposit() throws Exception {
        BookingSetup booking = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE);
        JsonNode checkout = checkout(booking);
        String reference = checkoutParams(checkout).get("vnp_TxnRef");

        clock.advance(Duration.ofMinutes(15 + 5));
        reconcileStale();
        assertThat(paymentAttemptRepository.findByReference(reference).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.EXPIRED);
        assertThat(paymentRepository.findByBookingIdAndPaymentType(booking.bookingId(), PaymentType.DEPOSIT)
                .orElseThrow().getStatus()).isEqualTo(PaymentStatus.EXPIRED);

        ipn(checkout, "00").andExpect(jsonPath("$.RspCode").value("00"));
        Payment payment = paymentRepository.findByBookingIdAndPaymentType(booking.bookingId(), PaymentType.DEPOSIT)
                .orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(bookingFinancialRepository.findById(booking.bookingId()).orElseThrow().getStatus())
                .isEqualTo(BookingFinancialStatus.DEPOSIT_PAID);
        assertThat(SERVER.depositPaidCalls()).containsExactly(booking.bookingId());
        assertThat(refundRepository.findAllByBookingIdOrderByCreatedAtAsc(booking.bookingId())).isEmpty();
    }

    @Test
    void latePaymentForStalePriceIsRefundedAndPaymentStaysOpen() throws Exception {
        UUID customerId = UUID.randomUUID();
        BookingSetup booking = bookingWithPrices(customerId, UUID.randomUUID(), PRICE);
        JsonNode stale = checkout(booking);
        // Giá đổi trước khi khách trả → phiên mới số tiền khác, phiên cũ EXPIRED.
        SERVER.putBooking(booking.bookingId(), customerId, booking.centerId(), "AWAITING_DEPOSIT",
                BASE.plus(Duration.ofHours(24)), List.of(PaymentTestServer.occurrence(booking.occurrenceIds().get(0), 1,
                        booking.startTimes().get(0), "SCHEDULED", 2_000_000L)));
        JsonNode current = checkout(booking);
        assertThat(current.get("amount").decimalValue()).isEqualByComparingTo("600000");

        ipn(stale, "00").andExpect(jsonPath("$.RspCode").value("00"));

        Refund refund = refundRepository.findAllByBookingIdOrderByCreatedAtAsc(booking.bookingId()).getFirst();
        assertThat(refund.getReason()).isEqualTo(RefundReason.LATE_PAYMENT);
        assertThat(refund.getAmount()).isEqualByComparingTo("300000");
        assertThat(paymentRepository.findByBookingIdAndPaymentType(booking.bookingId(), PaymentType.DEPOSIT)
                .orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(SERVER.depositPaidCalls()).isEmpty();

        ipn(current, "00").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(SERVER.depositPaidCalls()).containsExactly(booking.bookingId());
    }

    @Test
    void querydrConfirmsPaidSessionOrExpiresUnpaidOne() throws Exception {
        paymentProperties.getVnpay().setQuerydrEnabled(true);
        BookingSetup paid = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE);
        BookingSetup unpaid = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE);
        BookingSetup unknown = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE);
        String paidReference = checkoutParams(checkout(paid)).get("vnp_TxnRef");
        String unpaidReference = checkoutParams(checkout(unpaid)).get("vnp_TxnRef");
        String unknownReference = checkoutParams(checkout(unknown)).get("vnp_TxnRef");
        clock.advance(Duration.ofMinutes(25));

        SERVER.setQueryTransactionStatus("00");
        paymentService.reconcileAttempt(attemptId(paidReference));
        SERVER.setQueryTransactionStatus("01");
        paymentService.reconcileAttempt(attemptId(unpaidReference));
        SERVER.setVnPayResponse("00", 500, 0);
        paymentService.reconcileAttempt(attemptId(unknownReference));

        assertThat(paymentAttemptRepository.findByReference(paidReference).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCESS);
        assertThat(SERVER.depositPaidCalls()).containsExactly(paid.bookingId());
        assertThat(paymentAttemptRepository.findByReference(unpaidReference).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.EXPIRED);
        // VNPay không trả lời → để lượt sau hỏi lại.
        assertThat(paymentAttemptRepository.findByReference(unknownReference).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);

        JsonNode query = SERVER.vnPayRequests().getFirst();
        assertThat(query.get("vnp_Command").asString()).isEqualTo("querydr");
        String data = String.join("|", query.get("vnp_RequestId").asString(), query.get("vnp_Version").asString(),
                "querydr", PaymentTestServer.VNPAY_TMN_CODE, query.get("vnp_TxnRef").asString(),
                query.get("vnp_TransactionDate").asString(), query.get("vnp_CreateDate").asString(),
                query.get("vnp_IpAddr").asString(), query.get("vnp_OrderInfo").asString());
        assertThat(query.get("vnp_SecureHash").asString())
                .isEqualTo(VnPaySigner.hmacSha512(PaymentTestServer.VNPAY_SECRET, data));

        // Quá thời gian tối đa vẫn không rõ → đánh EXPIRED.
        clock.advance(Duration.ofHours(25));
        paymentService.reconcileAttempt(attemptId(unknownReference));
        assertThat(paymentAttemptRepository.findByReference(unknownReference).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void remainingPaidAfterWaiveIsRefundedAsDuplicate() throws Exception {
        BookingSetup booking = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE);
        payDeposit(booking);
        JsonNode invoice = body(mockMvc.perform(post("/api/v1/remaining-invoices").header("Authorization", service())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("bookingId", booking.bookingId().toString(),
                                "occurrences", List.of(Map.of("occurrenceId", booking.occurrenceIds().get(0).toString(),
                                        "finalStatus", "COMPLETED"))))))
                .andExpect(status().isOk())).get("result");
        UUID invoiceId = UUID.fromString(invoice.get("invoiceId").asString());
        JsonNode checkout = body(mockMvc.perform(post("/api/v1/remaining-invoices/{id}/payments", invoiceId)
                        .header("Authorization", customer(booking.customerId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"VNPAY\"}"))
                .andExpect(status().isOk())).get("result");
        mockMvc.perform(patch("/api/v1/remaining-invoices/{id}/waive", invoiceId).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Miễn nợ\"}"))
                .andExpect(status().isOk());

        ipn(checkout, "00").andExpect(jsonPath("$.RspCode").value("00"));

        Payment remaining = paymentRepository.findByBookingIdAndPaymentType(booking.bookingId(), PaymentType.REMAINING)
                .orElseThrow();
        assertThat(remaining.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        Refund refund = refundRepository.findAllByBookingIdOrderByCreatedAtAsc(booking.bookingId()).getFirst();
        assertThat(refund.getReason()).isEqualTo(RefundReason.DUPLICATE);
        assertThat(refund.getPaymentId()).isEqualTo(remaining.getId());
        assertThat(refund.getAmount()).isEqualByComparingTo("700000");
    }

    private PaymentOutbox outboxOf(BookingSetup booking) {
        Payment payment = paymentRepository.findByBookingIdAndPaymentType(booking.bookingId(), PaymentType.DEPOSIT)
                .orElseThrow();
        return paymentOutboxRepository.findByEventTypeAndPaymentId(OutboxEventType.DEPOSIT_CONFIRMATION,
                payment.getId()).orElseThrow();
    }

    private UUID attemptId(String reference) {
        return paymentAttemptRepository.findByReference(reference).orElseThrow().getId();
    }

    private void dispatchOutbox() {
        for (UUID outboxId : paymentOutboxService.findDueIds()) {
            paymentOutboxService.process(outboxId);
        }
    }

    private void dispatchRefunds() {
        for (UUID refundId : refundDispatchService.findDueIds()) {
            refundDispatchService.process(refundId);
        }
    }

    private void reconcileStale() {
        for (UUID attemptId : paymentService.findStaleAttemptIds()) {
            paymentService.reconcileAttempt(attemptId);
        }
    }
}
