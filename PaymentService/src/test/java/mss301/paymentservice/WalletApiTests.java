package mss301.paymentservice;

import mss301.paymentservice.entity.OccurrenceFinancial;
import mss301.paymentservice.entity.Refund;
import mss301.paymentservice.entity.Wallet;
import mss301.paymentservice.entity.WalletTransaction;
import mss301.paymentservice.enums.RefundChannel;
import mss301.paymentservice.enums.RefundMode;
import mss301.paymentservice.enums.RefundStatus;
import mss301.paymentservice.enums.WalletOwnerType;
import mss301.paymentservice.enums.WalletTransactionType;
import mss301.paymentservice.repository.OccurrenceFinancialRepository;
import mss301.paymentservice.repository.RefundRepository;
import mss301.paymentservice.repository.RemainingInvoiceRepository;
import mss301.paymentservice.repository.WalletRepository;
import mss301.paymentservice.repository.WalletTransactionRepository;
import mss301.paymentservice.service.RefundDispatchService;
import mss301.paymentservice.service.RemainingInvoiceService;
import mss301.paymentservice.service.WalletSettlementService;
import mss301.paymentservice.support.PaymentIntegrationTest;
import mss301.paymentservice.support.PaymentTestServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Ví 2026-11-09: hoàn vào ví khi khách hủy, cọc bị giữ → caregiver / trung tâm, rút tiền, cộng tiền buổi hoàn tất, trừ khiếu nại.
// 2026-11-10: lỗi phía trung tâm (caregiver vắng, trung tâm hủy) cũng hoàn vào ví; hoàn khiếu nại không trả hai lần.
class WalletApiTests extends PaymentIntegrationTest {
    static final long PRICE = 1_000_000L;
    static final String FROM = "2000-01-01T00:00:00Z";
    static final String TO = "2100-01-01T00:00:00Z";
    static final UUID PLATFORM_OWNER = new UUID(0L, 0L);

    @Autowired
    RefundDispatchService refundDispatchService;

    @Autowired
    WalletSettlementService walletSettlementService;

    @Autowired
    RefundRepository refundRepository;

    @Autowired
    OccurrenceFinancialRepository occurrenceFinancialRepository;

    @Autowired
    WalletRepository walletRepository;

    @Autowired
    WalletTransactionRepository walletTransactionRepository;

    @Autowired
    RemainingInvoiceRepository remainingInvoiceRepository;

    @Autowired
    RemainingInvoiceService remainingInvoiceService;

    @Test
    void customerCancelRefundsIntoWalletAndKeptDepositGoesToCaregiverOrCenter() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID centerId = UUID.randomUUID();
        UUID caregiverUserId = UUID.randomUUID();
        Instant cancelledAt = BASE.plus(Duration.ofHours(1));
        // Buổi 1 có caregiver, buổi 2 chưa phân công; cả hai còn 12h → hoàn 70 % cọc (210.000), giữ 90.000.
        CancelSetup setup = paidBooking(customerId, centerId, caregiverUserId, cancelledAt.plus(Duration.ofHours(12)));

        for (int attempt = 0; attempt < 2; attempt++) {
            createRefund("REFUND:CUSTOMER_CANCEL:w1", setup.bookingId(), setup.occurrenceIds(), "CUSTOMER_CANCEL",
                    cancelledAt);
        }
        // Lệnh khác (key khác) cho cùng buổi: buổi đã có kết cục → không giữ cọc / hoàn lần hai.
        createRefund("REFUND:CUSTOMER_CANCEL:other", setup.bookingId(), setup.occurrenceIds(), "CUSTOMER_CANCEL",
                cancelledAt);

        List<Refund> refunds = refundRepository.findAllByBookingIdOrderByCreatedAtAsc(setup.bookingId());
        assertThat(refunds).hasSize(2).allSatisfy(refund -> {
            assertThat(refund.getChannel()).isEqualTo(RefundChannel.WALLET);
            assertThat(refund.getAmount()).isEqualByComparingTo("210000");
        });
        assertThat(balanceOf(WalletOwnerType.CAREGIVER, caregiverUserId)).isEqualByComparingTo("90000");
        assertThat(balanceOf(WalletOwnerType.CENTER, centerId)).isEqualByComparingTo("90000");
        assertThat(walletRepository.findByOwnerTypeAndOwnerId(WalletOwnerType.CUSTOMER, customerId)).isEmpty();

