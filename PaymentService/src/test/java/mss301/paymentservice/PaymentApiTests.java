package mss301.paymentservice;

import mss301.paymentservice.entity.BookingFinancial;
import mss301.paymentservice.entity.OccurrenceFinancial;
import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.entity.PaymentAttempt;
import mss301.paymentservice.entity.PaymentTransaction;
import mss301.paymentservice.entity.Refund;
import mss301.paymentservice.entity.RemainingInvoice;
import mss301.paymentservice.enums.BookingFinancialStatus;
import mss301.paymentservice.enums.InvoiceStatus;
import mss301.paymentservice.enums.PaymentStatus;
import mss301.paymentservice.enums.PaymentType;
import mss301.paymentservice.enums.RefundMode;
import mss301.paymentservice.enums.RefundStatus;
import mss301.paymentservice.enums.TransactionEventType;
import mss301.paymentservice.gateway.VnPaySigner;
import mss301.paymentservice.repository.BookingFinancialRepository;
import mss301.paymentservice.repository.OccurrenceFinancialRepository;
import mss301.paymentservice.repository.PaymentAttemptRepository;
import mss301.paymentservice.repository.PaymentRepository;
import mss301.paymentservice.repository.PaymentTransactionRepository;
import mss301.paymentservice.repository.RefundRepository;
import mss301.paymentservice.repository.RemainingInvoiceRepository;
import mss301.paymentservice.service.PaymentOutboxService;
import mss301.paymentservice.service.RefundDispatchService;
import mss301.paymentservice.service.RemainingInvoiceService;
import mss301.paymentservice.support.PaymentIntegrationTest;
import mss301.paymentservice.support.PaymentTestServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentApiTests extends PaymentIntegrationTest {

    static final long PRICE = 1_000_000L;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    BookingFinancialRepository bookingFinancialRepository;

    @Autowired
    OccurrenceFinancialRepository occurrenceFinancialRepository;

    @Autowired
    RefundRepository refundRepository;

    @Autowired
    RemainingInvoiceRepository remainingInvoiceRepository;

    @Autowired
    PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    RefundDispatchService refundDispatchService;

    @Autowired
    RemainingInvoiceService remainingInvoiceService;

    @Autowired
    PaymentOutboxService paymentOutboxService;

    @Autowired
    TransactionTemplate transactionTemplate;

    // ===== phân quyền & validation =====

    @Test
    void wrongRoleIs403BadBodyIs400MissingTokenIs401() throws Exception {
        String quoteBody = quoteBody(UUID.randomUUID(), "CONFIRMED", "CUSTOMER_CANCEL", BASE, null, List.of());
        mockMvc.perform(post("/api/v1/refund-quotes").header("Authorization", customer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content(quoteBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(6003));
        mockMvc.perform(post("/api/v1/refund-quotes").contentType(MediaType.APPLICATION_JSON).content(quoteBody))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/refund-quotes").header("Authorization", service())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"CUSTOMER_CANCEL\"}"))
                .andExpect(status().isBadRequest());
        // fail-on-unknown-properties: field lạ → 400, không âm thầm bỏ qua.
        mockMvc.perform(post("/api/v1/refunds").header("Authorization", service()).header("Idempotency-Key", "K")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":\"" + UUID.randomUUID()
                                + "\",\"amount\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(6001));
        mockMvc.perform(get("/api/v1/payments/revenue").header("Authorization", customer(UUID.randomUUID()))
                        .param("from", BASE.toString()).param("to", BASE.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/refunds/{id}/retry", UUID.randomUUID()).header("Authorization", service()))
                .andExpect(status().isForbidden());
    }

    @Test
    void effectivePolicyIsGlobalSeedAndMissingPolicyIs503() throws Exception {
        mockMvc.perform(get("/api/v1/payment-policies/effective").header("Authorization", service())
                        .param("centerId", UUID.randomUUID().toString()).param("at", BASE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.policyId").value(GLOBAL_POLICY_ID.toString()))
                .andExpect(jsonPath("$.result.depositPercent").value(30))
                .andExpect(jsonPath("$.result.refundTiers.length()").value(3));
        mockMvc.perform(get("/api/v1/payment-policies/effective").header("Authorization", customer(UUID.randomUUID()))
                        .param("at", "2025-06-01T00:00:00Z"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(6019));
    }

    // ===== checkout cọc =====

    @Test
    void checkoutAllocatesDepositPerOccurrenceAndKeepsOneLiveSession() throws Exception {
        UUID customerId = UUID.randomUUID();
        BookingSetup booking = bookingWithPrices(customerId, UUID.randomUUID(), 100_001, 200_000, 150_000);

        JsonNode first = checkout(booking);
        JsonNode second = checkout(booking);

        // 30 % × 450.001 = 135.000,3 → 135.000; buổi 1, 2 làm tròn riêng, buổi cuối nhận phần lệch.
        assertThat(second.get("amount").decimalValue()).isEqualByComparingTo("135000");
        assertThat(second.get("paymentType").asString()).isEqualTo("DEPOSIT");
        assertThat(Instant.parse(second.get("expiresAt").asString())).isEqualTo(BASE.plus(Duration.ofMinutes(15)));
        Map<String, String> params = checkoutParams(second);
        assertThat(params.get("vnp_Amount")).isEqualTo("13500000");
        assertThat(params.get("vnp_TmnCode")).isEqualTo(PaymentTestServer.VNPAY_TMN_CODE);

        PaymentAttempt firstAttempt = paymentAttemptRepository.findByReference(checkoutParams(first).get("vnp_TxnRef"))
                .orElseThrow();
        PaymentAttempt secondAttempt = paymentAttemptRepository.findByReference(params.get("vnp_TxnRef")).orElseThrow();
        assertThat(firstAttempt.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(secondAttempt.getStatus()).isEqualTo(PaymentStatus.PENDING);

        List<OccurrenceFinancial> occurrences = occurrenceFinancialRepository.findAllByBookingId(booking.bookingId());
        Map<UUID, BigDecimal> deposits = new LinkedHashMap<>();
        for (OccurrenceFinancial occurrence : occurrences) {
            deposits.put(occurrence.getOccurrenceId(), occurrence.getDepositAmount());
        }
        assertThat(deposits.get(booking.occurrenceIds().get(0))).isEqualByComparingTo("30000");
        assertThat(deposits.get(booking.occurrenceIds().get(1))).isEqualByComparingTo("60000");
        assertThat(deposits.get(booking.occurrenceIds().get(2))).isEqualByComparingTo("45000");
        BookingFinancial financial = bookingFinancialRepository.findById(booking.bookingId()).orElseThrow();
        assertThat(financial.getStatus()).isEqualTo(BookingFinancialStatus.AWAITING_DEPOSIT);
        assertThat(financial.getPolicySnapshot().getDepositPercent()).isEqualByComparingTo("30");
        assertThat(eventsOf(booking.bookingId())).filteredOn(type -> type == TransactionEventType.CHECKOUT_CREATED)
                .hasSize(2);
    }

    @Test
    void checkoutRejectsOtherCustomerMissingPriceAndCapsSessionAtDepositDue() throws Exception {
        UUID customerId = UUID.randomUUID();
        BookingSetup booking = bookingWithPrices(customerId, UUID.randomUUID(), PRICE);
        mockMvc.perform(post("/api/v1/payments/bookings/{id}/checkout", booking.bookingId())
                        .header("Authorization", customer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"VNPAY\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(6017));
        mockMvc.perform(post("/api/v1/payments/bookings/{id}/checkout", booking.bookingId())
                        .header("Authorization", customer(customerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"STRIPE\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(6007));
        mockMvc.perform(post("/api/v1/payments/bookings/{id}/checkout", booking.bookingId())
                        .header("Authorization", customer(customerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(6035));

        UUID noPriceBooking = UUID.randomUUID();
        SERVER.putBooking(noPriceBooking, customerId, UUID.randomUUID(), "AWAITING_DEPOSIT", BASE.plusSeconds(3600),
                List.of(PaymentTestServer.occurrence(UUID.randomUUID(), 1, BASE.plus(Duration.ofDays(3)), "SCHEDULED", null)));
        mockMvc.perform(post("/api/v1/payments/bookings/{id}/checkout", noPriceBooking)
                        .header("Authorization", customer(customerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"VNPAY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(6018));

        UUID soonDueBooking = UUID.randomUUID();
        SERVER.putBooking(soonDueBooking, customerId, UUID.randomUUID(), "AWAITING_DEPOSIT", BASE.plusSeconds(600),
                List.of(PaymentTestServer.occurrence(UUID.randomUUID(), 1, BASE.plus(Duration.ofDays(3)), "SCHEDULED", PRICE)));
        JsonNode checkout = checkout(new BookingSetup(soonDueBooking, customerId, null, List.of(), List.of()));
        assertThat(Instant.parse(checkout.get("expiresAt").asString())).isEqualTo(BASE.plusSeconds(600));

        clock.set(BASE.plusSeconds(600));
        mockMvc.perform(post("/api/v1/payments/bookings/{id}/checkout", soonDueBooking)
                        .header("Authorization", customer(customerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"VNPAY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(6005));
    }

    // ===== IPN =====

    @Test
    void ipnIsIdempotentAndRejectsBadSignatureOrAmount() throws Exception {
        BookingSetup booking = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE, PRICE);
        JsonNode checkout = checkout(booking);
        Map<String, String> params = checkoutParams(checkout);
        String reference = params.get("vnp_TxnRef");

        Map<String, String> forged = new LinkedHashMap<>(Map.of("vnp_TxnRef", reference, "vnp_Amount",
                params.get("vnp_Amount"), "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00",
                "vnp_SecureHash", "deadbeef"));
        mockMvc.perform(withParams(get("/api/v1/payments/vnpay/ipn"), forged))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"));
        ipnWith(reference, "100", "00").andExpect(jsonPath("$.RspCode").value("04"));
        ipnWith("VNPAY-unknown", params.get("vnp_Amount"), "00").andExpect(jsonPath("$.RspCode").value("01"));

        // Booking tạm lỗi: IPN vẫn 00 (tiền đã ghi nhận), xác nhận cọc nằm ở outbox chờ gửi lại (Đợt 1b).
        SERVER.setDepositPaidStatus(503);
        ipn(checkout, "00").andExpect(jsonPath("$.RspCode").value("00"));
        Payment payment = paymentRepository.findByBookingIdAndPaymentType(booking.bookingId(), PaymentType.DEPOSIT)
                .orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getBookingNotifiedAt()).isNull();

        SERVER.setDepositPaidStatus(200);
        ipn(checkout, "00").andExpect(jsonPath("$.RspCode").value("02"));
        clock.advance(Duration.ofMinutes(1));
        for (UUID outboxId : paymentOutboxService.findDueIds()) {
            paymentOutboxService.process(outboxId);
        }
        ipn(checkout, "00").andExpect(jsonPath("$.RspCode").value("02"));
        assertThat(SERVER.depositPaidCalls()).containsExactly(booking.bookingId());
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getBookingNotifiedAt()).isNotNull();

        BookingFinancial financial = bookingFinancialRepository.findById(booking.bookingId()).orElseThrow();
        assertThat(financial.getStatus()).isEqualTo(BookingFinancialStatus.DEPOSIT_PAID);
        assertThat(financial.getDepositPaidAt()).isEqualTo(BASE);
        assertThat(financial.getDepositPaymentId()).isEqualTo(payment.getId());
        assertThat(eventsOf(booking.bookingId())).contains(TransactionEventType.IPN,
                TransactionEventType.PAYMENT_SUCCEEDED);
        // Không ghi chuỗi chữ ký vào nhật ký.
        assertThat(paymentTransactionRepository.findAllByBookingIdOrderByCreatedAtAsc(booking.bookingId()))
                .allSatisfy(row -> assertThat(String.valueOf(row.getDetail())).doesNotContain("SecureHash"));

        // Đã trả rồi → không mở phiên mới.
        mockMvc.perform(post("/api/v1/payments/bookings/{id}/checkout", booking.bookingId())
                        .header("Authorization", customer(booking.customerId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"VNPAY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(6006));
    }

    @Test
    void failedIpnMarksAttemptFailedAndAllowsNewSession() throws Exception {
        BookingSetup booking = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE);
        JsonNode checkout = checkout(booking);
        ipn(checkout, "24").andExpect(jsonPath("$.RspCode").value("00"));
        Payment payment = paymentRepository.findByBookingIdAndPaymentType(booking.bookingId(), PaymentType.DEPOSIT)
                .orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(SERVER.depositPaidCalls()).isEmpty();
        JsonNode retry = checkout(booking);
        assertThat(retry.get("status").asString()).isEqualTo("PENDING");
    }

    // ===== refund-quotes (GAP_ANALYSIS §1.2) =====

    @Test
    void refundQuoteForUnpaidBookingIsZero() throws Exception {
        BookingSetup booking = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE);
        checkout(booking);
        JsonNode quote = quote(booking.bookingId(), "AWAITING_DEPOSIT", "CUSTOMER_CANCEL", BASE, null,
                List.of(quoteOccurrence(booking.occurrenceIds().get(0), booking.startTimes().get(0), false)));
        assertThat(quote.get("totalRefund").decimalValue()).isEqualByComparingTo("0");
        assertThat(quote.get("items").get(0).get("rule").asString()).isEqualTo("NOT_PAID");
        assertThat(quote.get("items").get(0).get("priceAmount").decimalValue()).isEqualByComparingTo("1000000");

        JsonNode unknown = quote(UUID.randomUUID(), "CONFIRMED", "CENTER_CANCEL", BASE, null,
                List.of(quoteOccurrence(UUID.randomUUID(), BASE.plus(Duration.ofDays(3)), false)));
        assertThat(unknown.get("items").get(0).get("rule").asString()).isEqualTo("NOT_PAID");
        assertThat(unknown.get("totalPaid").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void refundQuoteAppliesTierBoundariesAt24And72Hours() throws Exception {
        Instant cancelledAt = BASE.plus(Duration.ofHours(2));
        List<Instant> starts = List.of(
                cancelledAt.plus(Duration.ofHours(72)),
                cancelledAt.plus(Duration.ofHours(72)).minusSeconds(1),
                cancelledAt.plus(Duration.ofHours(24)),
                cancelledAt.plus(Duration.ofHours(24)).minusSeconds(1),
                cancelledAt.plus(Duration.ofHours(30)));
        BookingSetup booking = bookingWithStarts(UUID.randomUUID(), UUID.randomUUID(), PRICE, starts);
        payDeposit(booking);
        List<Map<String, Object>> occurrences = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            occurrences.add(quoteOccurrence(booking.occurrenceIds().get(i), starts.get(i), false));
        }
        occurrences.add(quoteOccurrence(booking.occurrenceIds().get(4), starts.get(4), true));

        JsonNode quote = quote(booking.bookingId(), "CONFIRMED", "CUSTOMER_CANCEL", cancelledAt,
                starts.get(3), occurrences);

        JsonNode items = quote.get("items");
        assertItem(items.get(0), "TIER_72_INF", "300000", "0", "300000");
        assertItem(items.get(1), "TIER_24_72", "300000", "100000", "200000");
        assertItem(items.get(2), "TIER_24_72", "300000", "100000", "200000");
        assertItem(items.get(3), "TIER_0_24", "300000", "300000", "0");
        assertItem(items.get(4), "NOT_CANCELLABLE", "300000", "0", "0");
        assertThat(quote.get("totalPaid").decimalValue()).isEqualByComparingTo("1500000");
        assertThat(quote.get("totalFee").decimalValue()).isEqualByComparingTo("500000");
        assertThat(quote.get("totalRefund").decimalValue()).isEqualByComparingTo("700000");
        assertThat(quote.get("policyId").asString()).isEqualTo(GLOBAL_POLICY_ID.toString());
        // Preview không ghi DB.
        assertThat(refundRepository.findAllByBookingIdOrderByCreatedAtAsc(booking.bookingId())).isEmpty();
    }

    @Test
    void refundQuoteCoolingOffBoundaryAndPartyRules() throws Exception {
        Instant start = BASE.plus(Duration.ofHours(1)).plus(Duration.ofHours(30));
        BookingSetup booking = bookingWithStarts(UUID.randomUUID(), UUID.randomUUID(), PRICE, List.of(start));
        payDeposit(booking);
        List<Map<String, Object>> occurrence = List.of(quoteOccurrence(booking.occurrenceIds().get(0), start, false));

        // Đúng 60' sau khi cọc và buổi đầu còn ≥ 24h → thời gian cân nhắc, hoàn đủ.
        Instant atBoundary = BASE.plus(Duration.ofMinutes(60));
        assertItem(quote(booking.bookingId(), "CONFIRMED", "CUSTOMER_CANCEL", atBoundary, start, occurrence)
                .get("items").get(0), "COOLING_OFF", "300000", "0", "300000");
        // Quá 1 giây → theo mốc (còn 30h → 10 %).
        assertItem(quote(booking.bookingId(), "CONFIRMED", "CUSTOMER_CANCEL", atBoundary.plusSeconds(1), start, occurrence)
                .get("items").get(0), "TIER_24_72", "300000", "100000", "200000");
        // Trong 60' nhưng buổi đầu còn dưới 24h → không được cân nhắc.
        Instant closeStart = BASE.plus(Duration.ofMinutes(30)).plus(Duration.ofHours(24)).minusSeconds(1);
        assertItem(quote(booking.bookingId(), "CONFIRMED", "CUSTOMER_CANCEL", BASE.plus(Duration.ofMinutes(30)),
                closeStart, List.of(quoteOccurrence(booking.occurrenceIds().get(0), closeStart, false)))
                .get("items").get(0), "TIER_0_24", "300000", "300000", "0");

        assertItem(quote(booking.bookingId(), "CONFIRMED", "CENTER_CANCEL", start.minusSeconds(60), start, occurrence)
                .get("items").get(0), "FULL_REFUND_CENTER", "300000", "0", "300000");
        assertItem(quote(booking.bookingId(), "CONFIRMED", "CENTER_REJECT", start, start, occurrence)
                .get("items").get(0), "FULL_REFUND_CENTER", "300000", "0", "300000");
        assertItem(quote(booking.bookingId(), "IN_PROGRESS", "CAREGIVER_MISSED", start.plusSeconds(1800), null, occurrence)
                .get("items").get(0), "FULL_REFUND_MISSED", "300000", "0", "300000");
        assertItem(quote(booking.bookingId(), "CONFIRMED", "EXPIRED", start, null, occurrence)
                .get("items").get(0), "FULL_REFUND_SYSTEM", "300000", "0", "300000");
        // Booking đang PENDING / AWAITING_DEPOSIT phía Booking → chưa trả.
        assertItem(quote(booking.bookingId(), "PENDING", "CUSTOMER_CANCEL", start, start, occurrence)
                .get("items").get(0), "NOT_PAID", "0", "0", "0");
    }

    // ===== refunds =====

    @Test
    void refundIsIdempotentAndNeverExceedsPaid() throws Exception {
        BookingSetup booking = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE, PRICE);
        payDeposit(booking);
        UUID first = booking.occurrenceIds().get(0);

        JsonNode created = createRefund("REFUND:CENTER_CANCEL:" + first, booking.bookingId(), List.of(first),
                "CENTER_CANCEL", BASE).get("result");
        JsonNode replay = createRefund("REFUND:CENTER_CANCEL:" + first, booking.bookingId(), List.of(first),
                "CENTER_CANCEL", BASE).get("result");
        assertThat(created.get("items")).hasSize(1);
        assertThat(created.get("totalRefund").decimalValue()).isEqualByComparingTo("300000");
        assertThat(replay.get("items").get(0).get("refundId").asString())
                .isEqualTo(created.get("items").get(0).get("refundId").asString());
        assertThat(refundRepository.findAllByBookingIdOrderByCreatedAtAsc(booking.bookingId())).hasSize(1);
        assertThat(occurrenceFinancialRepository.findById(first).orElseThrow().getRefundedAmount())
                .isEqualByComparingTo("300000");

        // Key khác cho cùng buổi: buổi đã hoàn hết → không hoàn trùng.
        JsonNode again = createRefund("REFUND:OTHER:" + first, booking.bookingId(), List.of(first), "CENTER_CANCEL",
                BASE).get("result");
        assertThat(again.get("items")).isEmpty();

        // Bất biến: hoàn + đang chờ + mới ≤ đã thu (giả lập payment bị ghi thiếu tiền).
        transactionTemplate.executeWithoutResult(status -> paymentRepository
                .findByBookingIdAndPaymentType(booking.bookingId(), PaymentType.DEPOSIT).orElseThrow()
                .setAmount(new BigDecimal("300000")));
        mockMvc.perform(post("/api/v1/refunds").header("Authorization", service())
                        .header("Idempotency-Key", "REFUND:CENTER_CANCEL:second")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(booking.bookingId(), List.of(booking.occurrenceIds().get(1)), "CENTER_CANCEL",
                                BASE, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(6020));

        // Thiếu khóa (header + body) → 400.
        mockMvc.perform(post("/api/v1/refunds").header("Authorization", service())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(booking.bookingId(), List.of(first), "CENTER_CANCEL", BASE, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(6021));

        // Booking chưa cọc → 200 rỗng (outbox Booking không retry mãi).
        BookingSetup unpaid = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE);
        JsonNode empty = createRefund("REFUND:X", unpaid.bookingId(), unpaid.occurrenceIds(), "CENTER_CANCEL", BASE)
                .get("result");
        assertThat(empty.get("items")).isEmpty();
    }

    @Test
    void customerCancelRefundUsesSnapshotTiersAndMarksOccurrenceCancelled() throws Exception {
        BookingSetup booking = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE, PRICE);
        payDeposit(booking);
        Instant cancelledAt = booking.startTimes().get(0).minus(Duration.ofHours(48));
        JsonNode result = createRefund("REFUND:CUSTOMER_CANCEL:a", booking.bookingId(), booking.occurrenceIds(),
                "CUSTOMER_CANCEL", cancelledAt).get("result");
        // Buổi 1 còn 48h → phí 10 %; buổi 2 còn 72h → hoàn đủ cọc.
        assertThat(result.get("totalRefund").decimalValue()).isEqualByComparingTo("500000");
        Refund refund = refundRepository.findAllByIdempotencyKey("REFUND:CUSTOMER_CANCEL:a").stream()
                .filter(item -> item.getOccurrenceId().equals(booking.occurrenceIds().get(0)))
                .findFirst().orElseThrow();
        assertThat(refund.getFeeAmount()).isEqualByComparingTo("100000");
        assertThat(occurrenceFinancialRepository.findById(booking.occurrenceIds().get(0)).orElseThrow().getFinalStatus())
                .hasToString("CANCELLED");
    }

    @Test
    void simulatedDispatcherRefundsAndPaymentStaysSuccess() throws Exception {
        BookingSetup booking = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE, PRICE);
        payDeposit(booking);
        createRefund("REFUND:CAREGIVER_MISSED:" + booking.occurrenceIds().get(0), booking.bookingId(),
                List.of(booking.occurrenceIds().get(0)), "CAREGIVER_MISSED", BASE);

        dispatchDueRefunds();

        Refund refund = refundRepository.findAllByBookingIdOrderByCreatedAtAsc(booking.bookingId()).getFirst();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REFUNDED);
        assertThat(refund.getProviderResponseCode()).isEqualTo("SIMULATED");
        Payment payment = paymentRepository.findById(refund.getPaymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("300000");
        assertThat(occurrenceFinancialRepository.findById(booking.occurrenceIds().get(0)).orElseThrow().getFinalStatus())
                .hasToString("MISSED");

        // Hợp đồng Booking (findRefundableAmount): lọc status=SUCCESS, đọc refundedAmount thật.
        mockMvc.perform(get("/api/v1/payments").header("Authorization", service())
                        .param("bookingId", booking.bookingId().toString()).param("status", "SUCCESS")
                        .param("page", "0").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.items[0].refundedAmount").value(300000))
                .andExpect(jsonPath("$.result.items[0].amount").value(600000))
                .andExpect(jsonPath("$.result.totalPages").value(1));
        assertThat(eventsOf(booking.bookingId())).contains(TransactionEventType.REFUND_REQUESTED,
                TransactionEventType.REFUND_SUCCEEDED);

        mockMvc.perform(get("/api/v1/refunds").header("Authorization", customer(booking.customerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.items[0].status").value("REFUNDED"));
        mockMvc.perform(get("/api/v1/refunds").header("Authorization", customer(UUID.randomUUID())))
                .andExpect(jsonPath("$.result.items.length()").value(0));
    }

    @Test
    void vnPayRefundAdapterHandlesSuccessTimeoutAndRejection() throws Exception {
        paymentProperties.getRefund().setMode(RefundMode.VNPAY);
        BookingSetup booking = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE, PRICE, PRICE);
        payDeposit(booking);
        List<UUID> occurrences = booking.occurrenceIds();

        // Thành công (00): hoàn một phần → vnp_TransactionType 03, chữ ký đúng chuỗi nối "|".
        createRefund("REFUND:A", booking.bookingId(), List.of(occurrences.get(0)), "CENTER_CANCEL", BASE);
        dispatchDueRefunds();
        Refund succeeded = refundRepository.findAllByIdempotencyKey("REFUND:A").getFirst();
        assertThat(succeeded.getStatus()).isEqualTo(RefundStatus.REFUNDED);
        JsonNode request = SERVER.vnPayRequests().getFirst();
        assertThat(request.get("vnp_Command").asString()).isEqualTo("refund");
        assertThat(request.get("vnp_TransactionType").asString()).isEqualTo("03");
        assertThat(request.get("vnp_Amount").asString()).isEqualTo("30000000");
        String data = String.join("|", request.get("vnp_RequestId").asString(), request.get("vnp_Version").asString(),
                "refund", PaymentTestServer.VNPAY_TMN_CODE, "03", request.get("vnp_TxnRef").asString(), "30000000",
                request.get("vnp_TransactionNo").asString(), request.get("vnp_TransactionDate").asString(),
                request.get("vnp_CreateBy").asString(), request.get("vnp_CreateDate").asString(),
                request.get("vnp_IpAddr").asString(), request.get("vnp_OrderInfo").asString());
        assertThat(request.get("vnp_SecureHash").asString())
                .isEqualTo(VnPaySigner.hmacSha512(PaymentTestServer.VNPAY_SECRET, data));

        // Timeout → giữ REFUND_PENDING, attempts++, hẹn lại sau (backoff).
        SERVER.setVnPayResponse("00", 200, 2_500);
        createRefund("REFUND:B", booking.bookingId(), List.of(occurrences.get(1)), "CENTER_CANCEL", BASE);
        dispatchDueRefunds();
        Refund timedOut = refundRepository.findAllByIdempotencyKey("REFUND:B").getFirst();
        assertThat(timedOut.getStatus()).isEqualTo(RefundStatus.REFUND_PENDING);
        assertThat(timedOut.getAttempts()).isEqualTo(1);
        assertThat(timedOut.getNextAttemptAt()).isAfter(clock.instant());
        assertThat(timedOut.getLastError()).isNotBlank();

        // Lỗi nghiệp vụ (mã khác 00/94) → FAILED ngay; admin thử lại → REFUND_PENDING.
        SERVER.setVnPayResponse("91", 200, 0);
        createRefund("REFUND:C", booking.bookingId(), List.of(occurrences.get(2)), "CENTER_CANCEL", BASE);
        dispatchDueRefunds();
        Refund rejected = refundRepository.findAllByIdempotencyKey("REFUND:C").getFirst();
        assertThat(rejected.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(rejected.getProviderResponseCode()).isEqualTo("91");
        mockMvc.perform(post("/api/v1/refunds/{id}/retry", timedOut.getId()).header("Authorization", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(6023));
        mockMvc.perform(post("/api/v1/refunds/{id}/retry", rejected.getId()).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("REFUND_PENDING"));

        // Lỗi tạm thời lặp lại tới trần số lần → FAILED.
        paymentProperties.getRefund().setMaxAttempts(2);
        SERVER.setVnPayResponse("00", 503, 0);
        clock.advance(Duration.ofHours(2));
        dispatchDueRefunds();
        clock.advance(Duration.ofHours(2));
        dispatchDueRefunds();
        assertThat(refundRepository.findById(timedOut.getId()).orElseThrow().getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(eventsOf(booking.bookingId())).contains(TransactionEventType.REFUND_CALL,
                TransactionEventType.REFUND_FAILED, TransactionEventType.REFUND_RETRIED);
    }

    // ===== hóa đơn REMAINING + khiếu nại =====

    @Test
    void remainingInvoiceChargesNoShowSkipsCancelledAndTracksDebt() throws Exception {
        BookingSetup booking = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE, PRICE, PRICE, PRICE);
        payDeposit(booking);
        List<UUID> occurrences = booking.occurrenceIds();
        String body = remainingBody(booking.bookingId(), List.of(occurrences.get(0), occurrences.get(1),
                occurrences.get(2), occurrences.get(3)), List.of("COMPLETED", "CUSTOMER_NO_SHOW", "CANCELLED", "MISSED"));

        JsonNode created = createInvoice(booking.bookingId(), body);
        JsonNode replay = createInvoice(booking.bookingId(), body);
        // COMPLETED 1.000.000 − 300.000 + vắng mặt 100 % 1.000.000 − 300.000; hủy / MISSED không tính.
        assertThat(created.get("amount").decimalValue()).isEqualByComparingTo("1400000");
        assertThat(created.get("status").asString()).isEqualTo("PENDING");
        assertThat(Instant.parse(created.get("dueAt").asString())).isEqualTo(BASE.plus(Duration.ofHours(48)));
        assertThat(replay.get("invoiceId").asString()).isEqualTo(created.get("invoiceId").asString());

        mockMvc.perform(get("/api/v1/payments/me/outstanding").header("Authorization", customer(booking.customerId())))
                .andExpect(jsonPath("$.result.hasDebt").value(false))
                .andExpect(jsonPath("$.result.pendingInvoices.length()").value(1));

        clock.advance(Duration.ofHours(49));
        for (UUID invoiceId : remainingInvoiceService.findOverdueCandidateIds()) {
            remainingInvoiceService.markOverdue(invoiceId);
        }
        mockMvc.perform(get("/api/v1/payments/me/outstanding").header("Authorization", customer(booking.customerId())))
                .andExpect(jsonPath("$.result.hasDebt").value(true))
                .andExpect(jsonPath("$.result.overdueInvoices[0].amount").value(1400000));
        mockMvc.perform(get("/api/v1/remaining-invoices").header("Authorization", customer(booking.customerId())))
                .andExpect(jsonPath("$.result.items[0].status").value("OVERDUE"));

        // Khách trả hóa đơn quá hạn → PAID, booking CLOSED, hết nợ.
        UUID invoiceId = UUID.fromString(created.get("invoiceId").asString());
        mockMvc.perform(post("/api/v1/remaining-invoices/{id}/payments", invoiceId)
                        .header("Authorization", customer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"VNPAY\"}"))
                .andExpect(status().isNotFound());
        JsonNode checkout = body(mockMvc.perform(post("/api/v1/remaining-invoices/{id}/payments", invoiceId)
                        .header("Authorization", customer(booking.customerId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"VNPAY\"}"))
                .andExpect(status().isOk())).get("result");
        assertThat(checkout.get("paymentType").asString()).isEqualTo("REMAINING");
        assertThat(checkout.get("amount").decimalValue()).isEqualByComparingTo("1400000");
        ipn(checkout, "00").andExpect(jsonPath("$.RspCode").value("00"));

        RemainingInvoice invoice = remainingInvoiceRepository.findById(invoiceId).orElseThrow();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(bookingFinancialRepository.findById(booking.bookingId()).orElseThrow().getStatus())
                .isEqualTo(BookingFinancialStatus.CLOSED);
        assertThat(occurrenceFinancialRepository.findById(occurrences.get(0)).orElseThrow().getChargedAmount())
                .isEqualByComparingTo("700000");
        assertThat(SERVER.depositPaidCalls()).containsExactly(booking.bookingId());
        mockMvc.perform(get("/api/v1/payments/me/outstanding").header("Authorization", customer(booking.customerId())))
                .andExpect(jsonPath("$.result.hasDebt").value(false));
        assertThat(eventsOf(booking.bookingId())).contains(TransactionEventType.INVOICE_CREATED,
                TransactionEventType.INVOICE_OVERDUE, TransactionEventType.INVOICE_PAID);
    }

    @Test
    void remainingInvoiceNotRequiredWhenNothingToChargeAndAdminCanWaive() throws Exception {
        BookingSetup cancelled = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE);
        payDeposit(cancelled);
        JsonNode none = createInvoice(cancelled.bookingId(), remainingBody(cancelled.bookingId(),
                cancelled.occurrenceIds(), List.of("CANCELLED")));
        assertThat(none.get("status").asString()).isEqualTo("NOT_REQUIRED");
        assertThat(none.get("amount").decimalValue()).isEqualByComparingTo("0");
        assertThat(none.get("invoiceId").isNull()).isTrue();
        assertThat(bookingFinancialRepository.findById(cancelled.bookingId()).orElseThrow().getStatus())
                .isEqualTo(BookingFinancialStatus.CLOSED);

        BookingSetup unpaid = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE);
        assertThat(createInvoice(unpaid.bookingId(), remainingBody(unpaid.bookingId(), unpaid.occurrenceIds(),
                List.of("COMPLETED"))).get("status").asString()).isEqualTo("NOT_REQUIRED");

        BookingSetup completed = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE);
        payDeposit(completed);
        JsonNode invoice = createInvoice(completed.bookingId(), remainingBody(completed.bookingId(),
                completed.occurrenceIds(), List.of("COMPLETED")));
        UUID invoiceId = UUID.fromString(invoice.get("invoiceId").asString());
        mockMvc.perform(patch("/api/v1/remaining-invoices/{id}/waive", invoiceId).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(6026));
        mockMvc.perform(patch("/api/v1/remaining-invoices/{id}/waive", invoiceId)
                        .header("Authorization", customer(completed.customerId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/remaining-invoices/{id}/waive", invoiceId).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Khách khó khăn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("WAIVED"));
        mockMvc.perform(post("/api/v1/remaining-invoices/{id}/payments", invoiceId)
                        .header("Authorization", customer(completed.customerId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"VNPAY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(6025));
        mockMvc.perform(get("/api/v1/remaining-invoices").header("Authorization", admin())
                        .param("customerId", completed.customerId().toString()))
                .andExpect(jsonPath("$.result.items.length()").value(1));
    }

    @Test
    void complaintRefundReducesOpenInvoiceFirstAndIsCappedByPaid() throws Exception {
        BookingSetup booking = bookingWithPrices(UUID.randomUUID(), UUID.randomUUID(), PRICE, PRICE);
        payDeposit(booking);
        createInvoice(booking.bookingId(), remainingBody(booking.bookingId(), booking.occurrenceIds(),
                List.of("COMPLETED", "COMPLETED")));
        UUID adminId = UUID.randomUUID();

        // Trần = Σ(đã thu − đã hoàn − đang chờ) = 600.000 cọc.
        complaint(booking.bookingId(), UUID.randomUUID(), "600001", adminId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(6020));

        UUID complaintA = UUID.randomUUID();
        JsonNode first = body(complaint(booking.bookingId(), complaintA, "500000", adminId).andExpect(status().isOk()));
        assertThat(first.get("result").get("items")).isEmpty();
        assertThat(invoiceOf(booking.bookingId()).getAmount()).isEqualByComparingTo("900000");
        complaint(booking.bookingId(), complaintA, "500000", adminId).andExpect(status().isOk());
        assertThat(invoiceOf(booking.bookingId()).getAmount()).isEqualByComparingTo("900000");

        complaint(booking.bookingId(), UUID.randomUUID(), "600000", adminId).andExpect(status().isOk());
        assertThat(invoiceOf(booking.bookingId()).getAmount()).isEqualByComparingTo("300000");

        // Hóa đơn còn 300.000 → giảm về 0 (WAIVED), 300.000 còn lại hoàn từ cọc.
        JsonNode third = body(complaint(booking.bookingId(), UUID.randomUUID(), "600000", adminId)
                .andExpect(status().isOk())).get("result");
        assertThat(third.get("totalRefund").decimalValue()).isEqualByComparingTo("300000");
        RemainingInvoice invoice = invoiceOf(booking.bookingId());
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.WAIVED);
        assertThat(invoice.getWaiveReason()).isEqualTo("complaint refund");
        Refund refund = refundRepository.findAllByBookingIdOrderByCreatedAtAsc(booking.bookingId()).getFirst();
        assertThat(refund.getReason()).hasToString("COMPLAINT_ACCEPTED");
        assertThat(refund.getComplaintId()).isNotNull();
        assertThat(refund.getOccurrenceId()).isNull();

        // Còn 300.000 có thể hoàn (300.000 đang chờ đã giữ chỗ).
        complaint(booking.bookingId(), UUID.randomUUID(), "300001", adminId)
                .andExpect(status().isConflict());
        assertThat(eventsOf(booking.bookingId())).contains(TransactionEventType.INVOICE_REDUCED,
                TransactionEventType.INVOICE_WAIVED);
    }

    // ===== phạm vi đọc =====

    @Test
    void paymentReadsAreScopedByOwnerAndCenter() throws Exception {
        UUID centerId = UUID.randomUUID();
        BookingSetup booking = bookingWithPrices(UUID.randomUUID(), centerId, PRICE);
        payDeposit(booking);
        UUID paymentId = paymentRepository.findByBookingIdAndPaymentType(booking.bookingId(), PaymentType.DEPOSIT)
                .orElseThrow().getId();

        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).header("Authorization", customer(booking.customerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.paymentType").value("DEPOSIT"))
                .andExpect(jsonPath("$.result.refundedAmount").value(0));
        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).header("Authorization", customer(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/payments/by-booking/{id}", booking.bookingId())
                        .header("Authorization", customer(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).header("Authorization", manager(centerId)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).header("Authorization", manager(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/payments").header("Authorization", manager(null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(6027));
        mockMvc.perform(get("/api/v1/payments").header("Authorization", manager(centerId)))
                .andExpect(jsonPath("$.result.items.length()").value(1));
        mockMvc.perform(get("/api/v1/payments").header("Authorization", customer(UUID.randomUUID())))
                .andExpect(jsonPath("$.result.items.length()").value(0));
        mockMvc.perform(get("/api/v1/payments/by-booking/{id}", booking.bookingId())
                        .header("Authorization", customer(booking.customerId())))
                .andExpect(jsonPath("$.result.id").value(paymentId.toString()));
    }

    @Test
    void revenueSubtractsRefunds() throws Exception {
        UUID centerId = UUID.randomUUID();
        BookingSetup booking = bookingWithPrices(UUID.randomUUID(), centerId, PRICE, PRICE);
        payDeposit(booking);
        createRefund("REFUND:R", booking.bookingId(), List.of(booking.occurrenceIds().get(0)), "CENTER_CANCEL", BASE);
        dispatchDueRefunds();
        mockMvc.perform(get("/api/v1/payments/revenue").header("Authorization", admin())
                        .param("from", BASE.minusSeconds(60).toString()).param("to", BASE.plusSeconds(60).toString())
                        .param("centerId", centerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.successfulPaymentCount").value(1))
                .andExpect(jsonPath("$.result.grossRevenue").value(600000))
                .andExpect(jsonPath("$.result.totalRefunded").value(300000))
                .andExpect(jsonPath("$.result.totalRevenue").value(300000));
    }

    // ===== helpers =====

    private void dispatchDueRefunds() {
        for (UUID refundId : refundDispatchService.findDueIds()) {
            refundDispatchService.process(refundId);
        }
    }

    private List<TransactionEventType> eventsOf(UUID bookingId) {
        return paymentTransactionRepository.findAllByBookingIdOrderByCreatedAtAsc(bookingId).stream()
                .map(PaymentTransaction::getEventType)
                .toList();
    }

    private RemainingInvoice invoiceOf(UUID bookingId) {
        return remainingInvoiceRepository.findByBookingId(bookingId).orElseThrow();
    }

    private JsonNode quote(UUID bookingId, String bookingStatus, String reason, Instant cancelledAt,
                           Instant firstUpcomingStartAt, List<Map<String, Object>> occurrences) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/v1/refund-quotes").header("Authorization", service())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(bookingId, bookingStatus, reason, cancelledAt, firstUpcomingStartAt,
                                occurrences)))
                .andExpect(status().isOk());
        return body(result).get("result");
    }

    private String quoteBody(UUID bookingId, String bookingStatus, String reason, Instant cancelledAt,
                             Instant firstUpcomingStartAt, List<Map<String, Object>> occurrences) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bookingId", bookingId.toString());
        body.put("bookingStatus", bookingStatus);
        body.put("cancelledBy", reason.startsWith("CENTER") ? "CENTER" : "CUSTOMER");
        body.put("reason", reason);
        body.put("cancelledAt", cancelledAt.toString());
        body.put("firstUpcomingStartAt", firstUpcomingStartAt == null ? null : firstUpcomingStartAt.toString());
        body.put("occurrences", occurrences);
        return objectMapper.writeValueAsString(body);
    }

    private Map<String, Object> quoteOccurrence(UUID occurrenceId, Instant startAt, boolean hasSession) {
        Map<String, Object> occurrence = new LinkedHashMap<>();
        occurrence.put("occurrenceId", occurrenceId.toString());
        occurrence.put("startAt", startAt.toString());
        occurrence.put("status", hasSession ? "IN_PROGRESS" : "SCHEDULED");
        occurrence.put("hasSession", hasSession);
        return occurrence;
    }

    private void assertItem(JsonNode item, String rule, String paid, String fee, String refund) {
        assertThat(item.get("rule").asString()).isEqualTo(rule);
        assertThat(item.get("paidAmount").decimalValue()).isEqualByComparingTo(paid);
        assertThat(item.get("feeAmount").decimalValue()).isEqualByComparingTo(fee);
        assertThat(item.get("refundAmount").decimalValue()).isEqualByComparingTo(refund);
    }

    private JsonNode createRefund(String key, UUID bookingId, List<UUID> occurrenceIds, String reason,
                                  Instant cancelledAt) throws Exception {
        return body(mockMvc.perform(post("/api/v1/refunds").header("Authorization", service())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(bookingId, occurrenceIds, reason, cancelledAt, key)))
                .andExpect(status().isOk()));
    }

    private String refundBody(UUID bookingId, List<UUID> occurrenceIds, String reason, Instant cancelledAt, String key)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bookingId", bookingId.toString());
        body.put("occurrenceIds", occurrenceIds.stream().map(UUID::toString).toList());
        body.put("reason", reason);
        body.put("cancelledBy", "CENTER");
        body.put("cancelledAt", cancelledAt.toString());
        body.put("firstUpcomingStartAt", null);
        body.put("requestedBy", null);
        body.put("idempotencyKey", key);
        return objectMapper.writeValueAsString(body);
    }

    private String remainingBody(UUID bookingId, List<UUID> occurrenceIds, List<String> statuses) throws Exception {
        List<Map<String, Object>> occurrences = new ArrayList<>();
        for (int i = 0; i < occurrenceIds.size(); i++) {
            occurrences.add(Map.of("occurrenceId", occurrenceIds.get(i).toString(), "finalStatus", statuses.get(i)));
        }
        return objectMapper.writeValueAsString(Map.of("bookingId", bookingId.toString(), "occurrences", occurrences));
    }

    private JsonNode createInvoice(UUID bookingId, String body) throws Exception {
        return body(mockMvc.perform(post("/api/v1/remaining-invoices").header("Authorization", service())
                        .header("Idempotency-Key", "REMAINING_INVOICE:" + bookingId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())).get("result");
    }

    private ResultActions complaint(UUID bookingId, UUID complaintId, String amount, UUID decidedBy) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("bookingId", bookingId.toString(),
                "complaintId", complaintId.toString(), "amount", new BigDecimal(amount),
                "decidedBy", decidedBy.toString(), "note", "Khiếu nại hợp lệ"));
        return mockMvc.perform(post("/api/v1/refunds/complaints").header("Authorization", service())
                .header("Idempotency-Key", "COMPLAINT_REFUND:" + complaintId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
