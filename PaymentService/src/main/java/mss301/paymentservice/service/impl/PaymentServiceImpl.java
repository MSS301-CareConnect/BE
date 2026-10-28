package mss301.paymentservice.service.impl;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import feign.FeignException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.client.BookingClient;
import mss301.paymentservice.config.PaymentProperties;
import mss301.paymentservice.dto.request.DepositPaidRequest;
import mss301.paymentservice.dto.response.*;
import mss301.paymentservice.entity.*;
import mss301.paymentservice.enums.*;
import mss301.paymentservice.exception.*;
import mss301.paymentservice.gateway.*;
import mss301.paymentservice.mapper.PaymentMapper;
import mss301.paymentservice.repository.*;
import mss301.paymentservice.service.PaymentService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentServiceImpl implements PaymentService {
    PaymentRepository paymentRepository;
    PaymentAttemptRepository attemptRepository;
    BookingClient bookingClient;
    PaymentGatewayFactory gatewayFactory;
    VnPayGateway vnPayGateway;
    PaymentMapper mapper;
    PaymentProperties properties;
    TransactionTemplate transactionTemplate;
    Clock clock;

    @Override
    public CheckoutResponse createCheckout(UUID customerId, UUID bookingId, PaymentProvider provider, String clientIp) {
        BookingPaymentContextResponse booking = loadBooking(bookingId);

        if (!customerId.equals(booking.getCustomerId()))
            throw new AppException(ErrorCode.UNAUTHORIZED);

        if (!"AWAITING_DEPOSIT".equals(booking.getStatus()))
            throw new AppException(ErrorCode.BOOKING_NOT_PAYABLE);

        if (booking.getDepositDueAt() != null && clock.instant().isAfter(booking.getDepositDueAt()))
            throw new AppException(ErrorCode.BOOKING_NOT_PAYABLE);

        PendingCheckout pending = transactionTemplate.execute(status -> createPending(booking, provider));

        if (pending == null)
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR);

        CheckoutResult result = gatewayFactory.get(provider).createCheckout(CheckoutCommand.builder()
                .provider(provider).reference(pending.attempt().getReference()).bookingCode(booking.getBookingCode())
                .amount(pending.payment().getAmount()).currency("VND").expiresAt(pending.attempt().getExpiresAt())
                .clientIp(clientIp == null ? "127.0.0.1" : clientIp).build());

        transactionTemplate.executeWithoutResult(s -> {
            PaymentAttempt attempt = attemptRepository.findById(pending.attempt().getId()).orElseThrow();
            attempt.setProviderCheckoutId(result.providerCheckoutId());
            attempt.setCheckoutUrl(result.checkoutUrl());
        });

        return CheckoutResponse.builder().paymentId(pending.payment().getId()).bookingId(bookingId).provider(provider)
                .status(PaymentStatus.PENDING).amount(pending.payment().getAmount()).currency("VND")
                .checkoutUrl(result.checkoutUrl()).expiresAt(pending.attempt().getExpiresAt()).build();
    }

    private PendingCheckout createPending(BookingPaymentContextResponse booking, PaymentProvider provider) {
        Payment payment = paymentRepository.findByBookingId(booking.getBookingId()).orElseGet(() -> {
            Payment p = new Payment();
            p.setId(UUID.randomUUID());
            p.setBookingId(booking.getBookingId());
            p.setCustomerId(booking.getCustomerId());
            p.setCenterId(booking.getCenterId());

            if (booking.getTotalAmount() == null || booking.getTotalAmount().signum() <= 0)
                throw new AppException(ErrorCode.INVALID_REQUEST);

            BigDecimal rate = BigDecimal.valueOf(properties.getPolicy().getDepositPercent()).movePointLeft(2);
            p.setAmount(booking.getTotalAmount().multiply(rate).setScale(0, RoundingMode.HALF_UP));
            p.setCurrency("VND");
            p.setStatus(PaymentStatus.PENDING);
            return paymentRepository.save(p);
        });

        if (payment.getStatus() == PaymentStatus.SUCCESS)
            throw new AppException(ErrorCode.PAYMENT_ALREADY_SUCCESS);

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setPayment(payment);
        attempt.setProvider(provider);
        attempt.setStatus(PaymentStatus.PENDING);
        attempt.setAmount(payment.getAmount());
        attempt.setCurrency(payment.getCurrency());
        attempt.setReference(provider.name() + "-" + UUID.randomUUID());
        attempt.setExpiresAt(clock.instant().plus(Duration.ofMinutes(properties.getCheckoutExpiryMinutes())));
        attemptRepository.save(attempt);
        return new PendingCheckout(payment, attempt);
    }

    private BookingPaymentContextResponse loadBooking(UUID bookingId) {
        try {
            APIResponse<BookingPaymentContextResponse> response = bookingClient.getPaymentContext(bookingId);

            if (response == null || response.getResult() == null)
                throw new AppException(ErrorCode.BOOKING_SERVICE_UNAVAILABLE);

            return response.getResult();
        } catch (FeignException e) {
            throw new AppException(ErrorCode.BOOKING_SERVICE_UNAVAILABLE);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getById(UUID customerId, UUID paymentId) {
        Payment p = paymentRepository.findById(paymentId).orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        checkOwner(customerId, p);
        return mapper.toResponse(p);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getByBooking(UUID customerId, UUID bookingId) {
        Payment p = paymentRepository.findByBookingId(bookingId).orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        checkOwner(customerId, p);
        return mapper.toResponse(p);
    }

    private void checkOwner(UUID customerId, Payment p) {
        if (!customerId.equals(p.getCustomerId())) throw new AppException(ErrorCode.UNAUTHORIZED);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(UUID bookingId, PaymentStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Payment> result = bookingId != null && status != null ? paymentRepository.findByBookingIdAndStatus(bookingId, status, pageable)
                : bookingId != null ? paymentRepository.findByBookingId(bookingId, pageable)
                : status != null ? paymentRepository.findByStatus(status, pageable) : paymentRepository.findAll(pageable);

        return PageResponse.<PaymentResponse>builder().items(result.map(mapper::toResponse).getContent()).page(page).size(size)
                .totalElements(result.getTotalElements()).totalPages(result.getTotalPages()).build();
    }

    @Override
    public OutstandingDebtResponse getOutstandingDebt(UUID customerId) {
        return new OutstandingDebtResponse(false);
    }

    @Override
    public PaymentPolicyResponse getEffectivePolicy(UUID centerId, Instant at) {
        return new PaymentPolicyResponse(properties.getPolicy().getId(), properties.getPolicy().getDepositPercent());
    }

    @Override
    public RevenueResponse getRevenue(Instant from, Instant to, UUID centerId) {
        if (from.isAfter(to)) throw new AppException(ErrorCode.INVALID_REQUEST);
        long count = paymentRepository.successfulCount(from, to, centerId);
        BigDecimal total = paymentRepository.totalRevenue(from, to, centerId);
        BigDecimal average = count == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        return new RevenueResponse(count, total, average);
    }

    @Override
    public VnPayIpnResponse handleVnPayIpn(Map<String, String> params) {
        if (!vnPayGateway.validSignature(params))
            return new VnPayIpnResponse("97", "Invalid signature");

        String reference = params.get("vnp_TxnRef");
        PaymentAttempt current = attemptRepository.findByReference(reference).orElse(null);

        if (current == null)
            return new VnPayIpnResponse("01", "Order not found");

        if (!matchesVnPayAmount(current, params.get("vnp_Amount")))
            return new VnPayIpnResponse("04", "Invalid amount");

        boolean success = "00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"));
        CallbackResult result = transactionTemplate.execute(s -> updateByReference(reference, success, params.get("vnp_TransactionNo"), params.get("vnp_ResponseCode")));

        if (result == null)
            return new VnPayIpnResponse("99", "Unknown error");
        if (success && !result.notified()) {
            try {
                notifyBooking(result.paymentId(), result.bookingId(), reference);
            } catch (AppException e) {
                return new VnPayIpnResponse("99", "Booking update failed; please retry");
            }
        }
        return new VnPayIpnResponse(result.wasAlreadyDone() ? "02" : "00", result.wasAlreadyDone() ? "Order already confirmed" : "Confirm success");
    }

    private boolean matchesVnPayAmount(PaymentAttempt a, String raw) {
        try {
            return a.getAmount().movePointRight(2).compareTo(new BigDecimal(raw)) == 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private CallbackResult updateByReference(String ref, boolean success, String transactionId, String code) {
        PaymentAttempt a = attemptRepository.findByReferenceForUpdate(ref).orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        boolean already = a.getStatus() == PaymentStatus.SUCCESS;
        if (!already) {
            a.setStatus(success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
            a.setProviderTransactionId(transactionId);
            a.setResponseCode(code);
            a.setCompletedAt(clock.instant());
            if (success) {
                a.getPayment().setStatus(PaymentStatus.SUCCESS);
                a.getPayment().setPaidAt(clock.instant());
            }
        }
        return new CallbackResult(a.getPayment().getId(), a.getPayment().getBookingId(), a.getPayment().getBookingNotifiedAt() != null, already);
    }

    private void notifyBooking(UUID paymentId, UUID bookingId, String reference) {
        try {
            bookingClient.markDepositPaid(bookingId, new DepositPaidRequest(reference));
        } catch (FeignException e) {
            throw new AppException(ErrorCode.BOOKING_SERVICE_UNAVAILABLE);
        }
        transactionTemplate.executeWithoutResult(s -> {
            Payment p = paymentRepository.findById(paymentId).orElseThrow();
            p.setBookingNotifiedAt(clock.instant());
        });
    }

    @Override
    public PaymentReturnResponse handleVnPayReturn(Map<String, String> params) {
        if (!vnPayGateway.validSignature(params)) throw new AppException(ErrorCode.INVALID_SIGNATURE);
        PaymentAttempt a = attemptRepository.findByReference(params.get("vnp_TxnRef")).orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        return returnResponse(a.getPayment(), "Kết quả chính thức được cập nhật qua VNPay IPN");
    }

    @Override
    public void handleStripeWebhook(String payload, String signature) {
        if (!properties.getStripe().isEnabled() || properties.getStripe().getWebhookSecret() == null
                || properties.getStripe().getWebhookSecret().isBlank())
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_DISABLED);
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, properties.getStripe().getWebhookSecret());
        } catch (SignatureVerificationException e) {
            throw new AppException(ErrorCode.INVALID_SIGNATURE);
        }
        StripeObject object = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(object instanceof Session session))
            return;

        if ("checkout.session.completed".equals(event.getType()))
            completeStripe(session);
        else if ("checkout.session.expired".equals(event.getType()))
            expireStripe(session.getId());
    }

    private void completeStripe(Session session) {
        CallbackResult result = transactionTemplate.execute(s -> {
            PaymentAttempt a = attemptRepository.findByCheckoutIdForUpdate(session.getId()).orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
            if (session.getAmountTotal() == null || a.getAmount().setScale(0).longValueExact() != session.getAmountTotal()
                    || !"vnd".equalsIgnoreCase(session.getCurrency()) || !"paid".equals(session.getPaymentStatus()))
                throw new AppException(ErrorCode.AMOUNT_MISMATCH);
            boolean already = a.getStatus() == PaymentStatus.SUCCESS;
            if (!already) {
                a.setStatus(PaymentStatus.SUCCESS);
                a.setProviderTransactionId(session.getPaymentIntent());
                a.setResponseCode("paid");
                a.setCompletedAt(clock.instant());
                a.getPayment().setStatus(PaymentStatus.SUCCESS);
                a.getPayment().setPaidAt(clock.instant());
            }
            return new CallbackResult(a.getPayment().getId(), a.getPayment().getBookingId(), a.getPayment().getBookingNotifiedAt() != null, already);
        });
        if (result != null && !result.notified())
            notifyBooking(result.paymentId(), result.bookingId(), session.getId());
    }

    private void expireStripe(String sessionId) {
        transactionTemplate.executeWithoutResult(s -> {
            PaymentAttempt a = attemptRepository.findByCheckoutIdForUpdate(sessionId).orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
            if (a.getStatus() == PaymentStatus.PENDING) {
                a.setStatus(PaymentStatus.EXPIRED);
                a.setCompletedAt(clock.instant());
            }
        });
    }

    @Override
    public PaymentReturnResponse handleStripeReturn(String sessionId) {
        PaymentAttempt a = attemptRepository.findByProviderCheckoutId(sessionId).orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        return returnResponse(a.getPayment(), "Kết quả chính thức được cập nhật qua Stripe webhook");
    }

    private PaymentReturnResponse returnResponse(Payment p, String message) {
        return new PaymentReturnResponse(p.getId(), p.getBookingId(), p.getStatus(), message);
    }

    private record PendingCheckout(Payment payment, PaymentAttempt attempt) {
    }

    private record CallbackResult(UUID paymentId, UUID bookingId, boolean notified, boolean wasAlreadyDone) {
    }
}