        // Kênh WALLET không gọi VNPay kể cả khi đang ở chế độ VNPAY; dispatch lại không cộng trùng.
        paymentProperties.getRefund().setMode(RefundMode.VNPAY);
        dispatchDueRefunds();
        clock.advance(Duration.ofHours(1));
        dispatchDueRefunds();
        assertThat(SERVER.vnPayRequests()).isEmpty();
        assertThat(refundRepository.findAllByBookingIdOrderByCreatedAtAsc(setup.bookingId()))
                .allSatisfy(refund -> {
                    assertThat(refund.getStatus()).isEqualTo(RefundStatus.REFUNDED);
                    assertThat(refund.getProviderResponseCode()).isEqualTo("WALLET");
                });
        assertThat(balanceOf(WalletOwnerType.CUSTOMER, customerId)).isEqualByComparingTo("420000");
        Wallet customerWallet = walletRepository.findByOwnerTypeAndOwnerId(WalletOwnerType.CUSTOMER, customerId)
                .orElseThrow();
        List<WalletTransaction> ledger = ledgerOf(customerWallet.getId());
        assertThat(ledger).hasSize(2).allSatisfy(transaction -> {
            assertThat(transaction.getBookingId()).isEqualTo(setup.bookingId());
            assertThat(transaction.getAvailableAfter().subtract(transaction.getAvailableBefore()))
                    .isEqualByComparingTo("210000");
        });
        assertThat(ledger).extracting(transaction -> transaction.getAvailableAfter().stripTrailingZeros().toPlainString())
                .containsExactlyInAnyOrder("210000", "420000");

