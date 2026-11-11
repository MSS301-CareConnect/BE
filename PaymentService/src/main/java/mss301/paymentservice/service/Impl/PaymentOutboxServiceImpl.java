package mss301.paymentservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.client.BookingClient;
import mss301.paymentservice.client.dto.BookingNotificationRequest;
import mss301.paymentservice.client.dto.DepositPaidRequest;
import mss301.paymentservice.config.PaymentProperties;
import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.entity.PaymentOutbox;
import mss301.paymentservice.entity.Refund;
import mss301.paymentservice.entity.WalletWithdrawal;
import mss301.paymentservice.enums.RefundChannel;
import mss301.paymentservice.enums.RefundStatus;
import mss301.paymentservice.enums.OutboxEventType;
import mss301.paymentservice.enums.OutboxStatus;
import mss301.paymentservice.enums.RefundReason;
import mss301.paymentservice.enums.TransactionEventType;
import mss301.paymentservice.exception.AppException;
import mss301.paymentservice.exception.ErrorCode;
import mss301.paymentservice.mapper.PaymentOutboxMapper;
import mss301.paymentservice.repository.BookingFinancialRepository;
import mss301.paymentservice.repository.PaymentOutboxRepository;
import mss301.paymentservice.repository.PaymentRepository;
import mss301.paymentservice.repository.RefundRepository;
import mss301.paymentservice.repository.WalletWithdrawalRepository;
import mss301.paymentservice.service.PaymentOutboxService;
import mss301.paymentservice.service.PaymentTransactionService;
import mss301.paymentservice.service.RefundService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Báo Booking theo loại sự kiện (Booking idempotent nên gửi lại an toàn): xác nhận khoản đầu ({@code /deposit-paid}), đã
 * trả xong phần còn lại ({@code /remaining-paid}), quá hạn trả phần còn lại ({@code /payment-overdue}). Booking từ chối
 * (4xx — hết hạn / đã hủy / không tồn tại) → FAILED; khoản vừa thu được hoàn toàn bộ {@code BOOKING_NOT_CONFIRMABLE}
 * (quá hạn thì không có khoản mới). Lỗi tạm thời → backoff tới maxAttempts rồi FAILED để admin xử lý.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PaymentOutboxServiceImpl implements PaymentOutboxService {
    static int MAX_ERROR_LENGTH = 500;
    static int MAX_BACKOFF_SHIFT = 20;
    static String OVERDUE_REFERENCE_PREFIX = "invoice=";
    static String REFUND_REFERENCE_PREFIX = "refund=";
    static String REFUND_NOTIFICATION_TYPE = "REFUND_COMPLETED";
    static String REFUND_NOTIFICATION_TITLE = "Hoàn tiền thành công";
    static String BOOKING_REF_TYPE = "BOOKING";
    static String WITHDRAWAL_REFERENCE_PREFIX = "withdrawal=";
    static String WITHDRAWAL_REF_TYPE = "WALLET_WITHDRAWAL";
    static String WITHDRAWAL_COMPLETED_NOTIFICATION_TYPE = "WALLET_WITHDRAWAL_COMPLETED";
    static String WITHDRAWAL_REJECTED_NOTIFICATION_TYPE = "WALLET_WITHDRAWAL_REJECTED";
    static String WITHDRAWAL_COMPLETED_TITLE = "Rút tiền thành công";
    static String WITHDRAWAL_REJECTED_TITLE = "Yêu cầu rút tiền bị từ chối";
    static int MASKED_ACCOUNT_DIGITS = 4;
    static Set<OutboxEventType> NOTIFICATION_EVENTS = EnumSet.of(OutboxEventType.REFUND_COMPLETED,
            OutboxEventType.WITHDRAWAL_COMPLETED, OutboxEventType.WITHDRAWAL_REJECTED);
    static Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");

    PaymentOutboxRepository paymentOutboxRepository;
    PaymentRepository paymentRepository;
    RefundRepository refundRepository;
    BookingFinancialRepository bookingFinancialRepository;
    WalletWithdrawalRepository walletWithdrawalRepository;
    BookingClient bookingClient;
    RefundService refundService;
    PaymentTransactionService paymentTransactionService;
    PaymentOutboxMapper paymentOutboxMapper;
    PaymentProperties paymentProperties;
    TransactionTemplate transactionTemplate;
    Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueDepositConfirmation(Payment payment, String paymentReference) {
        enqueue(OutboxEventType.DEPOSIT_CONFIRMATION, payment, paymentReference);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueRemainingConfirmation(Payment payment, String paymentReference) {
        enqueue(OutboxEventType.REMAINING_CONFIRMATION, payment, paymentReference);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueuePaymentOverdue(Payment depositPayment, UUID invoiceId) {
        enqueue(OutboxEventType.PAYMENT_OVERDUE, depositPayment, OVERDUE_REFERENCE_PREFIX + invoiceId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueRefundCompleted(Refund refund) {
        List<Refund> group = refundRepository.findAllByIdempotencyKey(refund.getIdempotencyKey());
        boolean allDone = group.stream().allMatch(item -> item.getStatus() == RefundStatus.REFUNDED);
        if (!allDone) {
            return;
        }
        // Khóa trùng cố định theo dòng hoàn nhỏ nhất của nhóm — ai hoàn tất dòng cuối cũng ra cùng một khóa.
        Refund anchor = group.stream().min(Comparator.comparing(Refund::getId)).orElse(refund);
        String reference = REFUND_REFERENCE_PREFIX + anchor.getId();
        if (paymentOutboxRepository.existsByEventTypeAndPaymentIdAndPaymentReference(OutboxEventType.REFUND_COMPLETED,
                anchor.getPaymentId(), reference)) {
            return;
        }
        save(OutboxEventType.REFUND_COMPLETED, anchor.getBookingId(), anchor.getPaymentId(), reference);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueWithdrawalResult(WalletWithdrawal withdrawal) {
        OutboxEventType eventType = switch (withdrawal.getStatus()) {
            case COMPLETED -> OutboxEventType.WITHDRAWAL_COMPLETED;
            case REJECTED -> OutboxEventType.WITHDRAWAL_REJECTED;
            default -> null;
        };
        String reference = WITHDRAWAL_REFERENCE_PREFIX + withdrawal.getId();
        if (eventType == null || paymentOutboxRepository.existsByEventTypeAndPaymentReference(eventType, reference)) {
            return;
        }
        save(eventType, null, null, reference);
    }

    private void enqueue(OutboxEventType eventType, Payment payment, String paymentReference) {
        if (paymentOutboxRepository.existsByEventTypeAndPaymentId(eventType, payment.getId())) {
            return;
        }
        save(eventType, payment.getBookingId(), payment.getId(), paymentReference);
    }

    private void save(OutboxEventType eventType, UUID bookingId, UUID paymentId, String paymentReference) {
        PaymentOutbox outbox = paymentOutboxRepository.save(paymentOutboxMapper.toOutbox(eventType, bookingId,
                paymentId, paymentReference, OutboxStatus.PENDING, clock.instant()));
        UUID outboxId = outbox.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // Tiền đã ghi nhận: lỗi gửi chỉ để lại dòng PENDING cho scheduler, không làm hỏng response IPN.
                try {
                    process(outboxId);
                } catch (RuntimeException e) {
                    log.warn("Immediate dispatch of outbox {} failed ({}), scheduler will retry", outboxId,
                            e.getClass().getSimpleName());
                }
            }
        });
    }

    @Override
    public List<UUID> findDueIds() {
        return paymentOutboxRepository.findDueIds(OutboxStatus.PENDING, clock.instant(),
                PageRequest.of(0, paymentProperties.getOutbox().getBatchSize()));
    }

    @Override
    public void process(UUID outboxId) {
        // REQUIRES_NEW: có thể chạy trong afterCommit của transaction IPN (đã commit nhưng vẫn gắn thread).
        TransactionTemplate newTransaction = new TransactionTemplate(transactionTemplate.getTransactionManager());
        newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        PaymentOutbox claimed = newTransaction.execute(status -> claim(outboxId));
        if (claimed == null) {
            return;
        }
        Outcome outcome;
        String error = null;
        try {
            send(claimed);
            outcome = Outcome.SENT;
        } catch (AppException e) {
            boolean rejected = e.getErrorCode() == ErrorCode.BOOKING_COMMAND_REJECTED
                    || e.getErrorCode() == ErrorCode.BOOKING_NOT_FOUND;
            outcome = rejected ? Outcome.REJECTED : Outcome.RETRY;
            error = e.getErrorCode().name();
        } catch (RuntimeException e) {
            outcome = Outcome.RETRY;
            error = e.getClass().getSimpleName();
        }
        Outcome finalOutcome = outcome;
        String finalError = error;
        newTransaction.executeWithoutResult(status -> recordResult(outboxId, finalOutcome, finalError));
    }

    private void send(PaymentOutbox outbox) {
        UUID bookingId = outbox.getBookingId();
        switch (outbox.getEventType()) {
            case DEPOSIT_CONFIRMATION -> bookingClient.markDepositPaid(bookingId, DepositPaidRequest.builder()
                    .paymentReference(outbox.getPaymentReference())
                    .fullyPaid(isFullyPaidByDeposit(bookingId))
                    .build());
            case REMAINING_CONFIRMATION -> bookingClient.markRemainingPaid(bookingId, DepositPaidRequest.builder()
                    .paymentReference(outbox.getPaymentReference())
                    .build());
            case PAYMENT_OVERDUE -> bookingClient.markPaymentOverdue(bookingId);
            case REFUND_COMPLETED -> sendRefundNotification(outbox);
            case WITHDRAWAL_COMPLETED, WITHDRAWAL_REJECTED -> sendWithdrawalNotification(outbox);
        }
    }

    // Dựng nội dung lúc gửi từ chính các dòng hoàn (tổng tiền, kênh) — outbox chỉ giữ khóa của nhóm.
    private void sendRefundNotification(PaymentOutbox outbox) {
        UUID anchorId = UUID.fromString(outbox.getPaymentReference().substring(REFUND_REFERENCE_PREFIX.length()));
        Refund anchor = refundRepository.findById(anchorId).orElse(null);
        if (anchor == null) {
            log.warn("Refund notification {}: refund {} no longer exists, skipped", outbox.getId(), anchorId);
            return;
        }
        BigDecimal total = refundRepository.findAllByIdempotencyKey(anchor.getIdempotencyKey()).stream()
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String amount = NumberFormat.getCurrencyInstance(VIETNAMESE).format(total);
        String body = anchor.getChannel() == RefundChannel.WALLET
                ? "Đã hoàn " + amount + " vào ví CareConnect của bạn. Bạn có thể dùng hoặc rút về tài khoản ngân hàng."
                : "Đã hoàn " + amount + " về phương thức thanh toán ban đầu. Tiền về tài khoản tùy thời gian xử lý của"
                        + " ngân hàng.";
        bookingClient.createNotification(BookingNotificationRequest.builder()
                .recipientUserIds(List.of(anchor.getCustomerId()))
                .type(REFUND_NOTIFICATION_TYPE)
                .title(REFUND_NOTIFICATION_TITLE)
                .body(body)
                .refType(BOOKING_REF_TYPE)
                .refId(anchor.getBookingId())
                .build());
    }

    // Nội dung dựng lúc gửi từ chính yêu cầu rút (số tiền, ngân hàng, mã giao dịch / lý do).
    private void sendWithdrawalNotification(PaymentOutbox outbox) {
        UUID withdrawalId = UUID.fromString(outbox.getPaymentReference()
                .substring(WITHDRAWAL_REFERENCE_PREFIX.length()));
        WalletWithdrawal withdrawal = walletWithdrawalRepository.findById(withdrawalId).orElse(null);
        if (withdrawal == null) {
            log.warn("Withdrawal notification {}: withdrawal {} no longer exists, skipped", outbox.getId(),
                    withdrawalId);
            return;
        }
        String amount = NumberFormat.getCurrencyInstance(VIETNAMESE).format(withdrawal.getAmount());
        String account = maskAccount(withdrawal.getBankAccountNumber());
        boolean completed = outbox.getEventType() == OutboxEventType.WITHDRAWAL_COMPLETED;
        String body = completed
                ? "Đã chuyển " + amount + " tới " + withdrawal.getBankName() + " " + account + ". Mã giao dịch: "
                        + withdrawal.getTransferReference() + "."
                : "Yêu cầu rút " + amount + " về " + withdrawal.getBankName() + " " + account + " bị từ chối: "
                        + withdrawal.getRejectReason() + ". Số tiền đã trở lại số dư ví của bạn.";
        bookingClient.createNotification(BookingNotificationRequest.builder()
                .recipientUserIds(List.of(withdrawal.getRequesterUserId()))
                .type(completed ? WITHDRAWAL_COMPLETED_NOTIFICATION_TYPE : WITHDRAWAL_REJECTED_NOTIFICATION_TYPE)
                .title(completed ? WITHDRAWAL_COMPLETED_TITLE : WITHDRAWAL_REJECTED_TITLE)
                .body(body)
                .refType(WITHDRAWAL_REF_TYPE)
                .refId(withdrawal.getId())
                .build());
    }

    private String maskAccount(String accountNumber) {
        if (accountNumber.length() <= MASKED_ACCOUNT_DIGITS) {
            return accountNumber;
        }
        return "••••" + accountNumber.substring(accountNumber.length() - MASKED_ACCOUNT_DIGITS);
    }

    // Khoản đầu phủ toàn bộ tiền booking (SHIFT 100 %) → Booking đánh dấu đã trả đủ ngay.
    private boolean isFullyPaidByDeposit(UUID bookingId) {
        return bookingFinancialRepository.findById(bookingId)
                .map(financial -> financial.getDepositAmount().compareTo(financial.getTotalAmount()) >= 0)
                .orElse(false);
    }

    private PaymentOutbox claim(UUID outboxId) {
        PaymentOutbox outbox = paymentOutboxRepository.findForUpdateSkipLocked(outboxId).orElse(null);
        Instant now = clock.instant();
        if (outbox == null || outbox.getStatus() != OutboxStatus.PENDING || outbox.getNextAttemptAt().isAfter(now)) {
            return null;
        }
        outbox.setNextAttemptAt(now.plus(paymentProperties.getOutbox().getClaimLease()));
        return outbox;
    }

    private void recordResult(UUID outboxId, Outcome outcome, String error) {
        PaymentOutbox outbox = paymentOutboxRepository.findForUpdate(outboxId).orElse(null);
        if (outbox == null || outbox.getStatus() != OutboxStatus.PENDING) {
            return;
        }
        Instant now = clock.instant();
        outbox.setAttempts(outbox.getAttempts() + 1);
        if (NOTIFICATION_EVENTS.contains(outbox.getEventType())) {
            recordNotificationResult(outbox, outcome, error, now);
            return;
        }
        switch (outcome) {
            case SENT -> {
                outbox.setStatus(OutboxStatus.SENT);
                outbox.setSentAt(now);
                outbox.setLastError(null);
                if (outbox.getEventType() != OutboxEventType.PAYMENT_OVERDUE) {
                    paymentRepository.findById(outbox.getPaymentId())
                            .ifPresent(payment -> payment.setBookingNotifiedAt(now));
                }
                paymentTransactionService.record(outbox.getBookingId(), outbox.getPaymentId(), null, null,
                        TransactionEventType.BOOKING_CONFIRMED, null, null, outbox.getEventType()
                                + " reference=" + outbox.getPaymentReference(), null);
                log.info("Booking {} accepted {} ({})", outbox.getBookingId(), outbox.getEventType(),
                        outbox.getPaymentId());
            }
            case REJECTED -> {
                outbox.setStatus(OutboxStatus.FAILED);
                outbox.setLastError(truncate("Booking rejected: " + error));
                paymentTransactionService.record(outbox.getBookingId(), outbox.getPaymentId(), null, null,
                        TransactionEventType.BOOKING_REJECTED, null, null, outbox.getEventType() + " " + error, null);
                if (outbox.getEventType() == OutboxEventType.PAYMENT_OVERDUE) {
                    // Booking đã hủy / đã chạy trước khi nhận báo quá hạn — tiền đã xử lý ở Payment, chỉ để admin xem.
                    log.error("Booking {} rejected payment overdue ({})", outbox.getBookingId(), error);
                    return;
                }
                // Tiền đã thu nhưng booking không còn nhận khoản này (hết hạn / hủy) — hoàn toàn bộ.
                refundService.refundWholePayment(outbox.getPaymentId(), RefundReason.BOOKING_NOT_CONFIRMABLE);
                log.error("Booking {} rejected {} ({}), payment {} queued for refund", outbox.getBookingId(),
                        outbox.getEventType(), error, outbox.getPaymentId());
            }
            case RETRY -> scheduleRetry(outbox, error, now);
        }
    }

    // Thông báo không phải lệnh tiền: không ghi nhật ký giao dịch, Booking từ chối thì chỉ đánh FAILED (không hoàn gì).
    private void recordNotificationResult(PaymentOutbox outbox, Outcome outcome, String error, Instant now) {
        switch (outcome) {
            case SENT -> {
                outbox.setStatus(OutboxStatus.SENT);
                outbox.setSentAt(now);
                outbox.setLastError(null);
            }
            case REJECTED -> {
                outbox.setStatus(OutboxStatus.FAILED);
                outbox.setLastError(truncate("Booking rejected: " + error));
                log.error("Booking rejected {} notification {} ({})", outbox.getEventType(), outbox.getId(), error);
            }
            case RETRY -> scheduleRetry(outbox, error, now);
        }
    }

    private void scheduleRetry(PaymentOutbox outbox, String error, Instant now) {
        PaymentProperties.Outbox outboxProperties = paymentProperties.getOutbox();
        outbox.setLastError(truncate(error));
        if (outbox.getAttempts() >= outboxProperties.getMaxAttempts()) {
            outbox.setStatus(OutboxStatus.FAILED);
            log.error("{} for booking {} FAILED after {} attempts ({})", outbox.getEventType(), outbox.getBookingId(),
                    outbox.getAttempts(), error);
            return;
        }
        int shift = Math.min(outbox.getAttempts() - 1, MAX_BACKOFF_SHIFT);
        Duration backoff = outboxProperties.getBackoffBase().multipliedBy(1L << Math.max(shift, 0));
        if (backoff.compareTo(outboxProperties.getBackoffMax()) > 0) {
            backoff = outboxProperties.getBackoffMax();
        }
        outbox.setNextAttemptAt(now.plus(backoff));
        log.warn("{} for booking {} failed ({}), retry in {}", outbox.getEventType(), outbox.getBookingId(), error,
                backoff);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }

    private enum Outcome {
        SENT,
        REJECTED,
        RETRY
    }
}
