package mss301.paymentservice.service.Impl;

import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.config.PaymentProperties;
import mss301.paymentservice.dto.request.CreateCheckoutRequest;
import mss301.paymentservice.dto.request.RemainingInvoiceRequest;
import mss301.paymentservice.dto.request.WaiveInvoiceRequest;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.CheckoutResponse;
import mss301.paymentservice.dto.response.OutstandingDebtResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.dto.response.PolicySnapshot;
import mss301.paymentservice.dto.response.RemainingInvoiceResponse;
import mss301.paymentservice.dto.response.RemainingInvoiceResultResponse;
import mss301.paymentservice.entity.BookingFinancial;
import mss301.paymentservice.entity.OccurrenceFinancial;
import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.entity.PaymentAttempt;
import mss301.paymentservice.entity.RemainingInvoice;
import mss301.paymentservice.enums.BookingFinancialStatus;
import mss301.paymentservice.enums.InvoiceStatus;
import mss301.paymentservice.enums.OccurrenceFinalStatus;
import mss301.paymentservice.enums.OccurrenceStatus;
import mss301.paymentservice.enums.PaymentStatus;
import mss301.paymentservice.enums.PaymentType;
import mss301.paymentservice.enums.RoleCode;
import mss301.paymentservice.enums.TransactionEventType;
import mss301.paymentservice.exception.AppException;
import mss301.paymentservice.exception.ErrorCode;
import mss301.paymentservice.mapper.PaymentMapper;
import mss301.paymentservice.mapper.RemainingInvoiceMapper;
import mss301.paymentservice.repository.BookingFinancialRepository;
import mss301.paymentservice.repository.OccurrenceFinancialRepository;
import mss301.paymentservice.repository.PaymentRepository;
import mss301.paymentservice.repository.RemainingInvoiceRepository;
import mss301.paymentservice.service.PaymentCheckoutService;
import mss301.paymentservice.service.PaymentTransactionService;
import mss301.paymentservice.service.RemainingInvoiceService;
import mss301.paymentservice.util.VndMoney;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
public class RemainingInvoiceServiceImpl implements RemainingInvoiceService {
    static Set<InvoiceStatus> OPEN_STATUSES = EnumSet.of(InvoiceStatus.PENDING, InvoiceStatus.OVERDUE);
    static String CURRENCY = "VND";
    static int MAX_PAGE_SIZE = 100;
    static String DUE_AT = "dueAt";