        mockMvc.perform(get("/api/v1/wallets/me").header("Authorization", customer(customerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.ownerType").value("CUSTOMER"))
                .andExpect(jsonPath("$.result.availableBalance").value(420000))
                .andExpect(jsonPath("$.result.withdrawable").value(true));
        mockMvc.perform(get("/api/v1/wallets/me/transactions").header("Authorization", customer(customerId))
                        .param("from", FROM).param("to", TO))
                .andExpect(jsonPath("$.result.items.length()").value(2))
                .andExpect(jsonPath("$.result.items[0].type").value("REFUND_CREDIT"))
                .andExpect(jsonPath("$.result.items[*].availableAfter", containsInAnyOrder(210000.0, 420000.0)));
        mockMvc.perform(get("/api/v1/wallets/me/statistics").header("Authorization", customer(customerId))
                        .param("from", BASE.minus(Duration.ofDays(1)).toString())
                        .param("to", BASE.plus(Duration.ofDays(2)).toString()))
                .andExpect(jsonPath("$.result.totalPaid").value(600000))
                .andExpect(jsonPath("$.result.refundedToWallet").value(420000))
                .andExpect(jsonPath("$.result.netSpent").value(180000))
                .andExpect(jsonPath("$.result.series.length()").value(4))
                .andExpect(jsonPath("$.result.commissionIncome").doesNotExist());
        mockMvc.perform(get("/api/v1/refunds").header("Authorization", customer(customerId)))
                .andExpect(jsonPath("$.result.items[0].channel").value("WALLET"));
        mockMvc.perform(get("/api/v1/wallets/me/statistics")
                        .header("Authorization", SERVER.bearer(caregiverUserId, centerId, "CAREGIVER"))
                        .param("from", "2026-01-01T00:00:00Z").param("to", "2026-12-31T00:00:00Z")
                        .param("groupBy", "MONTH"))
                .andExpect(jsonPath("$.result.series.length()").value(12))
                .andExpect(jsonPath("$.result.forfeitIncome").value(90000))
                .andExpect(jsonPath("$.result.bookingCount").value(1));

        // Lý do hệ thống (không phải lỗi trung tâm) → vẫn hoàn 100 % qua cổng.
        CancelSetup other = paidBooking(customerId, centerId, null, BASE.plus(Duration.ofDays(3)));
        createRefund("REFUND:EXPIRED:w2", other.bookingId(), other.occurrenceIds(), "EXPIRED", BASE);
        assertThat(refundRepository.findAllByBookingIdOrderByCreatedAtAsc(other.bookingId()))
                .allSatisfy(refund -> assertThat(refund.getChannel()).isEqualTo(RefundChannel.GATEWAY));
    }

    // 2026-11-10: caregiver vắng / trung tâm hủy → khách nhận lại 100 % tiền buổi vào ví ngay, không ai được giữ phần nào,
    // buổi vắng không được cộng cho trung tâm.
    @Test
    void caregiverMissedAndCenterCancelRefundTheWholeVisitIntoTheCustomerWallet() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID centerId = UUID.randomUUID();
        UUID caregiverUserId = UUID.randomUUID();
        CancelSetup setup = paidBooking(customerId, centerId, caregiverUserId, BASE.plus(Duration.ofHours(5)));
        UUID missed = setup.occurrenceIds().get(0);
        UUID cancelled = setup.occurrenceIds().get(1);

        // Buổi 1: quá hạn check-in, Booking báo CAREGIVER_MISSED (gửi lại cùng key không hoàn trùng).
        Instant missedAt = BASE.plus(Duration.ofHours(5)).plus(Duration.ofMinutes(30));
        for (int attempt = 0; attempt < 2; attempt++) {
            createRefund("REFUND:CAREGIVER_MISSED:" + missed, setup.bookingId(), List.of(missed), "CAREGIVER_MISSED",
                    missedAt);
        }
        // Buổi 2: trung tâm hủy sát giờ — vẫn hoàn đủ.
        createRefund("REFUND:CENTER_CANCEL:" + cancelled, setup.bookingId(), List.of(cancelled), "CENTER_CANCEL",
                missedAt);

        List<Refund> refunds = refundRepository.findAllByBookingIdOrderByCreatedAtAsc(setup.bookingId());
        assertThat(refunds).hasSize(2).allSatisfy(refund -> {
            assertThat(refund.getChannel()).isEqualTo(RefundChannel.WALLET);
            assertThat(refund.getAmount()).isEqualByComparingTo("300000");
            assertThat(refund.getFeeAmount()).isEqualByComparingTo("0");
        });
        assertThat(occurrenceFinancialRepository.findById(missed).orElseThrow().getFinalStatus()).hasToString("MISSED");

        paymentProperties.getRefund().setMode(RefundMode.VNPAY);
        dispatchDueRefunds();
        assertThat(SERVER.vnPayRequests()).isEmpty();
        assertThat(balanceOf(WalletOwnerType.CUSTOMER, customerId)).isEqualByComparingTo("600000");
        assertThat(balanceOf(WalletOwnerType.CAREGIVER, caregiverUserId)).isEqualByComparingTo("0");
        assertThat(balanceOf(WalletOwnerType.CENTER, centerId)).isEqualByComparingTo("0");
        mockMvc.perform(get("/api/v1/refunds").header("Authorization", customer(customerId)))
                .andExpect(jsonPath("$.result.items.length()").value(2))
                .andExpect(jsonPath("$.result.items[0].channel").value("WALLET"))
                .andExpect(jsonPath("$.result.items[0].status").value("REFUNDED"));

        // Job cộng tiền buổi thấy CAREGIVER_MISSED → không cộng cho trung tâm / nền tảng.
        clock.set(BASE.plus(Duration.ofDays(2)));
        settleDue();
        assertThat(balanceOf(WalletOwnerType.CENTER, centerId)).isEqualByComparingTo("0");
    }

    // 2026-11-10: một lệnh hoàn (cùng idempotency key, nhiều buổi) → đúng một thông báo cho khách, ghi tổng tiền và nơi tiền về;
    // dispatch lại không gửi thêm.
    @Test
    void customerIsNotifiedOncePerCompletedRefundOrder() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID centerId = UUID.randomUUID();
        CancelSetup walletSetup = paidBooking(customerId, centerId, null, BASE.plus(Duration.ofDays(3)));
        createRefund("REFUND:CENTER_CANCEL:n1", walletSetup.bookingId(), walletSetup.occurrenceIds(), "CENTER_CANCEL",
                BASE);
        CancelSetup gatewaySetup = paidBooking(customerId, centerId, null, BASE.plus(Duration.ofDays(3)));
        createRefund("REFUND:EXPIRED:n2", gatewaySetup.bookingId(), gatewaySetup.occurrenceIds(), "EXPIRED", BASE);

        dispatchDueRefunds();
        dispatchDueRefunds();

        assertThat(SERVER.notificationBodies()).hasSize(2);
        JsonNode wallet = SERVER.notificationBodies().stream()
                .filter(body -> body.get("refId").asString().equals(walletSetup.bookingId().toString()))
                .findFirst().orElseThrow();
        assertThat(wallet.get("type").asString()).isEqualTo("REFUND_COMPLETED");
        assertThat(wallet.get("refType").asString()).isEqualTo("BOOKING");
        assertThat(wallet.get("recipientUserIds").get(0).asString()).isEqualTo(customerId.toString());
        // Hai buổi × 300.000 gộp trong một thông báo.
        assertThat(wallet.get("body").asString()).contains("600.000").contains("ví CareConnect");
        JsonNode gateway = SERVER.notificationBodies().stream()
                .filter(body -> body.get("refId").asString().equals(gatewaySetup.bookingId().toString()))
                .findFirst().orElseThrow();
        assertThat(gateway.get("body").asString()).contains("phương thức thanh toán");

        // Lệnh hoàn chưa xong hết (một dòng còn chờ) thì chưa báo.
        paymentProperties.getRefund().setMode(RefundMode.VNPAY);
        SERVER.setVnPayResponse("00", 503, 0);
        CancelSetup pendingSetup = paidBooking(customerId, centerId, null, BASE.plus(Duration.ofDays(3)));
        createRefund("REFUND:EXPIRED:n3", pendingSetup.bookingId(), pendingSetup.occurrenceIds(), "EXPIRED", BASE);
        dispatchDueRefunds();
        assertThat(SERVER.notificationBodies()).hasSize(2);
    }

