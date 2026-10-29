package mss301.paymentservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.client.BookingClient;
import mss301.paymentservice.client.dto.DepositPaidRequest;
import mss301.paymentservice.config.PaymentProperties;
import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.entity.PaymentOutbox;
import mss301.paymentservice.enums.OutboxEventType;
import mss301.paymentservice.enums.OutboxStatus;
import mss301.paymentservice.enums.RefundReason;
import mss301.paymentservice.enums.TransactionEventType;
import mss301.paymentservice.exception.AppException;
import mss301.paymentservice.exception.ErrorCode;
import mss301.paymentservice.mapper.PaymentOutboxMapper;
import mss301.paymentservice.repository.PaymentOutboxRepository;
import mss301.paymentservice.repository.PaymentRepository;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Gửi xác nhận cọc sang Booking ({@code POST /bookings/{id}/deposit-paid}, Booking trả 200 khi đã CONFIRMED nên gửi lại
 * an toàn). Booking từ chối (4xx — hết hạn / đã hủy / không tồn tại) → FAILED và hoàn toàn bộ cọc
 * {@code BOOKING_NOT_CONFIRMABLE}; lỗi tạm thời → backoff tới maxAttempts rồi FAILED để admin xử lý (Đợt 4).
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PaymentOutboxServiceImpl implements PaymentOutboxService {
    static int MAX_ERROR_LENGTH = 500;
    static int MAX_BACKOFF_SHIFT = 20;

    PaymentOutboxRepository paymentOutboxRepository;
    PaymentRepository paymentRepository;
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
        if (paymentOutboxRepository.existsByEventTypeAndPaymentId(OutboxEventType.DEPOSIT_CONFIRMATION, payment.getId())) {
            return;
        }
        PaymentOutbox outbox = paymentOutboxRepository.save(paymentOutboxMapper.toOutbox(
                OutboxEventType.DEPOSIT_CONFIRMATION, payment.getBookingId(), payment.getId(), paymentReference,
                OutboxStatus.PENDING, clock.instant()));
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
            bookingClient.markDepositPaid(claimed.getBookingId(), new DepositPaidRequest(claimed.getPaymentReference()));
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
        switch (outcome) {
            case SENT -> {
                outbox.setStatus(OutboxStatus.SENT);
                outbox.setSentAt(now);
                outbox.setLastError(null);
                paymentRepository.findById(outbox.getPaymentId())
                        .ifPresent(payment -> payment.setBookingNotifiedAt(now));
                paymentTransactionService.record(outbox.getBookingId(), outbox.getPaymentId(), null, null,
                        TransactionEventType.BOOKING_CONFIRMED, null, null, "reference=" + outbox.getPaymentReference(),
                        null);
                log.info("Booking {} confirmed deposit payment {}", outbox.getBookingId(), outbox.getPaymentId());
            }
            case REJECTED -> {
                outbox.setStatus(OutboxStatus.FAILED);
                outbox.setLastError(truncate("Booking rejected: " + error));
                paymentTransactionService.record(outbox.getBookingId(), outbox.getPaymentId(), null, null,
                        TransactionEventType.BOOKING_REJECTED, null, null, error, null);
                // Tiền đã thu nhưng booking không còn nhận cọc (hết hạn / hủy) — hoàn toàn bộ.
                refundService.refundWholePayment(outbox.getPaymentId(), RefundReason.BOOKING_NOT_CONFIRMABLE);
                log.error("Booking {} rejected deposit confirmation ({}), deposit {} queued for refund",
                        outbox.getBookingId(), error, outbox.getPaymentId());
            }
            case RETRY -> scheduleRetry(outbox, error, now);
        }
    }

    private void scheduleRetry(PaymentOutbox outbox, String error, Instant now) {
        PaymentProperties.Outbox outboxProperties = paymentProperties.getOutbox();
        outbox.setLastError(truncate(error));
        if (outbox.getAttempts() >= outboxProperties.getMaxAttempts()) {
            outbox.setStatus(OutboxStatus.FAILED);
            log.error("Deposit confirmation for booking {} FAILED after {} attempts ({})", outbox.getBookingId(),
                    outbox.getAttempts(), error);
            return;
        }
        int shift = Math.min(outbox.getAttempts() - 1, MAX_BACKOFF_SHIFT);
        Duration backoff = outboxProperties.getBackoffBase().multipliedBy(1L << Math.max(shift, 0));
        if (backoff.compareTo(outboxProperties.getBackoffMax()) > 0) {
            backoff = outboxProperties.getBackoffMax();
        }
        outbox.setNextAttemptAt(now.plus(backoff));
        log.warn("Deposit confirmation for booking {} failed ({}), retry in {}", outbox.getBookingId(), error, backoff);
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
