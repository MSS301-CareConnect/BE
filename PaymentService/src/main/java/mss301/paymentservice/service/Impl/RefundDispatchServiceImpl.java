package mss301.paymentservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.config.PaymentProperties;
import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.entity.PaymentAttempt;
import mss301.paymentservice.entity.Refund;
import mss301.paymentservice.enums.PaymentProvider;
import mss301.paymentservice.enums.PaymentStatus;
import mss301.paymentservice.enums.RefundMode;
import mss301.paymentservice.enums.RefundStatus;
import mss301.paymentservice.enums.TransactionEventType;
import mss301.paymentservice.gateway.RefundCommand;
import mss301.paymentservice.gateway.RefundResult;
import mss301.paymentservice.gateway.VnPayRefundGateway;
import mss301.paymentservice.repository.PaymentAttemptRepository;
import mss301.paymentservice.repository.PaymentRepository;
import mss301.paymentservice.repository.RefundRepository;
import mss301.paymentservice.service.PaymentTransactionService;
import mss301.paymentservice.service.RefundDispatchService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Dispatcher hoàn tiền (PAY-10). Mỗi refund: transaction ngắn "nhận việc" (SKIP LOCKED + thuê dòng), gọi cổng NGOÀI
 * transaction, rồi transaction ngắn ghi kết quả. SIMULATED (mặc định) đánh REFUNDED ngay mà vẫn đi đủ state machine,
 * nhật ký và cộng payment.refundedAmount; VNPAY gọi adapter; STRIPE → FAILED (D16 không hoàn qua Stripe).
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RefundDispatchServiceImpl implements RefundDispatchService {
    static String SIMULATED_CODE = "SIMULATED";
    static String SYSTEM_ACTOR = "system";
    static int MAX_ERROR_LENGTH = 500;
    static int MAX_BACKOFF_SHIFT = 20;

    RefundRepository refundRepository;
    PaymentRepository paymentRepository;
    PaymentAttemptRepository paymentAttemptRepository;
    VnPayRefundGateway vnPayRefundGateway;
    PaymentTransactionService paymentTransactionService;
    PaymentProperties paymentProperties;
    TransactionTemplate transactionTemplate;
    Clock clock;

    @Override
    public List<UUID> findDueIds() {
        return refundRepository.findDueIds(RefundStatus.REFUND_PENDING, clock.instant(),
                PageRequest.of(0, paymentProperties.getRefund().getBatchSize()));
    }

    @Override
    public void process(UUID refundId) {
        Claim claim = transactionTemplate.execute(status -> claim(refundId));
        if (claim == null) {
            return;
        }
        RefundResult result;
        try {
            result = execute(claim);
        } catch (RuntimeException e) {
            // Lỗi bất ngờ của adapter coi như tạm thời — có trần số lần, không làm sập scheduler.
            log.warn("Refund {} dispatch error ({})", refundId, e.getClass().getSimpleName());
            result = RefundResult.retryable(null, claim.command() == null ? null : claim.command().requestId(),
                    e.getClass().getSimpleName());
        }
        RefundResult finalResult = result;
        transactionTemplate.executeWithoutResult(status -> recordResult(refundId, claim, finalResult));
    }

    // Nhận việc: chỉ dòng REFUND_PENDING đã tới hạn; dời hạn một khoảng "thuê" để lượt khác không gửi trùng.
    private Claim claim(UUID refundId) {
        Refund refund = refundRepository.findForUpdateSkipLocked(refundId).orElse(null);
        Instant now = clock.instant();
        if (refund == null || refund.getStatus() != RefundStatus.REFUND_PENDING || refund.getNextAttemptAt().isAfter(now)) {
            return null;
        }
        refund.setNextAttemptAt(now.plus(paymentProperties.getRefund().getClaimLease()));
        Payment payment = paymentRepository.findById(refund.getPaymentId()).orElse(null);
        PaymentAttempt paidAttempt = findPaidAttempt(refund, payment);
        if (payment == null || paidAttempt == null) {
            return new Claim(null, null);
        }
        // Hoàn một phiên trả trễ / trùng: toàn phần nếu bằng đúng số tiền phiên đó; hoàn thường: so với payment.
        boolean fullRefund = refund.getAttemptId() != null
                ? refund.getAmount().compareTo(paidAttempt.getAmount()) == 0
                : refund.getAmount().compareTo(payment.getAmount()) == 0 && payment.getRefundedAmount().signum() == 0;
        String createdBy = refund.getRequestedBy() == null ? SYSTEM_ACTOR : refund.getRequestedBy().toString();
        String requestId = UUID.randomUUID().toString().replace("-", "");
        // vnp_TransactionDate = vnp_CreateDate của lệnh pay (= createdAt của phiên).
        RefundCommand command = new RefundCommand(refund.getId(), requestId, paidAttempt.getReference(),
                paidAttempt.getProviderTransactionId(), paidAttempt.getCreatedAt(), refund.getAmount(), fullRefund,
                createdBy, "Hoan tien booking " + refund.getBookingId());
        return new Claim(command, paidAttempt.getProvider());
    }

    // Hoàn thường đi qua giao dịch gốc (phiên thu tiền đầu tiên); hoàn phiên trả trễ / trùng đi qua đúng phiên đó.
    private PaymentAttempt findPaidAttempt(Refund refund, Payment payment) {
        if (payment == null) {
            return null;
        }
        if (refund.getAttemptId() != null) {
            return paymentAttemptRepository.findById(refund.getAttemptId()).orElse(null);
        }
        return paymentAttemptRepository.findAllCompletedByPayment(payment.getId(), PaymentStatus.SUCCESS).stream()
                .findFirst()
                .orElse(null);
    }

    private RefundResult execute(Claim claim) {
        if (claim.command() == null) {
            return RefundResult.rejected(null, null, "Payment has no successful attempt");
        }
        if (paymentProperties.getRefund().getMode() == RefundMode.SIMULATED) {
            return RefundResult.succeeded(SIMULATED_CODE, claim.command().requestId());
        }
        if (claim.provider() != PaymentProvider.VNPAY) {
            return RefundResult.rejected(null, claim.command().requestId(), "Refund via " + claim.provider()
                    + " is not supported");
        }
        return vnPayRefundGateway.refund(claim.command());
    }

    private void recordResult(UUID refundId, Claim claim, RefundResult result) {
        Refund refund = refundRepository.findForUpdate(refundId).orElse(null);
        if (refund == null || refund.getStatus() != RefundStatus.REFUND_PENDING) {
            return;
        }
        Instant now = clock.instant();
        refund.setAttempts(refund.getAttempts() + 1);
        refund.setProviderRequestId(result.providerRequestId());
        refund.setProviderResponseCode(result.responseCode());
        if (paymentProperties.getRefund().getMode() == RefundMode.VNPAY && claim.command() != null) {
            paymentTransactionService.record(refund.getBookingId(), refund.getPaymentId(), refund.getId(), null,
                    TransactionEventType.REFUND_CALL, refund.getAmount(), null,
                    "requestId=" + result.providerRequestId() + " code=" + result.responseCode(), null);
        }
        switch (result.outcome()) {
            case SUCCEEDED -> markRefunded(refund, now);
            case RETRYABLE -> scheduleRetry(refund, result, now);
            case REJECTED -> markFailed(refund, result.message());
        }
    }

    private void markRefunded(Refund refund, Instant now) {
        if (refund.getAttemptId() != null) {
            // Tiền của phiên trả trễ / trùng không nằm trong payment.amount → không cộng refundedAmount.
            completeRefund(refund, now);
            return;
        }
        Payment payment = paymentRepository.findForUpdate(refund.getPaymentId()).orElse(null);
        BigDecimal refunded = payment == null ? null : payment.getRefundedAmount().add(refund.getAmount());
        if (payment == null || refunded.compareTo(payment.getAmount()) > 0) {
            // Không bao giờ xảy ra nếu bất biến lúc tạo lệnh đúng — dừng lại thay vì hoàn vượt số đã thu.
            markFailed(refund, "Refunded amount would exceed payment amount");
            return;
        }
        payment.setRefundedAmount(refunded);
        completeRefund(refund, now);
    }

    private void completeRefund(Refund refund, Instant now) {
        refund.setStatus(RefundStatus.REFUNDED);
        refund.setRefundedAt(now);
        refund.setLastError(null);
        paymentTransactionService.record(refund.getBookingId(), refund.getPaymentId(), refund.getId(), null,
                TransactionEventType.REFUND_SUCCEEDED, refund.getAmount(), null,
                "code=" + refund.getProviderResponseCode(), null);
        log.info("Refund {} of {} on payment {} completed", refund.getId(), refund.getAmount(), refund.getPaymentId());
    }

    // Backoff lũy thừa base × 2^(n−1), trần backoffMax; quá maxAttempts → FAILED cho admin xử lý.
    private void scheduleRetry(Refund refund, RefundResult result, Instant now) {
        PaymentProperties.Refund refundProperties = paymentProperties.getRefund();
        if (refund.getAttempts() >= refundProperties.getMaxAttempts()) {
            markFailed(refund, "Max attempts reached: " + result.message());
            return;
        }
        int shift = Math.min(refund.getAttempts() - 1, MAX_BACKOFF_SHIFT);
        Duration backoff = refundProperties.getBackoffBase().multipliedBy(1L << Math.max(shift, 0));
        if (backoff.compareTo(refundProperties.getBackoffMax()) > 0) {
            backoff = refundProperties.getBackoffMax();
        }
        refund.setNextAttemptAt(now.plus(backoff));
        refund.setLastError(truncate(result.message()));
        log.warn("Refund {} attempt {} will retry in {} ({})", refund.getId(), refund.getAttempts(), backoff,
                result.message());
    }

    private void markFailed(Refund refund, String message) {
        refund.setStatus(RefundStatus.FAILED);
        refund.setLastError(truncate(message));
        paymentTransactionService.record(refund.getBookingId(), refund.getPaymentId(), refund.getId(), null,
                TransactionEventType.REFUND_FAILED, refund.getAmount(), null, truncate(message), null);
        log.error("Refund {} of {} on booking {} FAILED: {}", refund.getId(), refund.getAmount(), refund.getBookingId(),
                message);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }

    private record Claim(RefundCommand command, PaymentProvider provider) {
    }
}