    // 2026-11-10: hoàn khiếu nại không được trả hai lần — trung tâm trả lại phần đã nhận, phần còn lại trừ vào buổi chưa
    // đối soát (để lúc cộng buổi đó không tính lại), hết buổi thì nền tảng trả lại hoa hồng đã thu.
    @Test
    void complaintRefundIsNotPaidTwiceAcrossSettledAndUnsettledVisits() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID centerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        List<UUID> occurrenceIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<Map<String, Object>> occurrences = new ArrayList<>();
        for (int i = 0; i < occurrenceIds.size(); i++) {
            occurrences.add(PaymentTestServer.occurrence(occurrenceIds.get(i), i + 1,
                    BASE.plus(Duration.ofDays(1 + i)), "SCHEDULED", PRICE));
        }
        SERVER.putBooking(bookingId, customerId, centerId, "AWAITING_INITIAL_PAYMENT", BASE.plus(Duration.ofHours(24)),
                occurrences);
        SERVER.setPaymentTerms(bookingId, 100, null);
        payDeposit(new BookingSetup(bookingId, customerId, centerId, occurrenceIds, List.of()));
        BigDecimal platformBefore = balanceOf(WalletOwnerType.PLATFORM, PLATFORM_OWNER);

        // Buổi 1 xong và đã cộng (trung tâm 900.000, hoa hồng 100.000); buổi 2 chưa diễn ra.
        occurrences.get(0).put("status", "COMPLETED");
        clock.set(BASE.plus(Duration.ofDays(1)).plus(Duration.ofHours(3)));
        settleDue();
        assertThat(balanceOf(WalletOwnerType.CENTER, centerId)).isEqualByComparingTo("900000");

        // Hoàn khiếu nại 1.000.000: trung tâm trả lại 900.000, 100.000 còn lại ghi vào buổi 2 (chưa đối soát).
        complaint(bookingId, UUID.randomUUID(), "1000000").andExpect(status().isOk());
        assertThat(balanceOf(WalletOwnerType.CENTER, centerId)).isEqualByComparingTo("0");
        assertThat(occurrenceFinancialRepository.findById(occurrenceIds.get(1)).orElseThrow().getRefundedAmount())
                .isEqualByComparingTo("100000");
        assertThat(balanceOf(WalletOwnerType.PLATFORM, PLATFORM_OWNER).subtract(platformBefore))
                .isEqualByComparingTo("100000");

