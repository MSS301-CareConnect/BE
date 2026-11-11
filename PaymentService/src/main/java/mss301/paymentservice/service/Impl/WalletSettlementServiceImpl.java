package mss301.paymentservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.client.BookingClient;
import mss301.paymentservice.client.dto.BookingPaymentContextResponse;
import mss301.paymentservice.client.dto.RemoteApiResponse;
import mss301.paymentservice.config.PaymentProperties;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.BookingSettlementResponse;
import mss301.paymentservice.dto.response.PolicySnapshot;
import mss301.paymentservice.entity.BookingFinancial;
import mss301.paymentservice.entity.OccurrenceFinancial;
import mss301.paymentservice.enums.BookingFinancialStatus;
import mss301.paymentservice.enums.OccurrenceFinalStatus;
import mss301.paymentservice.enums.OccurrenceStatus;
import mss301.paymentservice.enums.RoleCode;
import mss301.paymentservice.enums.WalletOwnerType;
import mss301.paymentservice.enums.WalletTransactionType;
import mss301.paymentservice.exception.AppException;
import mss301.paymentservice.exception.ErrorCode;
import mss301.paymentservice.repository.BookingFinancialRepository;
import mss301.paymentservice.repository.OccurrenceFinancialRepository;
import mss301.paymentservice.repository.projection.WalletBookingTotalView;
import mss301.paymentservice.repository.WalletTransactionRepository;
import mss301.paymentservice.service.WalletService;
import mss301.paymentservice.service.WalletSettlementService;
import mss301.paymentservice.util.VndMoney;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    WalletTransactionRepository walletTransactionRepository;
    WalletService walletService;
    PaymentProperties paymentProperties;
    TransactionTemplate transactionTemplate;
    Clock clock;

    // Lấy id booking có buổi đã xong quá thời gian chờ để quyết toán.
    @Override
    public List<UUID> findDueBookingIds() {
        PaymentProperties.Wallet walletProperties = paymentProperties.getWallet();
        Instant cutoff = clock.instant().minus(walletProperties.getSettlementGrace());
        return occurrenceFinancialRepository.findSettlementCandidateBookingIds(BookingFinancialStatus.AWAITING_DEPOSIT,
                cutoff, PageRequest.of(0, walletProperties.getSettlementBatchSize()));
    }

    // Quyết toán một booking: lấy trạng thái buổi từ BookingService rồi ghi tiền vào ví.
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
            // Buổi đang bị giữ quyết toán (ca tự đóng chờ trung tâm xác nhận) bỏ qua lượt này, lượt quét sau xét lại.
            if (occurrence.getOccurrenceId() != null && occurrence.getStatus() != null
                    && !Boolean.TRUE.equals(occurrence.getSettlementHeld())) {
                statuses.put(occurrence.getOccurrenceId(), occurrence.getStatus());
            }
        }
        transactionTemplate.executeWithoutResult(status -> settle(bookingId, statuses));
    }

    // Bảng quyết toán của booking: đã thu, đã hoàn, phần trung tâm, hoa hồng.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<BookingSettlementResponse> getBookingSettlement(Set<RoleCode> roles, UUID claimCenterId,
                                                                       UUID bookingId) {
        BookingFinancial financial = bookingFinancialRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
        if (!roles.contains(RoleCode.ADMIN)) {
            if (claimCenterId == null) {
                throw new AppException(ErrorCode.CENTER_SCOPE_REQUIRED);
            }
            if (!financial.getCenterId().equals(claimCenterId)) {
                throw new AppException(ErrorCode.BOOKING_NOT_FOUND);
            }
        }
        Map<UUID, BookingSettlementResponse.Occurrence> lines = new HashMap<>();
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        int pendingCount = 0;
        for (OccurrenceFinancial occurrenceFinancial : occurrenceFinancialRepository.findAllByBookingId(bookingId)) {
            BigDecimal occurrencePaid = occurrenceFinancial.getDepositAmount().add(occurrenceFinancial.getChargedAmount());
            paid = paid.add(occurrencePaid);
            refunded = refunded.add(occurrenceFinancial.getRefundedAmount());
            if (occurrenceFinancial.getFinalStatus() == null && occurrenceFinancial.getSettledAt() == null) {
                pendingCount++;
            }
            lines.put(occurrenceFinancial.getOccurrenceId(), BookingSettlementResponse.Occurrence.builder()
                    .occurrenceId(occurrenceFinancial.getOccurrenceId())
                    .startAt(occurrenceFinancial.getStartAt())
                    .priceAmount(occurrenceFinancial.getPriceAmount())
                    .paidAmount(occurrencePaid)
                    .refundedAmount(occurrenceFinancial.getRefundedAmount())
                    .keptDepositAmount(BigDecimal.ZERO)
                    .centerAmount(BigDecimal.ZERO)
                    .commissionAmount(BigDecimal.ZERO)
                    .finalStatus(occurrenceFinancial.getFinalStatus())
                    .settledAt(occurrenceFinancial.getSettledAt())
                    .build());
        }
        BigDecimal centerService = BigDecimal.ZERO;
        BigDecimal centerKept = BigDecimal.ZERO;
        BigDecimal caregiverKept = BigDecimal.ZERO;
        BigDecimal complaintDebit = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;
        for (WalletBookingTotalView row : walletTransactionRepository.sumByBookingForSettlement(bookingId)) {
            BookingSettlementResponse.Occurrence line = row.getOccurrenceId() == null
                    ? null
                    : lines.get(row.getOccurrenceId());
            BigDecimal amount = row.getTotalAmount();
            WalletOwnerType owner = row.getOwnerType();
            WalletTransactionType type = row.getType();
            if (owner == WalletOwnerType.CENTER && type == WalletTransactionType.SERVICE_SETTLEMENT) {
                centerService = centerService.add(amount);
                if (line != null) {
                    line.setCenterAmount(line.getCenterAmount().add(amount));
                }
            } else if (owner == WalletOwnerType.PLATFORM && type == WalletTransactionType.COMMISSION_CREDIT) {
                commission = commission.add(amount);
                if (line != null) {
                    line.setCommissionAmount(line.getCommissionAmount().add(amount));
                }
            } else if (owner == WalletOwnerType.PLATFORM && type == WalletTransactionType.COMPLAINT_DEBIT) {
                // Hoàn khiếu nại vượt phần trung tâm đã nhận → nền tảng trả lại hoa hồng.
                commission = commission.subtract(amount);
            } else if (owner == WalletOwnerType.CENTER && type == WalletTransactionType.COMPLAINT_DEBIT) {
                complaintDebit = complaintDebit.add(amount);
            } else if (type == WalletTransactionType.FORFEIT_CREDIT) {
                if (owner == WalletOwnerType.CENTER) {
                    centerKept = centerKept.add(amount);
                } else {
                    caregiverKept = caregiverKept.add(amount);
                }
                if (line != null) {
                    line.setKeptDepositAmount(line.getKeptDepositAmount().add(amount));
                }
            }
        }
        List<BookingSettlementResponse.Occurrence> occurrences = new ArrayList<>(lines.values());
        occurrences.sort(Comparator.comparing(BookingSettlementResponse.Occurrence::getStartAt));
        BookingSettlementResponse settlementResponse = BookingSettlementResponse.builder()
                .bookingId(bookingId)
                .centerId(financial.getCenterId())
                .commissionPercent(financial.getPolicySnapshot().getCommissionPercent())
                .paidAmount(paid)
                .refundedAmount(refunded)
                .centerServiceAmount(centerService)
                .centerKeptDepositAmount(centerKept)
                .caregiverKeptDepositAmount(caregiverKept)
                .complaintDebitAmount(complaintDebit)
                .centerNetAmount(centerService.add(centerKept).subtract(complaintDebit))
                .commissionAmount(commission)
                .settledCount(occurrences.size() - pendingCount)
                .pendingCount(pendingCount)
                .occurrences(occurrences)
                .build();
        return APIResponse.success("Lấy quyết toán booking thành công", settlementResponse);
    }

    // Quyết toán các buổi đã kết thúc của booking.
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

    // Tính tiền thực nhận của một buổi, trừ hoa hồng rồi ghi vào ví.
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
