package mss301.paymentservice.service.Impl;

import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.client.BookingClient;
import mss301.paymentservice.client.dto.BookingPaymentContextResponse;
import mss301.paymentservice.client.dto.RemoteApiResponse;
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
import mss301.paymentservice.service.PaymentOutboxService;
import mss301.paymentservice.service.PaymentTransactionService;
import mss301.paymentservice.service.RefundService;
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
import java.util.Collection;
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
    PaymentOutboxService paymentOutboxService;
    RefundService refundService;
    RemainingInvoiceMapper remainingInvoiceMapper;
    PaymentMapper paymentMapper;
    PaymentProperties paymentProperties;
    BookingClient bookingClient;
    TransactionTemplate transactionTemplate;
    Clock clock;

    // Danh sách hóa đơn phần còn lại; khách xem của mình, admin lọc theo khách.
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

    // Tổng nợ quá hạn của khách hàng.
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

    // Tạo hóa đơn phần còn lại khi booking xong (idempotent); không còn nợ thì không phát sinh.
    @Override
    @Transactional
    public APIResponse<RemainingInvoiceResultResponse> create(String idempotencyKey, RemainingInvoiceRequest request) {
        UUID bookingId = request.getBookingId();
        BookingFinancial financial = bookingFinancialRepository.findForUpdate(bookingId).orElse(null);
        if (financial == null || financial.getStatus() == BookingFinancialStatus.AWAITING_DEPOSIT) {
            log.warn("Remaining invoice for booking {} not required: deposit never paid", bookingId);
            return notRequired(bookingId);
        }
        if (financial.getRemainingDueAt() != null) {
            // Spec v1 §9.1: booking mới trả đủ trước buổi đầu (openBeforeService), không thu sau hoàn tất.
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

    // Mở phiên thanh toán cho hóa đơn phần còn lại.
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

    // Miễn thu hóa đơn phần còn lại kèm lý do.
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

    // Mở hóa đơn phần còn lại trước buổi chăm sóc khi tới hạn thu.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void openBeforeService(BookingFinancial financial) {
        UUID bookingId = financial.getBookingId();
        if (financial.getRemainingDueAt() == null || remainingInvoiceRepository.findByBookingId(bookingId).isPresent()) {
            return;
        }
        BigDecimal remaining = unpaidBeforeService(occurrenceFinancialRepository.findAllByBookingId(bookingId));
        if (remaining.signum() <= 0) {
            return;
        }
        RemainingInvoice invoice = remainingInvoiceRepository.save(remainingInvoiceMapper.toInvoice(financial, remaining,
                financial.getRemainingDueAt(), InvoiceStatus.PENDING));
        paymentTransactionService.record(bookingId, null, null, invoice.getId(), TransactionEventType.INVOICE_CREATED,
                remaining, null, "dueAt=" + invoice.getDueAt() + " beforeService=true", null);
        log.info("Remaining invoice {} for booking {} due before the first visit: {}", invoice.getId(), bookingId, remaining);
    }

    // Đánh dấu hóa đơn đã trả khi thanh toán phần còn lại thành công.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markPaid(Payment payment, String paymentReference) {
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
            boolean beforeService = financial.getRemainingDueAt() != null;
            for (OccurrenceFinancial occurrenceFinancial : occurrenceFinancialRepository.findAllByBookingId(
                    invoice.getBookingId())) {
                if (beforeService) {
                    // Trả trước buổi đầu: mỗi buổi còn hiệu lực đã trả đủ giá (buổi đã hủy giữ nguyên).
                    if (occurrenceFinancial.getFinalStatus() == null) {
                        occurrenceFinancial.setChargedAmount(unpaidOf(occurrenceFinancial)
                                .add(occurrenceFinancial.getChargedAmount()));
                    }
                    continue;
                }
                BigDecimal extra = chargeOf(occurrenceFinancial, financial.getPolicySnapshot())
                        .subtract(occurrenceFinancial.getDepositAmount());
                occurrenceFinancial.setChargedAmount(VndMoney.nonNegative(extra));
            }
            if (beforeService) {
                paymentOutboxService.enqueueRemainingConfirmation(payment, paymentReference);
            }
        });
        return true;
    }

    // Lấy id hóa đơn đang chờ đã quá hạn.
    @Override
    public List<UUID> findOverdueCandidateIds() {
        return remainingInvoiceRepository.findDueIds(InvoiceStatus.PENDING, clock.instant(),
                PageRequest.of(0, paymentProperties.getInvoice().getBatchSize()));
    }

    // Không @Transactional: gọi Booking qua mạng trước, ghi bằng transaction ngắn (rule #24).
    @Override
    public void refreshCaregivers(UUID invoiceId) {
        RemainingInvoice invoice = remainingInvoiceRepository.findById(invoiceId).orElse(null);
        if (invoice == null || invoice.getStatus() != InvoiceStatus.PENDING) {
            return;
        }
        UUID bookingId = invoice.getBookingId();
        BookingPaymentContextResponse context;
        try {
            RemoteApiResponse<BookingPaymentContextResponse> response = bookingClient.getPaymentContext(bookingId);
            context = response == null ? null : response.getResult();
        } catch (RuntimeException e) {
            log.warn("Caregivers of booking {} not refreshed before the overdue check ({})", bookingId,
                    e.getClass().getSimpleName());
            return;
        }
        if (context == null || context.getOccurrences() == null) {
            return;
        }
        Map<UUID, BookingPaymentContextResponse.Occurrence> contextOccurrences = new HashMap<>();
        for (BookingPaymentContextResponse.Occurrence occurrence : context.getOccurrences()) {
            contextOccurrences.put(occurrence.getOccurrenceId(), occurrence);
        }
        transactionTemplate.executeWithoutResult(status -> {
            // Cùng thứ tự khóa với mọi lệnh tiền của booking.
            if (bookingFinancialRepository.findForUpdate(bookingId).isEmpty()) {
                return;
            }
            for (OccurrenceFinancial occurrenceFinancial : occurrenceFinancialRepository.findAllByBookingId(bookingId)) {
                BookingPaymentContextResponse.Occurrence occurrence = contextOccurrences.get(
                        occurrenceFinancial.getOccurrenceId());
                if (occurrence != null && occurrenceFinancial.getFinalStatus() == null) {
                    occurrenceFinancial.assignCaregiver(occurrence.getCaregiverId(), occurrence.getCaregiverUserId());
                }
            }
        });
    }

    // Đánh dấu hóa đơn quá hạn và báo BookingService.
    @Override
    @Transactional
    public void markOverdue(UUID invoiceId) {
        RemainingInvoice invoice = remainingInvoiceRepository.findForUpdateSkipLocked(invoiceId).orElse(null);
        if (invoice == null || invoice.getStatus() != InvoiceStatus.PENDING || invoice.getDueAt().isAfter(clock.instant())) {
            return;
        }
        BookingFinancial financial = bookingFinancialRepository.findForUpdate(invoice.getBookingId()).orElse(null);
        if (financial != null && financial.getRemainingDueAt() != null) {
            expireBeforeService(invoice, financial);
            return;
        }
        invoice.setStatus(InvoiceStatus.OVERDUE);
        paymentTransactionService.record(invoice.getBookingId(), null, null, invoice.getId(),
                TransactionEventType.INVOICE_OVERDUE, invoice.getAmount(), null, null, null);
        log.info("Remaining invoice {} of customer {} is overdue", invoice.getId(), invoice.getCustomerId());
    }

    /**
     * M1, M3: quá hạn trả phần còn lại trước buổi đầu → hóa đơn EXPIRED (không phải nợ), giữ cọc theo policy đã snapshot,
     * hoàn phần dư, báo Booking chuyển EXPIRED. Hóa đơn đã khóa — IPN trả phần còn lại khóa cùng dòng nên không đua: trả
     * trước thì không quá hạn, trả sau thì hóa đơn không còn mở → hoàn lại khoản vừa thu.
     */
    private void expireBeforeService(RemainingInvoice invoice, BookingFinancial financial) {
        invoice.setStatus(InvoiceStatus.EXPIRED);
        paymentTransactionService.record(invoice.getBookingId(), null, null, invoice.getId(),
                TransactionEventType.INVOICE_OVERDUE, invoice.getAmount(), null, "beforeService=true", null);
        refundService.refundOverdueForfeit(financial);
        paymentRepository.findByBookingIdAndPaymentType(invoice.getBookingId(), PaymentType.DEPOSIT)
                .filter(deposit -> deposit.getStatus() == PaymentStatus.SUCCESS)
                .ifPresent(deposit -> paymentOutboxService.enqueuePaymentOverdue(deposit, invoice.getId()));
        log.info("Remaining invoice {} expired before service, booking {} expires", invoice.getId(),
                invoice.getBookingId());
    }

    // Khóa hóa đơn của khách và mở lần thanh toán mới.
    private PaymentAttempt openRemainingAttempt(UUID customerId, UUID invoiceId, CreateCheckoutRequest request) {
        RemainingInvoice invoice = remainingInvoiceRepository.findForUpdate(invoiceId)
                .filter(found -> found.getCustomerId().equals(customerId))
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));
        if (!OPEN_STATUSES.contains(invoice.getStatus()) || invoice.getAmount().signum() <= 0) {
            throw new AppException(ErrorCode.INVOICE_NOT_PAYABLE);
        }
        BookingFinancial financial = bookingFinancialRepository.findById(invoice.getBookingId())
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_PAYABLE));
        Instant now = clock.instant();
        // Hóa đơn trước buổi đầu chỉ trả được tới hạn (job quá hạn có thể chưa chạy tới).
        boolean beforeService = financial.getRemainingDueAt() != null;
        if (beforeService && !now.isBefore(invoice.getDueAt())) {
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
        Instant expiresAt = now.plus(Duration.ofMinutes(financial.getPolicySnapshot().getPaymentSessionTtlMinutes()));
        if (beforeService && invoice.getDueAt().isBefore(expiresAt)) {
            expiresAt = invoice.getDueAt();
        }
        return paymentCheckoutService.openAttempt(payment, request.getProvider(), expiresAt);
    }

    // Phần chưa trả trước buổi đầu = Σ (giá − cọc − đã thu thêm) của buổi còn hiệu lực (chưa có kết cục).
    static BigDecimal unpaidBeforeService(Collection<OccurrenceFinancial> occurrenceFinancials) {
        BigDecimal unpaid = BigDecimal.ZERO;
        for (OccurrenceFinancial occurrenceFinancial : occurrenceFinancials) {
            if (occurrenceFinancial.getFinalStatus() == null) {
                unpaid = unpaid.add(unpaidOf(occurrenceFinancial));
            }
        }
        return unpaid;
    }

    // Số tiền chưa trả của một buổi (giá − cọc − đã thu).
    private static BigDecimal unpaidOf(OccurrenceFinancial occurrenceFinancial) {
        return VndMoney.nonNegative(occurrenceFinancial.getPriceAmount()
                .subtract(occurrenceFinancial.getDepositAmount())
                .subtract(occurrenceFinancial.getChargedAmount()));
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

    // Đổi trạng thái buổi sang trạng thái tài chính cuối.
    private OccurrenceFinalStatus toFinalStatus(OccurrenceStatus status) {
        return switch (status) {
            case COMPLETED -> OccurrenceFinalStatus.COMPLETED;
            // Buổi hết hạn (booking chưa thanh toán xong) cũng không tính tiền như buổi hủy.
            case CANCELLED, EXPIRED -> OccurrenceFinalStatus.CANCELLED;
            // OccurrenceFinalStatus là dữ liệu riêng của Payment (occurrence_financials) — giữ tên MISSED.
            case CAREGIVER_MISSED -> OccurrenceFinalStatus.MISSED;
            case CUSTOMER_NO_SHOW -> OccurrenceFinalStatus.CUSTOMER_NO_SHOW;
            case PENDING_ASSIGNMENT, RESERVED, SCHEDULED, ACKNOWLEDGED, IN_PROGRESS -> null;
        };
    }

    // Response khi booking không phát sinh hóa đơn phần còn lại.
    private APIResponse<RemainingInvoiceResultResponse> notRequired(UUID bookingId) {
        RemainingInvoiceResultResponse resultResponse = remainingInvoiceMapper.toNotRequiredResponse(bookingId);
        return APIResponse.success("Không phát sinh hóa đơn phần còn lại", resultResponse);
    }
}