        // Buổi 2 xong: chỉ còn 900.000 của khách → trung tâm 810.000, hoa hồng 90.000. Tổng chi = đúng 2.000.000 đã thu.
        occurrences.get(1).put("status", "COMPLETED");
        clock.set(BASE.plus(Duration.ofDays(3)));
        settleDue();
        assertThat(balanceOf(WalletOwnerType.CENTER, centerId)).isEqualByComparingTo("810000");
        assertThat(balanceOf(WalletOwnerType.PLATFORM, PLATFORM_OWNER).subtract(platformBefore))
                .isEqualByComparingTo("190000");

        // Hoàn tiếp 1.000.000 khi mọi buổi đã đối soát: trung tâm trả 810.000, nền tảng trả lại 190.000 hoa hồng.
        complaint(bookingId, UUID.randomUUID(), "1000000").andExpect(status().isOk());
        assertThat(balanceOf(WalletOwnerType.CENTER, centerId)).isEqualByComparingTo("0");
        assertThat(balanceOf(WalletOwnerType.PLATFORM, PLATFORM_OWNER).subtract(platformBefore))
                .isEqualByComparingTo("0");
    }

    // 2026-11-10: buổi được phân người SAU khi khách trả khoản đầu — cọc bị giữ vì quá hạn phải vào ví caregiver đang được
    // giao (đọc lại từ Booking trước khi xử lý quá hạn), không vào ví trung tâm theo bản chụp cũ.
    @Test
    void overdueForfeitGoesToTheCaregiverAssignedAfterTheDeposit() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID centerId = UUID.randomUUID();
        UUID caregiverUserId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        List<UUID> occurrenceIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<Map<String, Object>> occurrences = new ArrayList<>();
        for (int i = 0; i < occurrenceIds.size(); i++) {
            occurrences.add(PaymentTestServer.occurrence(occurrenceIds.get(i), i + 1,
                    BASE.plus(Duration.ofDays(5 + i)), "SCHEDULED", PRICE));
        }
        SERVER.putBooking(bookingId, customerId, centerId, "AWAITING_INITIAL_PAYMENT", BASE.plus(Duration.ofHours(24)),
                occurrences);
        Instant dueAt = BASE.plus(Duration.ofDays(2));
        SERVER.setPaymentTerms(bookingId, 30, dueAt);
        payDeposit(new BookingSetup(bookingId, customerId, centerId, occurrenceIds, List.of()));
        assertThat(occurrenceFinancialRepository.findById(occurrenceIds.get(0)).orElseThrow().getCaregiverUserId())
                .isNull();

        // Sau khi trả cọc, hệ thống tự phân người cho buổi 1; buổi 2 vẫn chưa có người.
        occurrences.get(0).put("caregiverId", UUID.randomUUID().toString());
        occurrences.get(0).put("caregiverUserId", caregiverUserId.toString());
        clock.set(dueAt);
        UUID invoiceId = remainingInvoiceRepository.findByBookingId(bookingId).orElseThrow().getId();
        remainingInvoiceService.refreshCaregivers(invoiceId);
        remainingInvoiceService.markOverdue(invoiceId);

        // Giữ 100 % cọc (300.000 mỗi buổi): buổi 1 → caregiver, buổi 2 → trung tâm.
        assertThat(balanceOf(WalletOwnerType.CAREGIVER, caregiverUserId)).isEqualByComparingTo("300000");
        assertThat(balanceOf(WalletOwnerType.CENTER, centerId)).isEqualByComparingTo("300000");

        // Booking lỗi lúc đọc lại → vẫn xử lý được quá hạn bằng bản chụp cũ (không ném lỗi).
        remainingInvoiceService.refreshCaregivers(UUID.randomUUID());
    }

    @Test
    void withdrawalHoldsBalanceAndAdminRejectsOrCompletesIt() throws Exception {
        UUID customerId = UUID.randomUUID();
        CancelSetup setup = paidBooking(customerId, UUID.randomUUID(), null, BASE.plus(Duration.ofDays(3)));
        // Còn ≥ 24h → hoàn đủ 300.000 mỗi buổi vào ví.
        createRefund("REFUND:CUSTOMER_CANCEL:w3", setup.bookingId(), setup.occurrenceIds(), "CUSTOMER_CANCEL", BASE);
        dispatchDueRefunds();
        assertThat(balanceOf(WalletOwnerType.CUSTOMER, customerId)).isEqualByComparingTo("600000");
        String token = customer(customerId);

        withdraw(token, "700000", "0123456789").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(6041));
        withdraw(token, "100000", "12ab").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(6048));
        withdraw(token, "100000.5", "0123456789").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(6045));

        JsonNode first = body(withdraw(token, "400000", "0123456789").andExpect(status().isOk())).get("result");
        assertThat(first.get("status").asString()).isEqualTo("PENDING");
        Wallet wallet = walletRepository.findByOwnerTypeAndOwnerId(WalletOwnerType.CUSTOMER, customerId).orElseThrow();
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("200000");
        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("400000");
        // Phần đang chờ duyệt không rút lại được.
        withdraw(token, "300000", "0123456789").andExpect(status().isConflict());

        String firstId = first.get("id").asString();
        mockMvc.perform(post("/api/v1/wallet-withdrawals/{id}/approve", firstId).header("Authorization", token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/wallet-withdrawals/{id}/reject", firstId).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(6050));
        mockMvc.perform(post("/api/v1/wallet-withdrawals/{id}/reject", firstId).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sai tên chủ tài khoản\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("REJECTED"))
                .andExpect(jsonPath("$.result.rejectReason").value("Sai tên chủ tài khoản"));
        wallet = walletRepository.findByOwnerTypeAndOwnerId(WalletOwnerType.CUSTOMER, customerId).orElseThrow();
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("600000");
        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("0");

        String secondId = body(withdraw(token, "250000", "0123456789").andExpect(status().isOk()))
                .get("result").get("id").asString();
        // Chưa duyệt thì chưa xác nhận chuyển khoản được.
        complete(secondId, "FT123").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(6043));
        mockMvc.perform(post("/api/v1/wallet-withdrawals/{id}/approve", secondId).header("Authorization", admin()))
                .andExpect(jsonPath("$.result.status").value("APPROVED"));
        complete(secondId, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(6051));
        complete(secondId, "FT123").andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.transferReference").value("FT123"));
        complete(secondId, "FT123").andExpect(status().isConflict());
        wallet = walletRepository.findByOwnerTypeAndOwnerId(WalletOwnerType.CUSTOMER, customerId).orElseThrow();
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("350000");
        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("0");
        assertThat(ledgerOf(wallet.getId())).extracting(WalletTransaction::getType)
                .containsExactlyInAnyOrder(WalletTransactionType.REFUND_CREDIT, WalletTransactionType.REFUND_CREDIT,
                        WalletTransactionType.WITHDRAWAL_HOLD, WalletTransactionType.WITHDRAWAL_RELEASE,
                        WalletTransactionType.WITHDRAWAL_HOLD, WalletTransactionType.WITHDRAWAL_PAID);

        // Phạm vi: chủ ví thấy yêu cầu của mình, người khác 404, admin thấy tất cả và lọc được.
        mockMvc.perform(get("/api/v1/wallet-withdrawals").header("Authorization", token))
                .andExpect(jsonPath("$.result.items.length()").value(2));
        mockMvc.perform(get("/api/v1/wallet-withdrawals/{id}", secondId)
                        .header("Authorization", customer(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(6042));
        mockMvc.perform(get("/api/v1/wallet-withdrawals").header("Authorization", admin())
                        .param("status", "COMPLETED").param("ownerType", "CUSTOMER"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/wallets/{id}/transactions", wallet.getId()).header("Authorization", admin())
                        .param("from", FROM).param("to", TO))
                .andExpect(jsonPath("$.result.items.length()").value(6));
        mockMvc.perform(get("/api/v1/wallets/{id}/transactions", wallet.getId()).header("Authorization", token)
                        .param("from", FROM).param("to", TO))
                .andExpect(status().isForbidden());
        // Ví nền tảng không rút; admin không có endpoint tạo yêu cầu.
        withdraw(admin(), "1000", "0123456789").andExpect(status().isForbidden());
    }

    @Test
    void completedVisitsAreSettledToCenterAndPlatformOnceAndComplaintDebitsTheCenter() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID centerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        List<UUID> occurrenceIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        List<Map<String, Object>> occurrences = new ArrayList<>();
        for (int i = 0; i < occurrenceIds.size(); i++) {
            occurrences.add(PaymentTestServer.occurrence(occurrenceIds.get(i), i + 1,
                    BASE.plus(Duration.ofDays(1 + i)), "SCHEDULED", PRICE));
        }
        SERVER.putBooking(bookingId, customerId, centerId, "AWAITING_INITIAL_PAYMENT", BASE.plus(Duration.ofHours(24)),
                occurrences);
        SERVER.setPaymentTerms(bookingId, 100, null);
        payDeposit(new BookingSetup(bookingId, customerId, centerId, occurrenceIds, List.of()));

        // Buổi 1 xong, buổi 2 bị hủy phía Booking (không cộng), buổi 3 đang diễn ra (chưa chốt).
        occurrences.get(0).put("status", "COMPLETED");
        occurrences.get(1).put("status", "CANCELLED");
        occurrences.get(2).put("status", "IN_PROGRESS");
        clock.set(BASE.plus(Duration.ofDays(4)));
        settleDue();
        settleDue();

        assertThat(balanceOf(WalletOwnerType.CENTER, centerId)).isEqualByComparingTo("900000");
        assertThat(balanceOf(WalletOwnerType.PLATFORM, PLATFORM_OWNER)).isGreaterThanOrEqualTo(new BigDecimal("100000"));
        OccurrenceFinancial completed = occurrenceFinancialRepository.findById(occurrenceIds.get(0)).orElseThrow();
        assertThat(completed.getFinalStatus()).hasToString("COMPLETED");
        assertThat(completed.getSettledAt()).isNotNull();
        assertThat(occurrenceFinancialRepository.findById(occurrenceIds.get(1)).orElseThrow().getSettledAt()).isNotNull();
        assertThat(occurrenceFinancialRepository.findById(occurrenceIds.get(2)).orElseThrow().getSettledAt()).isNull();

        // Buổi 3 xong ở lượt quét sau.
        occurrences.get(2).put("status", "CUSTOMER_NO_SHOW");
        settleDue();
        assertThat(balanceOf(WalletOwnerType.CENTER, centerId)).isEqualByComparingTo("1800000");

        // Khiếu nại 2.500.000 > phần trung tâm đã nhận 1.800.000 → trừ tối đa phần đã nhận, ví trung tâm về 0; gửi lại không trừ.
        UUID complaintId = UUID.randomUUID();
        for (int attempt = 0; attempt < 2; attempt++) {
            complaint(bookingId, complaintId, "2500000").andExpect(status().isOk());
        }
        assertThat(balanceOf(WalletOwnerType.CENTER, centerId)).isEqualByComparingTo("0");

        mockMvc.perform(get("/api/v1/wallets/me/statistics").header("Authorization", manager(centerId))
                        .param("from", FROM).param("to", TO).param("groupBy", "MONTH"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/wallets/me/statistics").header("Authorization", manager(centerId))
                        .param("from", BASE.minus(Duration.ofDays(1)).toString())
                        .param("to", BASE.plus(Duration.ofDays(10)).toString()))
                .andExpect(jsonPath("$.result.ownerType").value("CENTER"))
                .andExpect(jsonPath("$.result.serviceIncome").value(1800000))
                .andExpect(jsonPath("$.result.commissionDeducted").value(200000))
                .andExpect(jsonPath("$.result.complaintDebits").value(1800000))
                .andExpect(jsonPath("$.result.totalIncome").value(0));
        mockMvc.perform(get("/api/v1/wallets/me").header("Authorization", manager(centerId)))
                .andExpect(jsonPath("$.result.withdrawable").value(false));
        mockMvc.perform(get("/api/v1/wallets/me/statistics").header("Authorization", admin())
                        .param("from", BASE.minus(Duration.ofDays(1)).toString())
                        .param("to", BASE.plus(Duration.ofDays(10)).toString()))
                .andExpect(jsonPath("$.result.ownerType").value("PLATFORM"))
                .andExpect(jsonPath("$.result.liabilities").isArray())
                .andExpect(jsonPath("$.result.pendingWithdrawalCount").isNumber());
    }

    // ===== helpers =====

    private CancelSetup paidBooking(UUID customerId, UUID centerId, UUID caregiverUserId, Instant firstStart)
            throws Exception {
        UUID bookingId = UUID.randomUUID();
        List<UUID> occurrenceIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<Map<String, Object>> occurrences = new ArrayList<>();
        for (int i = 0; i < occurrenceIds.size(); i++) {
            Map<String, Object> occurrence = PaymentTestServer.occurrence(occurrenceIds.get(i), i + 1, firstStart,
                    "SCHEDULED", PRICE);
            if (i == 0 && caregiverUserId != null) {
                occurrence.put("caregiverId", UUID.randomUUID().toString());
                occurrence.put("caregiverUserId", caregiverUserId.toString());
            }
            occurrences.add(occurrence);
        }
        SERVER.putBooking(bookingId, customerId, centerId, "AWAITING_INITIAL_PAYMENT", BASE.plus(Duration.ofHours(24)),
                occurrences);
        payDeposit(new BookingSetup(bookingId, customerId, centerId, occurrenceIds, List.of()));
        return new CancelSetup(bookingId, occurrenceIds);
    }

    private void createRefund(String key, UUID bookingId, List<UUID> occurrenceIds, String reason, Instant cancelledAt)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bookingId", bookingId.toString());
        body.put("occurrenceIds", occurrenceIds.stream().map(UUID::toString).toList());
        body.put("reason", reason);
        body.put("cancelledBy", reason.startsWith("CENTER") ? "CENTER" : "CUSTOMER");
        body.put("cancelledAt", cancelledAt.toString());
        body.put("firstUpcomingStartAt", null);
        body.put("requestedBy", null);
        body.put("idempotencyKey", key);
        mockMvc.perform(post("/api/v1/refunds").header("Authorization", service())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private ResultActions withdraw(String token, String amount, String accountNumber) throws Exception {
        String body = "{\"amount\":" + amount + ",\"bankName\":\"Vietcombank\",\"bankAccountNumber\":\"" + accountNumber
                + "\",\"accountHolderName\":\"NGUYEN VAN A\"}";
        return mockMvc.perform(post("/api/v1/wallet-withdrawals").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions complete(String withdrawalId, String reference) throws Exception {
        return mockMvc.perform(post("/api/v1/wallet-withdrawals/{id}/complete", withdrawalId)
                .header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"transferReference\":\"" + reference + "\"}"));
    }

    private ResultActions complaint(UUID bookingId, UUID complaintId, String amount) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("bookingId", bookingId.toString(),
                "complaintId", complaintId.toString(), "amount", new BigDecimal(amount),
                "decidedBy", UUID.randomUUID().toString(), "note", "Khiếu nại hợp lệ"));
        return mockMvc.perform(post("/api/v1/refunds/complaints").header("Authorization", service())
                .header("Idempotency-Key", "COMPLAINT_REFUND:" + complaintId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void dispatchDueRefunds() {
        for (UUID refundId : refundDispatchService.findDueIds()) {
            refundDispatchService.process(refundId);
        }
    }

    private void settleDue() {
        for (UUID bookingId : walletSettlementService.findDueBookingIds()) {
            walletSettlementService.process(bookingId);
        }
    }

    private BigDecimal balanceOf(WalletOwnerType ownerType, UUID ownerId) {
        return walletRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .map(Wallet::getAvailableBalance)
                .orElse(BigDecimal.ZERO);
    }

    private List<WalletTransaction> ledgerOf(UUID walletId) {
        return walletTransactionRepository.findAll().stream()
                .filter(transaction -> transaction.getWalletId().equals(walletId))
                .toList();
    }

    private record CancelSetup(UUID bookingId, List<UUID> occurrenceIds) {
    }
}
