package mss301.paymentservice.service.Impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.dto.response.CheckoutResponse;
import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.entity.PaymentAttempt;
import mss301.paymentservice.enums.PaymentProvider;
import mss301.paymentservice.enums.PaymentStatus;
import mss301.paymentservice.enums.TransactionEventType;
import mss301.paymentservice.exception.AppException;
import mss301.paymentservice.exception.ErrorCode;
import mss301.paymentservice.gateway.CheckoutCommand;
import mss301.paymentservice.gateway.CheckoutResult;
import mss301.paymentservice.gateway.PaymentGatewayFactory;
import mss301.paymentservice.mapper.PaymentMapper;
import mss301.paymentservice.repository.PaymentAttemptRepository;
import mss301.paymentservice.service.PaymentCheckoutService;
import mss301.paymentservice.service.PaymentTransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PaymentCheckoutServiceImpl implements PaymentCheckoutService {
    static String FORWARDED_FOR = "X-Forwarded-For";
    static String FALLBACK_IP = "127.0.0.1";

    PaymentAttemptRepository paymentAttemptRepository;
    PaymentGatewayFactory paymentGatewayFactory;
    PaymentTransactionService paymentTransactionService;
    PaymentMapper paymentMapper;
    TransactionTemplate transactionTemplate;
    Clock clock;

    @Override
    public void checkProviderEnabled(PaymentProvider provider) {
        paymentGatewayFactory.getEnabled(provider);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentAttempt openAttempt(Payment payment, PaymentProvider provider, Instant expiresAt) {
        Instant now = clock.instant();
        for (PaymentAttempt pendingAttempt : paymentAttemptRepository.findAllByPaymentAndStatus(payment.getId(),
                PaymentStatus.PENDING)) {
            pendingAttempt.setStatus(PaymentStatus.EXPIRED);
            pendingAttempt.setCompletedAt(now);
        }
        String reference = provider.name() + "-" + UUID.randomUUID();
        PaymentAttempt attempt = paymentAttemptRepository.save(paymentMapper.toAttempt(payment, provider, reference,
                PaymentStatus.PENDING, expiresAt));
        paymentTransactionService.record(payment.getBookingId(), payment.getId(), null, payment.getInvoiceId(),
                TransactionEventType.CHECKOUT_CREATED, attempt.getAmount(), null,
                "provider=" + provider + " reference=" + reference + " type=" + payment.getPaymentType(), null);
        log.info("Payment {} checkout {} opened ({} {})", payment.getId(), reference, payment.getPaymentType(),
                attempt.getAmount());
        return attempt;
    }

    @Override
    public CheckoutResponse launch(UUID attemptId, String orderLabel, String clientIp) {
        CheckoutCommand command = transactionTemplate.execute(status -> {
            PaymentAttempt attempt = findAttempt(attemptId);
            return new CheckoutCommand(attempt.getProvider(), attempt.getReference(), orderLabel, attempt.getAmount(),
                    attempt.getCurrency(), attempt.getCreatedAt(), attempt.getExpiresAt(), clientIp);
        });
        CheckoutResult checkoutResult;
        try {
            checkoutResult = paymentGatewayFactory.getEnabled(command.provider()).createCheckout(command);
        } catch (RuntimeException e) {
            log.error("Checkout {} failed at gateway ({})", command.reference(), e.getClass().getSimpleName());
            transactionTemplate.executeWithoutResult(status -> markAttemptFailed(attemptId));
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
        return transactionTemplate.execute(status -> {
            PaymentAttempt attempt = findAttempt(attemptId);
            attempt.setProviderCheckoutId(checkoutResult.providerCheckoutId());
            attempt.setCheckoutUrl(checkoutResult.checkoutUrl());
            return paymentMapper.toCheckoutResponse(attempt.getPayment(), attempt);
        });
    }

    // Controller mỏng (rule #22): IP khách cho vnp_IpAddr đọc ở đây — phần tử đầu của X-Forwarded-For (gateway thêm).
    @Override
    public String resolveClientIp(HttpServletRequest httpServletRequest) {
        String forwardedFor = httpServletRequest.getHeader(FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String remoteAddress = httpServletRequest.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? FALLBACK_IP : remoteAddress;
    }

    private PaymentAttempt findAttempt(UUID attemptId) {
        return paymentAttemptRepository.findWithPaymentById(attemptId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private void markAttemptFailed(UUID attemptId) {
        PaymentAttempt attempt = findAttempt(attemptId);
        if (attempt.getStatus() == PaymentStatus.PENDING) {
            attempt.setStatus(PaymentStatus.FAILED);
            attempt.setCompletedAt(clock.instant());
        }
    }
}
