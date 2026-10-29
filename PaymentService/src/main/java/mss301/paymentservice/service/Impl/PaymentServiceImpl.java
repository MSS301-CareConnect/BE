package mss301.paymentservice.service.Impl;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.repository.RefundRepository;
import mss301.paymentservice.client.BookingClient;
import mss301.paymentservice.client.dto.BookingPaymentContextResponse;
import mss301.paymentservice.client.dto.RemoteApiResponse;
import mss301.paymentservice.config.PaymentProperties;
import mss301.paymentservice.dto.request.CreateCheckoutRequest;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.CheckoutResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.dto.response.PaymentResponse;
import mss301.paymentservice.dto.response.PaymentReturnResponse;
import mss301.paymentservice.dto.response.PolicySnapshot;
import mss301.paymentservice.dto.response.RevenueResponse;
import mss301.paymentservice.dto.response.VnPayIpnResponse;
import mss301.paymentservice.entity.BookingFinancial;
import mss301.paymentservice.entity.OccurrenceFinancial;
import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.entity.PaymentAttempt;
import mss301.paymentservice.entity.PlatformPolicy;
import mss301.paymentservice.enums.BookingFinancialStatus;
import mss301.paymentservice.enums.BookingStatus;
import mss301.paymentservice.enums.OccurrenceStatus;
import mss301.paymentservice.enums.PaymentProvider;
import mss301.paymentservice.enums.PaymentStatus;
import mss301.paymentservice.enums.PaymentType;
import mss301.paymentservice.enums.RefundReason;
import mss301.paymentservice.enums.RoleCode;
import mss301.paymentservice.enums.TransactionEventType;
import mss301.paymentservice.exception.AppException;
import mss301.paymentservice.exception.ErrorCode;
import mss301.paymentservice.gateway.QueryResult;
import mss301.paymentservice.gateway.VnPayGateway;
import mss301.paymentservice.gateway.VnPayQueryGateway;
import mss301.paymentservice.mapper.FinancialMapper;
import mss301.paymentservice.mapper.PaymentMapper;
import mss301.paymentservice.mapper.PaymentPolicyMapper;
import mss301.paymentservice.repository.BookingFinancialRepository;
import mss301.paymentservice.repository.OccurrenceFinancialRepository;
import mss301.paymentservice.repository.PaymentAttemptRepository;
import mss301.paymentservice.repository.PaymentRepository;
import mss301.paymentservice.service.PaymentCheckoutService;
import mss301.paymentservice.service.PaymentOutboxService;
import mss301.paymentservice.service.PaymentPolicyService;
import mss301.paymentservice.service.PaymentService;
import mss301.paymentservice.service.PaymentTransactionService;
import mss301.paymentservice.service.RefundService;
import mss301.paymentservice.service.RemainingInvoiceService;
import mss301.paymentservice.util.DepositAllocator;
import mss301.paymentservice.util.VndMoney;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PaymentServiceImpl implements PaymentService {
    static String CURRENCY = "VND";
    static String VNP_TXN_REF = "vnp_TxnRef";
    static String VNP_AMOUNT = "vnp_Amount";
    static String VNP_RESPONSE_CODE = "vnp_ResponseCode";
    static String VNP_TRANSACTION_STATUS = "vnp_TransactionStatus";
    static String VNP_TRANSACTION_NO = "vnp_TransactionNo";
    static String VNPAY_SUCCESS = "00";
    static String STRIPE_COMPLETED = "checkout.session.completed";
    static String STRIPE_EXPIRED = "checkout.session.expired";
    static String STRIPE_PAID = "paid";
    static int MAX_PAGE_SIZE = 100;
    static String CREATED_AT = "createdAt";

    PaymentRepository paymentRepository;
    RefundRepository refundRepository;
    PaymentAttemptRepository paymentAttemptRepository;
    BookingFinancialRepository bookingFinancialRepository;
    OccurrenceFinancialRepository occurrenceFinancialRepository;
    BookingClient bookingClient;
    PaymentPolicyService paymentPolicyService;
    PaymentCheckoutService paymentCheckoutService;
    PaymentTransactionService paymentTransactionService;
    PaymentOutboxService paymentOutboxService;
    RefundService refundService;
    RemainingInvoiceService remainingInvoiceService;
    VnPayGateway vnPayGateway;
    VnPayQueryGateway vnPayQueryGateway;
    PaymentMapper paymentMapper;
    PaymentPolicyMapper paymentPolicyMapper;
    FinancialMapper financialMapper;
    PaymentProperties paymentProperties;
    TransactionTemplate transactionTemplate;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PaymentResponse> get(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        checkReadScope(payment, actorId, roles, centerId);
        PaymentResponse paymentResponse = withReserved(List.of(payment)).getFirst();
        return APIResponse.success("Lấy giao dịch thành công", paymentResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PaymentResponse> getByBooking(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID bookingId,
                                                     PaymentType paymentType) {
        PaymentType type = paymentType == null ? PaymentType.DEPOSIT : paymentType;
        Payment payment = paymentRepository.findByBookingIdAndPaymentType(bookingId, type)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        checkReadScope(payment, actorId, roles, centerId);
        PaymentResponse paymentResponse = withReserved(List.of(payment)).getFirst();
        return APIResponse.success("Lấy giao dịch thành công", paymentResponse);
    }

    // M-09: gắn tiền hoàn đang giữ chỗ để bên gọi (Booking khiếu nại) tính đúng trần còn hoàn được.
    private List<PaymentResponse> withReserved(List<Payment> payments) {
        Map<UUID, BigDecimal> reserved = new HashMap<>();
        if (!payments.isEmpty()) {
            for (Object[] row : refundRepository.sumAmountByPayment(payments.stream().map(Payment::getId).toList(),
                    RefundServiceImpl.RESERVED_STATUSES)) {
                reserved.put((UUID) row[0], (BigDecimal) row[1]);
            }
        }
        List<PaymentResponse> responses = paymentMapper.toResponseList(payments);
        responses.forEach(response -> response.setReservedRefundAmount(
                reserved.getOrDefault(response.getId(), BigDecimal.ZERO)));
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<PaymentResponse>> list(UUID actorId, Set<RoleCode> roles, UUID centerId,
                                                           UUID bookingId, PaymentStatus status,
                                                           PaymentType paymentType, int page, int size) {
        Specification<Payment> specification = buildListSpecification(actorId, roles, centerId, bookingId, status,
                paymentType);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, CREATED_AT));
        Page<Payment> payments = paymentRepository.findAll(specification, pageRequest);
        PageResponse<PaymentResponse> pageResponse = PageResponse.of(payments, withReserved(payments.getContent()));
        return APIResponse.success("Lấy danh sách giao dịch thành công", pageResponse);
    }

    // PAY-15 tối thiểu (D15): doanh thu thuần = đã thu − đã hoàn.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<RevenueResponse> getRevenue(Instant from, Instant to, UUID centerId) {
        if (from.isAfter(to)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        long count = paymentRepository.countPaid(PaymentStatus.SUCCESS, from, to, centerId);
        BigDecimal gross = paymentRepository.sumPaid(PaymentStatus.SUCCESS, from, to, centerId);
        BigDecimal refunded = paymentRepository.sumRefunded(PaymentStatus.SUCCESS, from, to, centerId);
        BigDecimal net = gross.subtract(refunded);
        BigDecimal average = count == 0
                ? BigDecimal.ZERO
                : net.divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP);
        RevenueResponse revenueResponse = paymentMapper.toRevenueResponse(count, gross, refunded, net, average);
        return APIResponse.success("Thống kê doanh thu thành công", revenueResponse);
    }

    @Override
    public APIResponse<CheckoutResponse> createCheckout(UUID customerId, UUID bookingId, CreateCheckoutRequest request,
                                                        HttpServletRequest httpServletRequest) {
        paymentCheckoutService.checkProviderEnabled(request.getProvider());
        // Đọc Booking ngoài transaction (rule #24).
        BookingPaymentContextResponse context = fetchPaymentContext(bookingId);
        if (!customerId.equals(context.getCustomerId())) {
            // Không lộ booking của người khác có tồn tại hay không.
            throw new AppException(ErrorCode.BOOKING_NOT_FOUND);
        }
        Instant now = clock.instant();
        boolean pastDue = context.getDepositDueAt() != null && !now.isBefore(context.getDepositDueAt());
        if (context.getStatus() != BookingStatus.AWAITING_DEPOSIT || pastDue) {
            throw new AppException(ErrorCode.BOOKING_NOT_PAYABLE);
        }
        List<DepositAllocator.Line> lines = toAllocationLines(context);
        PlatformPolicy policy = paymentPolicyService.findEffective(context.getCenterId(), now);
        DepositAllocator.Result allocation = DepositAllocator.allocate(lines, policy.getDepositPercent());
        if (allocation.totalDeposit().signum() <= 0) {
            throw new AppException(ErrorCode.BOOKING_NOT_PAYABLE);
        }
        PolicySnapshot policySnapshot = paymentPolicyMapper.toSnapshot(policy);
        Instant expiresAt = now.plus(Duration.ofMinutes(policy.getPaymentSessionTtlMinutes()));
        if (context.getDepositDueAt() != null && context.getDepositDueAt().isBefore(expiresAt)) {
            expiresAt = context.getDepositDueAt();
        }
        Instant attemptExpiresAt = expiresAt;
        UUID attemptId = transactionTemplate.execute(status -> openDepositAttempt(context, policySnapshot, allocation,
                request, attemptExpiresAt));
        String clientIp = paymentCheckoutService.resolveClientIp(httpServletRequest);
        CheckoutResponse checkoutResponse = paymentCheckoutService.launch(attemptId, "booking " + context.getBookingCode(),
                clientIp);
        return APIResponse.success("Tạo phiên thanh toán thành công", checkoutResponse);
    }

    @Override
    public VnPayIpnResponse handleVnPayIpn(Map<String, String> params) {
        if (!vnPayGateway.isValidSignature(params)) {
            log.warn("VNPay IPN rejected: invalid signature (ref {})", params.get(VNP_TXN_REF));
            return ipnResponse("97", "Invalid signature");
        }
        // Đợt 1b: IPN chỉ ghi nhận tiền (+ outbox xác nhận cọc) trong một transaction — Booking lỗi không làm IPN lỗi.
        IpnOutcome outcome = transactionTemplate.execute(status -> applyVnPayIpn(params));
        return ipnResponse(outcome.responseCode(), outcome.message());
    }

    @Override
    @Transactional
    public APIResponse<PaymentReturnResponse> handleVnPayReturn(Map<String, String> params) {
        if (!vnPayGateway.isValidSignature(params)) {
            throw new AppException(ErrorCode.INVALID_SIGNATURE);
        }
        PaymentAttempt attempt = paymentAttemptRepository.findByReference(params.get(VNP_TXN_REF))
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        Payment payment = attempt.getPayment();
        paymentTransactionService.record(payment.getBookingId(), payment.getId(), null, payment.getInvoiceId(),
                TransactionEventType.RETURN, null, null, "responseCode=" + params.get(VNP_RESPONSE_CODE), true);
        PaymentReturnResponse paymentReturnResponse = paymentMapper.toReturnResponse(payment,
                "Kết quả chính thức được cập nhật qua VNPay IPN");
        return APIResponse.success("Nhận kết quả VNPay thành công", paymentReturnResponse);
    }

    @Override
    public APIResponse<Void> handleStripeWebhook(String payload, String signature) {
        PaymentProperties.Stripe stripe = paymentProperties.getStripe();
        if (!stripe.isEnabled() || stripe.getWebhookSecret() == null || stripe.getWebhookSecret().isBlank()) {
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_DISABLED);
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, stripe.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            throw new AppException(ErrorCode.INVALID_SIGNATURE);
        }
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (stripeObject instanceof Session session) {
            if (STRIPE_COMPLETED.equals(event.getType())) {
                completeStripe(session);
            } else if (STRIPE_EXPIRED.equals(event.getType())) {
                transactionTemplate.executeWithoutResult(status -> expireStripe(session.getId()));
            }
        }
        return APIResponse.success("Đã xử lý webhook");
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PaymentReturnResponse> handleStripeReturn(String sessionId) {
        PaymentAttempt attempt = paymentAttemptRepository.findByProviderCheckoutId(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        PaymentReturnResponse paymentReturnResponse = paymentMapper.toReturnResponse(attempt.getPayment(),
                "Kết quả chính thức được cập nhật qua Stripe webhook");
        return APIResponse.success("Nhận kết quả Stripe thành công", paymentReturnResponse);
    }

    @Override
    public List<UUID> findStaleAttemptIds() {
        Instant before = clock.instant().minus(paymentProperties.getExpiry().getGrace());
        return paymentAttemptRepository.findStaleIds(PaymentStatus.PENDING, before,
                PageRequest.of(0, paymentProperties.getExpiry().getBatchSize()));
    }

    @Override
    public void reconcileAttempt(UUID attemptId) {
        Instant now = clock.instant();
        StaleAttempt stale = transactionTemplate.execute(status -> paymentAttemptRepository.findById(attemptId)
                .filter(attempt -> attempt.getStatus() == PaymentStatus.PENDING)
                .filter(attempt -> !attempt.getExpiresAt().plus(paymentProperties.getExpiry().getGrace()).isAfter(now))
                .map(attempt -> new StaleAttempt(attempt.getProvider(), attempt.getReference(), attempt.getCreatedAt(),
                        attempt.getExpiresAt()))
                .orElse(null));
        if (stale == null) {
            return;
        }
        // Gọi VNPay ngoài transaction (rule #24); tắt querydr → coi như chưa thu.
        QueryResult queryResult = stale.provider() == PaymentProvider.VNPAY && paymentProperties.getVnpay().isQuerydrEnabled()
                ? vnPayQueryGateway.query(stale.reference(), stale.createdAt())
                : new QueryResult(QueryResult.Status.NOT_PAID, null, null, null);
        transactionTemplate.executeWithoutResult(status -> applyReconciliation(stale, queryResult));
    }

    private void applyReconciliation(StaleAttempt stale, QueryResult queryResult) {
        PaymentAttempt attempt = paymentAttemptRepository.findForUpdateByReference(stale.reference()).orElse(null);
        if (attempt == null || attempt.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        Payment payment = attempt.getPayment();
        Instant now = clock.instant();
        if (paymentProperties.getVnpay().isQuerydrEnabled() && stale.provider() == PaymentProvider.VNPAY) {
            paymentTransactionService.record(payment.getBookingId(), payment.getId(), null, payment.getInvoiceId(),
                    TransactionEventType.QUERYDR, null, null, "reference=" + stale.reference() + " result="
                            + queryResult.status() + " code=" + queryResult.responseCode(), null);
        }
        if (queryResult.status() == QueryResult.Status.PAID
                && matchesVnPayAmount(attempt, queryResult.amountMinor() == null
                        ? VndMoney.toVnPayAmount(attempt.getAmount())
                        : queryResult.amountMinor())) {
            log.warn("Attempt {} paid without IPN — confirmed via querydr", stale.reference());
            completeAttempt(attempt, true, queryResult.transactionNo(), VNPAY_SUCCESS);
            return;
        }
        boolean stillUnknown = queryResult.status() == QueryResult.Status.UNKNOWN
                && stale.expiresAt().plus(paymentProperties.getExpiry().getMaxUnknown()).isAfter(now);
        if (stillUnknown) {
            return;
        }
        attempt.setStatus(PaymentStatus.EXPIRED);
        attempt.setCompletedAt(now);
        boolean otherPending = paymentAttemptRepository.findAllByPaymentAndStatus(payment.getId(), PaymentStatus.PENDING)
                .stream()
                .anyMatch(other -> !other.getId().equals(attempt.getId()));
        if (payment.getStatus() == PaymentStatus.PENDING && !otherPending) {
            payment.setStatus(PaymentStatus.EXPIRED);
        }
        paymentTransactionService.record(payment.getBookingId(), payment.getId(), null, payment.getInvoiceId(),
                TransactionEventType.PAYMENT_EXPIRED, attempt.getAmount(), null, "reference=" + stale.reference(), null);
        log.info("Attempt {} expired", stale.reference());
    }

    private BookingPaymentContextResponse fetchPaymentContext(UUID bookingId) {
        RemoteApiResponse<BookingPaymentContextResponse> response;
        try {
            response = bookingClient.getPaymentContext(bookingId);
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Booking payment-context {} unavailable ({})", bookingId, e.getClass().getSimpleName());
            throw new AppException(ErrorCode.BOOKING_SERVICE_UNAVAILABLE);
        }
        if (response == null || response.getResult() == null) {
            throw new AppException(ErrorCode.BOOKING_SERVICE_UNAVAILABLE);
        }
        return response.getResult();
    }

    // Chỉ buổi chưa bắt đầu (SCHEDULED / ACKNOWLEDGED) được tính cọc; mọi buổi đó phải có giá.
    private List<DepositAllocator.Line> toAllocationLines(BookingPaymentContextResponse context) {
        List<DepositAllocator.Line> lines = new ArrayList<>();
        if (context.getOccurrences() != null) {
            for (BookingPaymentContextResponse.Occurrence occurrence : context.getOccurrences()) {
                if (!OccurrenceStatus.NOT_STARTED.contains(occurrence.getStatus())) {
                    continue;
                }
                if (occurrence.getPriceAmount() == null || occurrence.getStartAt() == null) {
                    throw new AppException(ErrorCode.BOOKING_PRICE_MISSING);
                }
                lines.add(new DepositAllocator.Line(occurrence.getOccurrenceId(), occurrence.getOccurrenceNo(),
                        occurrence.getStartAt(), occurrence.getPriceAmount()));
            }
        }
        if (lines.isEmpty()) {
            throw new AppException(ErrorCode.BOOKING_NOT_PAYABLE);
        }
        return lines;
    }

    private UUID openDepositAttempt(BookingPaymentContextResponse context, PolicySnapshot policySnapshot,
                                    DepositAllocator.Result allocation, CreateCheckoutRequest request, Instant expiresAt) {
        UUID bookingId = context.getBookingId();
        BookingFinancial financial = bookingFinancialRepository.findForUpdate(bookingId).orElse(null);
        if (financial == null) {
            financial = bookingFinancialRepository.save(financialMapper.toBookingFinancial(context, policySnapshot,
                    allocation.totalPrice(), allocation.totalDeposit(), BookingFinancialStatus.AWAITING_DEPOSIT));
        } else if (financial.getStatus() != BookingFinancialStatus.AWAITING_DEPOSIT) {
            throw new AppException(ErrorCode.PAYMENT_ALREADY_SUCCESS);
        } else {
            // Giá / chính sách có thể đổi giữa hai lần mở phiên trước khi khách trả — chụp lại.
            financialMapper.updatePricing(policySnapshot, allocation.totalPrice(), allocation.totalDeposit(), financial);
        }
        upsertOccurrenceFinancials(financial, allocation);
        Payment payment = paymentRepository.findByBookingIdAndPaymentType(bookingId, PaymentType.DEPOSIT).orElse(null);
        if (payment == null) {
            payment = paymentRepository.save(paymentMapper.toPayment(bookingId, context.getCustomerId(),
                    context.getCenterId(), PaymentType.DEPOSIT, null, allocation.totalDeposit(), CURRENCY,
                    PaymentStatus.PENDING));
        } else if (payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new AppException(ErrorCode.PAYMENT_ALREADY_SUCCESS);
        } else {
            payment.setAmount(allocation.totalDeposit());
            payment.setStatus(PaymentStatus.PENDING);
        }
        PaymentAttempt attempt = paymentCheckoutService.openAttempt(payment, request.getProvider(), expiresAt);
        return attempt.getId();
    }

    private void upsertOccurrenceFinancials(BookingFinancial financial, DepositAllocator.Result allocation) {
        Map<UUID, OccurrenceFinancial> existing = new HashMap<>();
        for (OccurrenceFinancial occurrenceFinancial : occurrenceFinancialRepository.findAllByBookingId(
                financial.getBookingId())) {
            existing.put(occurrenceFinancial.getOccurrenceId(), occurrenceFinancial);
        }
        Set<UUID> allocated = new HashSet<>();
        List<OccurrenceFinancial> created = new ArrayList<>();
        for (DepositAllocator.Allocation item : allocation.allocations()) {
            allocated.add(item.occurrenceId());
            OccurrenceFinancial occurrenceFinancial = existing.get(item.occurrenceId());
            if (occurrenceFinancial == null) {
                created.add(financialMapper.toOccurrenceFinancial(item, financial.getBookingId(), financial.getCenterId()));
            } else {
                financialMapper.updateAllocation(item, occurrenceFinancial);
            }
        }
        // Buổi đã bị hủy trước khi khách trả cọc không còn được phân bổ cọc.
        for (OccurrenceFinancial occurrenceFinancial : existing.values()) {
            if (!allocated.contains(occurrenceFinancial.getOccurrenceId())) {
                occurrenceFinancial.setDepositAmount(BigDecimal.ZERO);
            }
        }
        occurrenceFinancialRepository.saveAll(created);
    }

    private IpnOutcome applyVnPayIpn(Map<String, String> params) {
        String reference = params.get(VNP_TXN_REF);
        PaymentAttempt attempt = reference == null
                ? null
                : paymentAttemptRepository.findForUpdateByReference(reference).orElse(null);
        if (attempt == null) {
            log.warn("VNPay IPN for unknown reference {}", reference);
            return new IpnOutcome("01", "Order not found");
        }
        Payment payment = attempt.getPayment();
        String responseCode = params.get(VNP_RESPONSE_CODE);
        paymentTransactionService.record(payment.getBookingId(), payment.getId(), null, payment.getInvoiceId(),
                TransactionEventType.IPN, attempt.getAmount(), null, "reference=" + reference + " responseCode="
                        + responseCode + " transactionNo=" + params.get(VNP_TRANSACTION_NO), true);
        if (!matchesVnPayAmount(attempt, params.get(VNP_AMOUNT))) {
            log.warn("VNPay IPN amount mismatch for {}", reference);
            return new IpnOutcome("04", "Invalid amount");
        }
        if (attempt.getStatus() == PaymentStatus.SUCCESS) {
            return new IpnOutcome("02", "Order already confirmed");
        }
        boolean success = VNPAY_SUCCESS.equals(responseCode)
                && VNPAY_SUCCESS.equals(params.get(VNP_TRANSACTION_STATUS));
        if (attempt.getStatus() != PaymentStatus.PENDING) {
            if (!success) {
                return new IpnOutcome("02", "Order already confirmed");
            }
            // Phiên đã hết hạn / bị thay / thất bại mà VNPay vẫn báo đã thu tiền: trả trễ hoặc trả trùng.
            completeLatePayment(attempt, params.get(VNP_TRANSACTION_NO), responseCode);
            return new IpnOutcome(VNPAY_SUCCESS, "Confirm Success");
        }
        completeAttempt(attempt, success, params.get(VNP_TRANSACTION_NO), responseCode);
        return new IpnOutcome(VNPAY_SUCCESS, "Confirm Success");
    }

    private boolean matchesVnPayAmount(PaymentAttempt attempt, String rawAmount) {
        if (rawAmount == null) {
            return false;
        }
        try {
            return attempt.getAmount().movePointRight(2).compareTo(new BigDecimal(rawAmount)) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // Ghi nhận kết quả cuối của một phiên (IPN / webhook) — gọi trong transaction đã khóa phiên.
    private void completeAttempt(PaymentAttempt attempt, boolean success, String transactionNo, String responseCode) {
        Instant now = clock.instant();
        Payment payment = attempt.getPayment();
        attempt.setStatus(success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        attempt.setProviderTransactionId(transactionNo);
        attempt.setResponseCode(responseCode);
        attempt.setCompletedAt(now);
        if (!success) {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.FAILED);
            }
            paymentTransactionService.record(payment.getBookingId(), payment.getId(), null, payment.getInvoiceId(),
                    TransactionEventType.PAYMENT_FAILED, attempt.getAmount(), null, "responseCode=" + responseCode, null);
            log.info("Payment {} attempt {} failed ({})", payment.getId(), attempt.getReference(), responseCode);
            return;
        }
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(now);
        paymentTransactionService.record(payment.getBookingId(), payment.getId(), null, payment.getInvoiceId(),
                TransactionEventType.PAYMENT_SUCCEEDED, payment.getAmount(), null, "reference=" + attempt.getReference(),
                null);
        // Đã thu tiền: phiên khác còn mở của payment này không được thu thêm lần nữa.
        for (PaymentAttempt otherAttempt : paymentAttemptRepository.findAllByPaymentAndStatus(payment.getId(),
                PaymentStatus.PENDING)) {
            if (!otherAttempt.getId().equals(attempt.getId())) {
                otherAttempt.setStatus(PaymentStatus.CANCELLED);
                otherAttempt.setCompletedAt(now);
            }
        }
        if (payment.getPaymentType() == PaymentType.DEPOSIT) {
            bookingFinancialRepository.findForUpdate(payment.getBookingId()).ifPresent(financial -> {
                if (financial.getStatus() == BookingFinancialStatus.AWAITING_DEPOSIT) {
                    financial.setStatus(BookingFinancialStatus.DEPOSIT_PAID);
                    financial.setDepositPaymentId(payment.getId());
                    financial.setDepositPaidAt(now);
                }
            });
            paymentOutboxService.enqueueDepositConfirmation(payment, attempt.getReference());
        } else if (!remainingInvoiceService.markPaid(payment)) {
            refundService.refundWholePayment(payment.getId(), RefundReason.DUPLICATE);
        }
        log.info("Payment {} {} succeeded ({})", payment.getId(), payment.getPaymentType(), payment.getAmount());
    }

    /**
     * VNPay báo đã thu tiền cho phiên không còn PENDING. Payment chưa thu, cùng số tiền và booking / hóa đơn vẫn chờ trả
     * → nhận như thanh toán bình thường (Booking có thể từ chối → outbox hoàn BOOKING_NOT_CONFIRMABLE). Ngược lại tiền
     * của phiên này là tiền thừa → hoàn riêng đúng phiên đó: DUPLICATE (payment đã thu) hoặc LATE_PAYMENT.
     */
    private void completeLatePayment(PaymentAttempt attempt, String transactionNo, String responseCode) {
        Payment payment = attempt.getPayment();
        boolean acceptable = payment.getStatus() != PaymentStatus.SUCCESS
                && attempt.getAmount().compareTo(payment.getAmount()) == 0
                && isStillPayable(payment);
        if (acceptable) {
            log.warn("Late payment on attempt {} accepted for payment {}", attempt.getReference(), payment.getId());
            completeAttempt(attempt, true, transactionNo, responseCode);
            return;
        }
        attempt.setStatus(PaymentStatus.SUCCESS);
        attempt.setProviderTransactionId(transactionNo);
        attempt.setResponseCode(responseCode);
        attempt.setCompletedAt(clock.instant());
        RefundReason reason = payment.getStatus() == PaymentStatus.SUCCESS
                ? RefundReason.DUPLICATE
                : RefundReason.LATE_PAYMENT;
        paymentTransactionService.record(payment.getBookingId(), payment.getId(), null, payment.getInvoiceId(),
                TransactionEventType.PAYMENT_SUCCEEDED, attempt.getAmount(), null,
                "reference=" + attempt.getReference() + " separate=" + reason, null);
        refundService.refundSeparateAttempt(attempt, reason);
    }

    private boolean isStillPayable(Payment payment) {
        if (payment.getPaymentType() == PaymentType.DEPOSIT) {
            return bookingFinancialRepository.findById(payment.getBookingId())
                    .map(financial -> financial.getStatus() == BookingFinancialStatus.AWAITING_DEPOSIT)
                    .orElse(false);
        }
        return payment.getInvoiceId() != null;
    }

    private void completeStripe(Session session) {
        transactionTemplate.executeWithoutResult(status -> {
            PaymentAttempt attempt = paymentAttemptRepository.findForUpdateByCheckoutId(session.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
            boolean amountMatches = session.getAmountTotal() != null
                    && attempt.getAmount().compareTo(BigDecimal.valueOf(session.getAmountTotal())) == 0
                    && CURRENCY.equalsIgnoreCase(session.getCurrency())
                    && STRIPE_PAID.equals(session.getPaymentStatus());
            if (!amountMatches) {
                throw new AppException(ErrorCode.AMOUNT_MISMATCH);
            }
            if (attempt.getStatus() == PaymentStatus.PENDING) {
                completeAttempt(attempt, true, session.getPaymentIntent(), STRIPE_PAID);
            }
        });
    }

    private void expireStripe(String sessionId) {
        PaymentAttempt attempt = paymentAttemptRepository.findForUpdateByCheckoutId(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        if (attempt.getStatus() == PaymentStatus.PENDING) {
            attempt.setStatus(PaymentStatus.EXPIRED);
            attempt.setCompletedAt(clock.instant());
        }
    }

    // Đọc lẻ ngoài phạm vi → 404 (không lộ giao dịch của người khác có tồn tại).
    private void checkReadScope(Payment payment, UUID actorId, Set<RoleCode> roles, UUID centerId) {
        if (roles.contains(RoleCode.ADMIN) || roles.contains(RoleCode.SERVICE)) {
            return;
        }
        if (roles.contains(RoleCode.CUSTOMER) && payment.getCustomerId().equals(actorId)) {
            return;
        }
        if (roles.contains(RoleCode.CENTER_MANAGER)) {
            checkCenterClaim(centerId);
            if (payment.getCenterId().equals(centerId)) {
                return;
            }
        }
        throw new AppException(ErrorCode.PAYMENT_NOT_FOUND);
    }

    private void checkCenterClaim(UUID centerId) {
        if (centerId == null) {
            throw new AppException(ErrorCode.CENTER_SCOPE_REQUIRED);
        }
    }

    private Specification<Payment> buildListSpecification(UUID actorId, Set<RoleCode> roles, UUID centerId,
                                                           UUID bookingId, PaymentStatus status, PaymentType paymentType) {
        UUID scopeCustomerId;
        UUID scopeCenterId;
        if (roles.contains(RoleCode.ADMIN) || roles.contains(RoleCode.SERVICE)) {
            scopeCustomerId = null;
            scopeCenterId = null;
        } else if (roles.contains(RoleCode.CENTER_MANAGER)) {
            checkCenterClaim(centerId);
            scopeCustomerId = null;
            scopeCenterId = centerId;
        } else if (roles.contains(RoleCode.CUSTOMER)) {
            scopeCustomerId = actorId;
            scopeCenterId = null;
        } else {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (scopeCustomerId != null) {
                predicates.add(criteriaBuilder.equal(root.get("customerId"), scopeCustomerId));
            }
            if (scopeCenterId != null) {
                predicates.add(criteriaBuilder.equal(root.get("centerId"), scopeCenterId));
            }
            if (bookingId != null) {
                predicates.add(criteriaBuilder.equal(root.get("bookingId"), bookingId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (paymentType != null) {
                predicates.add(criteriaBuilder.equal(root.get("paymentType"), paymentType));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private VnPayIpnResponse ipnResponse(String responseCode, String message) {
        return VnPayIpnResponse.builder()
                .responseCode(responseCode)
                .message(message)
                .build();
    }

    private record IpnOutcome(String responseCode, String message) {
    }

    private record StaleAttempt(PaymentProvider provider, String reference, Instant createdAt, Instant expiresAt) {
    }
}