    RemainingInvoiceRepository remainingInvoiceRepository;
    BookingFinancialRepository bookingFinancialRepository;
    OccurrenceFinancialRepository occurrenceFinancialRepository;
    PaymentRepository paymentRepository;
    PaymentCheckoutService paymentCheckoutService;
    PaymentTransactionService paymentTransactionService;
    RemainingInvoiceMapper remainingInvoiceMapper;
    PaymentMapper paymentMapper;
    PaymentProperties paymentProperties;
    TransactionTemplate transactionTemplate;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<RemainingInvoiceResponse>> list(UUID actorId, Set<RoleCode> roles, UUID customerId,
                                                                    InvoiceStatus status, int page, int size) {
        UUID scopeCustomerId = roles.contains(RoleCode.ADMIN) ? customerId : actorId;
        Specification<RemainingInvoice> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (scopeCustomerId != null) {
                predicates.add(criteriaBuilder.equal(root.get("customerId"), scopeCustomerId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, DUE_AT));
        Page<RemainingInvoice> invoices = remainingInvoiceRepository.findAll(specification, pageRequest);
        PageResponse<RemainingInvoiceResponse> pageResponse = PageResponse.of(invoices,
                remainingInvoiceMapper.toResponseList(invoices.getContent()));
        return APIResponse.success("Lấy danh sách hóa đơn thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<OutstandingDebtResponse> getOutstanding(UUID customerId) {
        List<RemainingInvoice> overdue = remainingInvoiceRepository.findAllByCustomerIdAndStatusOrderByDueAtAsc(customerId,
                InvoiceStatus.OVERDUE);
        List<RemainingInvoice> pending = remainingInvoiceRepository.findAllByCustomerIdAndStatusOrderByDueAtAsc(customerId,
                InvoiceStatus.PENDING);
        OutstandingDebtResponse outstandingDebtResponse = remainingInvoiceMapper.toOutstandingResponse(!overdue.isEmpty(),
                overdue, pending);
        return APIResponse.success("Kiểm tra công nợ thành công", outstandingDebtResponse);
    }

    @Override
    @Transactional
    public APIResponse<RemainingInvoiceResultResponse> create(String idempotencyKey, RemainingInvoiceRequest request) {
        UUID bookingId = request.getBookingId();
        BookingFinancial financial = bookingFinancialRepository.findForUpdate(bookingId).orElse(null);
        if (financial == null || financial.getStatus() == BookingFinancialStatus.AWAITING_DEPOSIT) {
            log.warn("Remaining invoice for booking {} not required: deposit never paid", bookingId);
            return notRequired(bookingId);
        }
        RemainingInvoice existing = remainingInvoiceRepository.findByBookingId(bookingId).orElse(null);
        if (existing != null) {
            RemainingInvoiceResultResponse resultResponse = remainingInvoiceMapper.toResultResponse(existing);
            return APIResponse.success("Hóa đơn phần còn lại đã tồn tại", resultResponse);
        }
        if (financial.getStatus() == BookingFinancialStatus.CLOSED) {
            return notRequired(bookingId);
        }
        Map<UUID, OccurrenceFinancial> occurrenceFinancials = new HashMap<>();
        for (OccurrenceFinancial occurrenceFinancial : occurrenceFinancialRepository.findAllByBookingId(bookingId)) {
            occurrenceFinancials.put(occurrenceFinancial.getOccurrenceId(), occurrenceFinancial);
        }
        PolicySnapshot policy = financial.getPolicySnapshot();
        BigDecimal remaining = BigDecimal.ZERO;
        for (RemainingInvoiceRequest.Occurrence occurrence : request.getOccurrences()) {
            OccurrenceFinalStatus finalStatus = toFinalStatus(occurrence.getFinalStatus());
            OccurrenceFinancial occurrenceFinancial = occurrenceFinancials.get(occurrence.getOccurrenceId());
            if (finalStatus == null || occurrenceFinancial == null) {
                log.warn("Remaining invoice {}: occurrence {} ({}) skipped", bookingId, occurrence.getOccurrenceId(),
                        occurrence.getFinalStatus());
                continue;
            }
            occurrenceFinancial.setFinalStatus(finalStatus);
            BigDecimal extra = chargeOf(occurrenceFinancial, policy).subtract(occurrenceFinancial.getDepositAmount());
            remaining = remaining.add(VndMoney.nonNegative(extra));
        }
        if (remaining.signum() == 0) {
            financial.setStatus(BookingFinancialStatus.CLOSED);
            log.info("Booking {} closed without remaining invoice", bookingId);
            return notRequired(bookingId);
        }
        Instant dueAt = clock.instant().plus(Duration.ofHours(policy.getRemainingDueHours()));
        RemainingInvoice invoice = remainingInvoiceRepository.save(remainingInvoiceMapper.toInvoice(financial, remaining,
                dueAt, InvoiceStatus.PENDING));
        paymentTransactionService.record(bookingId, null, null, invoice.getId(), TransactionEventType.INVOICE_CREATED,
                remaining, idempotencyKey, "dueAt=" + dueAt, null);
        log.info("Remaining invoice {} for booking {}: {}", invoice.getId(), bookingId, remaining);
        RemainingInvoiceResultResponse resultResponse = remainingInvoiceMapper.toResultResponse(invoice);
        return APIResponse.success("Đã tạo hóa đơn phần còn lại", resultResponse);
    }

    @Override
    public APIResponse<CheckoutResponse> pay(UUID customerId, UUID invoiceId, CreateCheckoutRequest request,
                                             HttpServletRequest httpServletRequest) {
        paymentCheckoutService.checkProviderEnabled(request.getProvider());
        PaymentAttempt attempt = transactionTemplate.execute(status -> openRemainingAttempt(customerId, invoiceId,
                request));
        String clientIp = paymentCheckoutService.resolveClientIp(httpServletRequest);
        CheckoutResponse checkoutResponse = paymentCheckoutService.launch(attempt.getId(), "hoa don " + invoiceId,
                clientIp);
        return APIResponse.success("Tạo phiên thanh toán thành công", checkoutResponse);
    }

    @Override
    @Transactional
    public APIResponse<RemainingInvoiceResponse> waive(UUID actorId, UUID invoiceId, WaiveInvoiceRequest request) {
        RemainingInvoice invoice = remainingInvoiceRepository.findForUpdate(invoiceId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));
        if (!OPEN_STATUSES.contains(invoice.getStatus())) {
            throw new AppException(ErrorCode.INVOICE_NOT_WAIVABLE);
        }
        invoice.setStatus(InvoiceStatus.WAIVED);
        invoice.setWaivedBy(actorId);
        invoice.setWaiveReason(request.getReason().trim());
        bookingFinancialRepository.findForUpdate(invoice.getBookingId())
                .ifPresent(financial -> financial.setStatus(BookingFinancialStatus.CLOSED));
        paymentTransactionService.record(invoice.getBookingId(), null, null, invoice.getId(),
                TransactionEventType.INVOICE_WAIVED, invoice.getAmount(), null, "by=" + actorId, null);
        log.info("Remaining invoice {} waived by {}", invoiceId, actorId);
        RemainingInvoiceResponse remainingInvoiceResponse = remainingInvoiceMapper.toResponse(invoice);
        return APIResponse.success("Đã miễn hóa đơn", remainingInvoiceResponse);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markPaid(Payment payment) {
        RemainingInvoice invoice = payment.getInvoiceId() == null
                ? null
                : remainingInvoiceRepository.findForUpdate(payment.getInvoiceId()).orElse(null);
        if (invoice == null || !OPEN_STATUSES.contains(invoice.getStatus())) {
            // Hóa đơn đã miễn / đã trả bằng phiên khác — caller hoàn tiền thừa (DUPLICATE).
            log.warn("Payment {} succeeded for invoice {} that is not open", payment.getId(), payment.getInvoiceId());
            return false;
        }
        Instant now = clock.instant();
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(now);
        invoice.setPaymentId(payment.getId());
        paymentTransactionService.record(invoice.getBookingId(), payment.getId(), null, invoice.getId(),
                TransactionEventType.INVOICE_PAID, payment.getAmount(), null, null, null);
        bookingFinancialRepository.findForUpdate(invoice.getBookingId()).ifPresent(financial -> {
            financial.setStatus(BookingFinancialStatus.CLOSED);
            for (OccurrenceFinancial occurrenceFinancial : occurrenceFinancialRepository.findAllByBookingId(
                    invoice.getBookingId())) {
                BigDecimal extra = chargeOf(occurrenceFinancial, financial.getPolicySnapshot())
                        .subtract(occurrenceFinancial.getDepositAmount());
                occurrenceFinancial.setChargedAmount(VndMoney.nonNegative(extra));
            }
        });
        return true;
    }

    @Override
    public List<UUID> findOverdueCandidateIds() {
        return remainingInvoiceRepository.findDueIds(InvoiceStatus.PENDING, clock.instant(),
                PageRequest.of(0, paymentProperties.getInvoice().getBatchSize()));
    }

    @Override
    @Transactional
    public void markOverdue(UUID invoiceId) {
        RemainingInvoice invoice = remainingInvoiceRepository.findForUpdateSkipLocked(invoiceId).orElse(null);
        if (invoice == null || invoice.getStatus() != InvoiceStatus.PENDING || invoice.getDueAt().isAfter(clock.instant())) {
            return;
        }
        invoice.setStatus(InvoiceStatus.OVERDUE);
        paymentTransactionService.record(invoice.getBookingId(), null, null, invoice.getId(),
                TransactionEventType.INVOICE_OVERDUE, invoice.getAmount(), null, null, null);
        log.info("Remaining invoice {} of customer {} is overdue", invoice.getId(), invoice.getCustomerId());
    }

    private PaymentAttempt openRemainingAttempt(UUID customerId, UUID invoiceId, CreateCheckoutRequest request) {
        RemainingInvoice invoice = remainingInvoiceRepository.findForUpdate(invoiceId)
                .filter(found -> found.getCustomerId().equals(customerId))
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));
        if (!OPEN_STATUSES.contains(invoice.getStatus()) || invoice.getAmount().signum() <= 0) {
            throw new AppException(ErrorCode.INVOICE_NOT_PAYABLE);
        }
        Payment payment = paymentRepository.findByBookingIdAndPaymentType(invoice.getBookingId(), PaymentType.REMAINING)
                .orElse(null);
        if (payment == null) {
            payment = paymentRepository.save(paymentMapper.toPayment(invoice.getBookingId(), invoice.getCustomerId(),
                    invoice.getCenterId(), PaymentType.REMAINING, invoice.getId(), invoice.getAmount(), CURRENCY,
                    PaymentStatus.PENDING));
        } else if (payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new AppException(ErrorCode.INVOICE_NOT_PAYABLE);
        } else {
            // Số hóa đơn có thể đã giảm (khiếu nại) giữa hai lần mở phiên.
            payment.setAmount(invoice.getAmount());
            payment.setStatus(PaymentStatus.PENDING);
        }
        invoice.setPaymentId(payment.getId());
        int ttlMinutes = bookingFinancialRepository.findById(invoice.getBookingId())
                .map(financial -> financial.getPolicySnapshot().getPaymentSessionTtlMinutes())
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_PAYABLE));
        Instant expiresAt = clock.instant().plus(Duration.ofMinutes(ttlMinutes));
        return paymentCheckoutService.openAttempt(payment, request.getProvider(), expiresAt);
    }

    // COMPLETED tính đủ giá, khách vắng tính % theo snapshot; buổi hủy / MISSED không tính tiền.
    private BigDecimal chargeOf(OccurrenceFinancial occurrenceFinancial, PolicySnapshot policy) {
        if (occurrenceFinancial.getFinalStatus() == OccurrenceFinalStatus.COMPLETED) {
            return occurrenceFinancial.getPriceAmount();
        }
        if (occurrenceFinancial.getFinalStatus() == OccurrenceFinalStatus.CUSTOMER_NO_SHOW) {
            return VndMoney.percentOf(policy.getCustomerNoShowChargePercent(), occurrenceFinancial.getPriceAmount());
        }
        return BigDecimal.ZERO;
    }

    private OccurrenceFinalStatus toFinalStatus(OccurrenceStatus status) {
        return switch (status) {
            case COMPLETED -> OccurrenceFinalStatus.COMPLETED;
            case CANCELLED -> OccurrenceFinalStatus.CANCELLED;
            case MISSED -> OccurrenceFinalStatus.MISSED;
            case CUSTOMER_NO_SHOW -> OccurrenceFinalStatus.CUSTOMER_NO_SHOW;
            case SCHEDULED, ACKNOWLEDGED, IN_PROGRESS -> null;
        };
    }

    private APIResponse<RemainingInvoiceResultResponse> notRequired(UUID bookingId) {
        RemainingInvoiceResultResponse resultResponse = remainingInvoiceMapper.toNotRequiredResponse(bookingId);
        return APIResponse.success("Không phát sinh hóa đơn phần còn lại", resultResponse);
    }
}
