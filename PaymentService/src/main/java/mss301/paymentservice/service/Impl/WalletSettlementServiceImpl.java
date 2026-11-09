package mss301.paymentservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.client.BookingClient;
import mss301.paymentservice.client.dto.BookingPaymentContextResponse;
import mss301.paymentservice.client.dto.RemoteApiResponse;
import mss301.paymentservice.config.PaymentProperties;
import mss301.paymentservice.dto.response.PolicySnapshot;
import mss301.paymentservice.entity.BookingFinancial;
import mss301.paymentservice.entity.OccurrenceFinancial;
import mss301.paymentservice.enums.BookingFinancialStatus;
import mss301.paymentservice.enums.OccurrenceFinalStatus;
import mss301.paymentservice.enums.OccurrenceStatus;
import mss301.paymentservice.repository.BookingFinancialRepository;
import mss301.paymentservice.repository.OccurrenceFinancialRepository;
import mss301.paymentservice.service.WalletService;
import mss301.paymentservice.service.WalletSettlementService;
import mss301.paymentservice.util.VndMoney;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cộng tiền buổi đã phục vụ: gọi payment-context NGOÀI transaction, rồi transaction ngắn (khóa booking_financials) chốt
 * từng buổi. COMPLETED / CUSTOMER_NO_SHOW → tiền khách đã trả cho buổi (cọc + thu thêm − đã hoàn) trừ hoa hồng snapshot vào
 * ví trung tâm, hoa hồng vào ví nền tảng. CANCELLED / EXPIRED / CAREGIVER_MISSED → chỉ đánh settled_at (tiền do luồng hoàn
 * tiền xử lý, kể cả khi lệnh hoàn của Booking tới sau).
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class WalletSettlementServiceImpl implements WalletSettlementService {
    static Set<OccurrenceStatus> NOT_BILLABLE = EnumSet.of(OccurrenceStatus.CANCELLED, OccurrenceStatus.EXPIRED,
            OccurrenceStatus.CAREGIVER_MISSED);
    static BigDecimal FULL_PAYMENT_PERCENT = BigDecimal.valueOf(100);

    BookingClient bookingClient;
    BookingFinancialRepository bookingFinancialRepository;
    OccurrenceFinancialRepository occurrenceFinancialRepository;
    WalletService walletService;
    PaymentProperties paymentProperties;
    TransactionTemplate transactionTemplate;
    Clock clock;

    @Override
    public List<UUID> findDueBookingIds() {
        PaymentProperties.Wallet walletProperties = paymentProperties.getWallet();
        Instant cutoff = clock.instant().minus(walletProperties.getSettlementGrace());
        return occurrenceFinancialRepository.findSettlementCandidateBookingIds(BookingFinancialStatus.AWAITING_DEPOSIT,
                cutoff, PageRequest.of(0, walletProperties.getSettlementBatchSize()));
    }

    @Override
    public void process(UUID bookingId) {
        BookingPaymentContextResponse context;
        try {
            RemoteApiResponse<BookingPaymentContextResponse> response = bookingClient.getPaymentContext(bookingId);
            context = response == null ? null : response.getResult();
        } catch (RuntimeException e) {
            // Booking tạm lỗi: lượt quét sau thử lại, không làm hỏng các booking khác.
            log.warn("Settlement of booking {} postponed: payment-context unavailable ({})", bookingId,
                    e.getClass().getSimpleName());
            return;
        }
        if (context == null || context.getOccurrences() == null) {
            return;
        }
        Map<UUID, OccurrenceStatus> statuses = new HashMap<>();
        for (BookingPaymentContextResponse.Occurrence occurrence : context.getOccurrences()) {
            if (occurrence.getOccurrenceId() != null && occurrence.getStatus() != null) {
                statuses.put(occurrence.getOccurrenceId(), occurrence.getStatus());
            }
        }
        transactionTemplate.executeWithoutResult(status -> settle(bookingId, statuses));
    }

    private void settle(UUID bookingId, Map<UUID, OccurrenceStatus> statuses) {
        BookingFinancial financial = bookingFinancialRepository.findForUpdate(bookingId).orElse(null);
        if (financial == null || financial.getStatus() == BookingFinancialStatus.AWAITING_DEPOSIT) {
            return;
        }
        Instant now = clock.instant();
        boolean legacy = isLegacyBooking(financial.getPolicySnapshot(), financial);
        for (OccurrenceFinancial occurrenceFinancial : occurrenceFinancialRepository.findAllByBookingId(bookingId)) {
            OccurrenceStatus status = statuses.get(occurrenceFinancial.getOccurrenceId());
            if (status == null || occurrenceFinancial.getFinalStatus() != null
                    || occurrenceFinancial.getSettledAt() != null) {
                continue;
            }
            if (NOT_BILLABLE.contains(status)) {
                occurrenceFinancial.setSettledAt(now);
                continue;
            }
            if (status != OccurrenceStatus.COMPLETED && status != OccurrenceStatus.CUSTOMER_NO_SHOW) {
                continue;
            }
            if (legacy) {
                // Booking trước spec v1 thu phần còn lại sau hoàn tất (hóa đơn REMAINING) — không cộng ví tự động.
                occurrenceFinancial.setSettledAt(now);
                log.warn("Occurrence {} of legacy booking {} not settled to wallets", occurrenceFinancial.getOccurrenceId(),
                        bookingId);
                continue;
            }
            settleOccurrence(financial, occurrenceFinancial, status, now);
        }
    }

    private void settleOccurrence(BookingFinancial financial, OccurrenceFinancial occurrenceFinancial,
                                  OccurrenceStatus status, Instant now) {
        BigDecimal net = VndMoney.nonNegative(occurrenceFinancial.getDepositAmount()
                .add(occurrenceFinancial.getChargedAmount())
                .subtract(occurrenceFinancial.getRefundedAmount()));
        BigDecimal percent = financial.getPolicySnapshot().getCommissionPercent() == null
                ? BigDecimal.ZERO
                : financial.getPolicySnapshot().getCommissionPercent().min(VndMoney.HUNDRED);
        BigDecimal commission = VndMoney.percentOf(percent, net).min(net);
        occurrenceFinancial.setFinalStatus(status == OccurrenceStatus.COMPLETED
                ? OccurrenceFinalStatus.COMPLETED
                : OccurrenceFinalStatus.CUSTOMER_NO_SHOW);
        occurrenceFinancial.setSettledAt(now);
        walletService.settleOccurrence(occurrenceFinancial, net.subtract(commission), commission);
        log.info("Occurrence {} of booking {} settled: center {}, commission {}", occurrenceFinancial.getOccurrenceId(),
                financial.getBookingId(), net.subtract(commission), commission);
    }

    // Không có hạn trả phần còn lại mà khoản đầu < 100 % → booking cũ (thu sau hoàn tất), không phải trả đủ trước.
    private boolean isLegacyBooking(PolicySnapshot policy, BookingFinancial financial) {
        return financial.getRemainingDueAt() == null
                && (policy.getDepositPercent() == null || policy.getDepositPercent().compareTo(FULL_PAYMENT_PERCENT) < 0);
    }
}
