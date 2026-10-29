package mss301.paymentservice.service.Impl;

import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.dto.request.ComplaintRefundRequest;
import mss301.paymentservice.dto.request.RefundCreateRequest;
import mss301.paymentservice.dto.request.RefundQuoteRequest;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.dto.response.RefundCreateResponse;
import mss301.paymentservice.dto.response.RefundItemResponse;
import mss301.paymentservice.dto.response.RefundQuoteResponse;
import mss301.paymentservice.dto.response.RefundResponse;
import mss301.paymentservice.entity.BookingFinancial;
import mss301.paymentservice.entity.OccurrenceFinancial;
import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.entity.PaymentAttempt;
import mss301.paymentservice.entity.Refund;
import mss301.paymentservice.entity.RemainingInvoice;
import mss301.paymentservice.enums.BookingFinancialStatus;
import mss301.paymentservice.enums.BookingStatus;
import mss301.paymentservice.enums.CancelledBy;
import mss301.paymentservice.enums.InvoiceStatus;
import mss301.paymentservice.enums.OccurrenceFinalStatus;
import mss301.paymentservice.enums.PaymentStatus;
import mss301.paymentservice.enums.PaymentType;
import mss301.paymentservice.enums.RefundReason;
import mss301.paymentservice.enums.RefundStatus;
import mss301.paymentservice.enums.RoleCode;
import mss301.paymentservice.enums.TransactionEventType;
import mss301.paymentservice.exception.AppException;
import mss301.paymentservice.exception.ErrorCode;
import mss301.paymentservice.mapper.RefundMapper;
import mss301.paymentservice.repository.BookingFinancialRepository;
import mss301.paymentservice.repository.OccurrenceFinancialRepository;
import mss301.paymentservice.repository.PaymentRepository;
import mss301.paymentservice.repository.PaymentTransactionRepository;
import mss301.paymentservice.repository.RefundRepository;
import mss301.paymentservice.repository.RemainingInvoiceRepository;
import mss301.paymentservice.service.PaymentTransactionService;
import mss301.paymentservice.service.RefundService;
import mss301.paymentservice.util.RefundCalculator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
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
public class RefundServiceImpl implements RefundService {
    // Tiền đang giữ chỗ trên payment: chưa chuyển xong (PENDING) hoặc lỗi chờ admin thử lại (FAILED — vẫn nợ khách).
    static Set<RefundStatus> RESERVED_STATUSES = EnumSet.of(RefundStatus.REFUND_PENDING, RefundStatus.FAILED);
    static Set<BookingStatus> UNPAID_BOOKING_STATUSES = EnumSet.of(BookingStatus.PENDING, BookingStatus.AWAITING_DEPOSIT);
    static Set<InvoiceStatus> OPEN_INVOICE_STATUSES = EnumSet.of(InvoiceStatus.PENDING, InvoiceStatus.OVERDUE);
    static String COMPLAINT_KEY_PREFIX = "COMPLAINT_REFUND:";
    static String KEY_SEPARATOR = ":";
    static String COMPLAINT_WAIVE_REASON = "complaint refund";
    static int MAX_KEY_LENGTH = 150;
    static int MAX_PAGE_SIZE = 100;
    static String CREATED_AT = "createdAt";

    RefundRepository refundRepository;
    BookingFinancialRepository bookingFinancialRepository;
    OccurrenceFinancialRepository occurrenceFinancialRepository;
    PaymentRepository paymentRepository;
    RemainingInvoiceRepository remainingInvoiceRepository;
    PaymentTransactionRepository paymentTransactionRepository;
    PaymentTransactionService paymentTransactionService;
    RefundMapper refundMapper;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<RefundResponse>> list(UUID actorId, Set<RoleCode> roles, UUID bookingId,
                                                          RefundStatus status, int page, int size) {
        UUID scopeCustomerId = roles.contains(RoleCode.ADMIN) || roles.contains(RoleCode.SERVICE) ? null : actorId;
        Specification<Refund> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (scopeCustomerId != null) {
                predicates.add(criteriaBuilder.equal(root.get("customerId"), scopeCustomerId));
            }
            if (bookingId != null) {
                predicates.add(criteriaBuilder.equal(root.get("bookingId"), bookingId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, CREATED_AT));
        Page<Refund> refunds = refundRepository.findAll(specification, pageRequest);
        PageResponse<RefundResponse> pageResponse = PageResponse.of(refunds,
                refundMapper.toResponseList(refunds.getContent()));
        return APIResponse.success("Lấy danh sách hoàn tiền thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<RefundQuoteResponse> quote(RefundQuoteRequest request) {
        BookingFinancial financial = bookingFinancialRepository.findById(request.getBookingId()).orElse(null);
        Map<UUID, OccurrenceFinancial> occurrenceFinancials = findOccurrenceFinancials(financial);
        boolean paid = isDepositPaid(financial) && !UNPAID_BOOKING_STATUSES.contains(request.getBookingStatus());
        RefundCalculator.Context context = new RefundCalculator.Context(paid,
                financial == null ? null : financial.getPolicySnapshot(), request.getReason(), request.getCancelledAt(),
                request.getFirstUpcomingStartAt(), financial == null ? null : financial.getDepositPaidAt());
        List<RefundCalculator.OccurrenceInput> inputs = new ArrayList<>();
        for (RefundQuoteRequest.Occurrence occurrence : request.getOccurrences()) {
            OccurrenceFinancial occurrenceFinancial = occurrenceFinancials.get(occurrence.getOccurrenceId());
            Instant startAt = occurrence.getStartAt() != null || occurrenceFinancial == null
                    ? occurrence.getStartAt()
                    : occurrenceFinancial.getStartAt();
            inputs.add(new RefundCalculator.OccurrenceInput(occurrence.getOccurrenceId(), startAt, occurrence.getStatus(),
                    occurrence.isHasSession(), priceOf(occurrenceFinancial), paidAmountOf(occurrenceFinancial)));
        }
        List<RefundCalculator.Line> lines = RefundCalculator.calculate(context, inputs);
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalFee = BigDecimal.ZERO;
        BigDecimal totalRefund = BigDecimal.ZERO;
        for (RefundCalculator.Line line : lines) {
            totalPaid = totalPaid.add(line.paidAmount());
            totalFee = totalFee.add(line.feeAmount());
            totalRefund = totalRefund.add(line.refundAmount());
        }
        UUID policyId = financial == null ? null : financial.getPolicyId();
        RefundQuoteResponse refundQuoteResponse = refundMapper.toQuoteResponse(refundMapper.toQuoteItemList(lines),
                totalPaid, totalFee, totalRefund, policyId);
        return APIResponse.success("Tính tiền hoàn thành công", refundQuoteResponse);
    }

    @Override
    @Transactional
    public APIResponse<RefundCreateResponse> create(String idempotencyKey, RefundCreateRequest request) {
        String key = resolveIdempotencyKey(idempotencyKey, request.getIdempotencyKey());
        UUID bookingId = request.getBookingId();
        BookingFinancial financial = bookingFinancialRepository.findForUpdate(bookingId).orElse(null);
        Payment deposit = paymentRepository.findByBookingIdAndPaymentType(bookingId, PaymentType.DEPOSIT)
                .filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS)
                .orElse(null);
        if (!isDepositPaid(financial) || deposit == null) {
            // Booking chưa cọc: không có tiền để hoàn — 200 rỗng để outbox Booking không retry mãi.
            log.warn("Refund {} for booking {} skipped: deposit not paid", key, bookingId);
            return buildCreateResponse(List.of());
        }
        Map<UUID, Refund> alreadyCreated = new HashMap<>();
        for (Refund refund : refundRepository.findAllByIdempotencyKey(key)) {
            if (refund.getOccurrenceId() != null && refund.getBookingId().equals(bookingId)) {
                alreadyCreated.put(refund.getOccurrenceId(), refund);
            }
        }
        Map<UUID, OccurrenceFinancial> occurrenceFinancials = findOccurrenceFinancials(financial);
        List<RefundCalculator.OccurrenceInput> inputs = new ArrayList<>();
        Set<UUID> requested = new HashSet<>(request.getOccurrenceIds());
        for (UUID occurrenceId : requested) {
            OccurrenceFinancial occurrenceFinancial = occurrenceFinancials.get(occurrenceId);
            if (alreadyCreated.containsKey(occurrenceId)) {
                continue;
            }
            if (occurrenceFinancial == null) {
                log.warn("Refund {}: occurrence {} has no financial record, skipped", key, occurrenceId);
                continue;
            }
            inputs.add(new RefundCalculator.OccurrenceInput(occurrenceId, occurrenceFinancial.getStartAt(), null, false,
                    occurrenceFinancial.getPriceAmount(), paidAmountOf(occurrenceFinancial)));
        }
        RefundCalculator.Context context = new RefundCalculator.Context(true, financial.getPolicySnapshot(),
                request.getReason(), request.getCancelledAt(), request.getFirstUpcomingStartAt(),
                financial.getDepositPaidAt());
        List<RefundCalculator.Line> lines = RefundCalculator.calculate(context, inputs);
        BigDecimal newTotal = BigDecimal.ZERO;
        for (RefundCalculator.Line line : lines) {
            newTotal = newTotal.add(line.refundAmount());
        }
        checkRefundable(deposit, newTotal);
        OccurrenceFinalStatus finalStatus = request.getReason() == RefundReason.CAREGIVER_MISSED
                ? OccurrenceFinalStatus.MISSED
                : OccurrenceFinalStatus.CANCELLED;
        List<Refund> refunds = new ArrayList<>(alreadyCreated.values());
        Instant now = clock.instant();
        for (RefundCalculator.Line line : lines) {
            OccurrenceFinancial occurrenceFinancial = occurrenceFinancials.get(line.occurrenceId());
            occurrenceFinancial.setFinalStatus(finalStatus);
            if (line.refundAmount().signum() <= 0) {
                continue;
            }
            // Ghi "đã hoàn" ngay khi tạo lệnh: lần tính sau (lý do khác, key khác) không hoàn trùng buổi này.
            occurrenceFinancial.setRefundedAmount(occurrenceFinancial.getRefundedAmount().add(line.refundAmount()));
            Refund refund = refundRepository.save(refundMapper.toRefund(deposit, null, line.occurrenceId(), null,
                    line.refundAmount(), line.feeAmount(), request.getReason(), request.getCancelledBy(),
                    request.getRequestedBy(), key, RefundStatus.REFUND_PENDING, now));
            paymentTransactionService.record(bookingId, deposit.getId(), refund.getId(), null,
                    TransactionEventType.REFUND_REQUESTED, refund.getAmount(), key,
                    "reason=" + request.getReason() + " rule=" + line.rule() + " fee=" + line.feeAmount(), null);
            refunds.add(refund);
        }
        log.info("Refund {} for booking {}: {} item(s), total {}", key, bookingId, refunds.size(), newTotal);
        return buildCreateResponse(refunds);
    }

    @Override
    @Transactional
    public APIResponse<RefundCreateResponse> createComplaintRefund(String idempotencyKey, ComplaintRefundRequest request) {
        String key = resolveIdempotencyKey(idempotencyKey, COMPLAINT_KEY_PREFIX + request.getComplaintId());
        List<Refund> existing = refundRepository.findAllByIdempotencyKey(key);
        boolean invoiceReduced = paymentTransactionRepository.existsByIdempotencyKeyAndEventType(key,
                TransactionEventType.INVOICE_REDUCED);
        if (!existing.isEmpty() || invoiceReduced) {
            return buildCreateResponse(existing);
        }
        UUID bookingId = request.getBookingId();
        BookingFinancial financial = bookingFinancialRepository.findForUpdate(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.REFUND_EXCEEDS_PAID));
        // REMAINING trước rồi DEPOSIT: hoàn từ khoản thu sau cùng trước.
        List<Payment> payments = new ArrayList<>(paymentRepository.findAllByBookingIdAndStatus(bookingId,
                PaymentStatus.SUCCESS));
        payments.sort(Comparator.comparing(payment -> payment.getPaymentType() == PaymentType.REMAINING ? 0 : 1));
        Map<UUID, BigDecimal> rooms = new HashMap<>();
        BigDecimal refundable = BigDecimal.ZERO;
        for (Payment payment : payments) {
            BigDecimal room = refundableRoomOf(payment);
            rooms.put(payment.getId(), room);
            refundable = refundable.add(room);
        }
        if (request.getAmount().compareTo(refundable) > 0) {
            throw new AppException(ErrorCode.REFUND_EXCEEDS_PAID);
        }
        BigDecimal remaining = reduceOpenInvoice(financial, request, key);
        List<Refund> refunds = new ArrayList<>();
        Instant now = clock.instant();
        for (Payment payment : payments) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal part = rooms.get(payment.getId()).min(remaining);
            if (part.signum() <= 0) {
                continue;
            }
            Refund refund = refundRepository.save(refundMapper.toRefund(payment, null, null, request.getComplaintId(), part,
                    BigDecimal.ZERO, RefundReason.COMPLAINT_ACCEPTED, CancelledBy.ADMIN, request.getDecidedBy(), key,
                    RefundStatus.REFUND_PENDING, now));
            paymentTransactionService.record(bookingId, payment.getId(), refund.getId(), null,
                    TransactionEventType.REFUND_REQUESTED, part, key, "complaint=" + request.getComplaintId(), null);
            refunds.add(refund);
            remaining = remaining.subtract(part);
        }
        log.info("Complaint refund {} for booking {}: {} refund(s)", key, bookingId, refunds.size());
        return buildCreateResponse(refunds);
    }

    @Override
    @Transactional
    public APIResponse<RefundResponse> retry(UUID actorId, UUID refundId) {
        Refund refund = refundRepository.findForUpdate(refundId)
                .orElseThrow(() -> new AppException(ErrorCode.REFUND_NOT_FOUND));
        if (refund.getStatus() != RefundStatus.FAILED) {
            throw new AppException(ErrorCode.REFUND_NOT_RETRYABLE);
        }
        refund.setStatus(RefundStatus.REFUND_PENDING);
        refund.setAttempts(0);
        refund.setNextAttemptAt(clock.instant());
        refund.setLastError(null);
        paymentTransactionService.record(refund.getBookingId(), refund.getPaymentId(), refund.getId(), null,
                TransactionEventType.REFUND_RETRIED, refund.getAmount(), null, "by=" + actorId, null);
        log.info("Refund {} requeued by admin {}", refundId, actorId);
        RefundResponse refundResponse = refundMapper.toResponse(refund);
        return APIResponse.success("Đã đưa yêu cầu hoàn tiền vào hàng đợi", refundResponse);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void refundWholePayment(UUID paymentId, RefundReason reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        BookingFinancial financial = bookingFinancialRepository.findForUpdate(payment.getBookingId()).orElse(null);
        String key = reason.name() + KEY_SEPARATOR + paymentId;
        if (refundRepository.existsByIdempotencyKey(key)) {
            return;
        }
        BigDecimal room = refundableRoomOf(payment);
        if (room.signum() <= 0) {
            log.warn("Whole refund {} skipped: nothing left to refund on payment {}", key, paymentId);
            return;
        }
        Refund refund = refundRepository.save(refundMapper.toRefund(payment, null, null, null, room, BigDecimal.ZERO,
                reason, CancelledBy.SYSTEM, null, key, RefundStatus.REFUND_PENDING, clock.instant()));
        paymentTransactionService.record(payment.getBookingId(), paymentId, refund.getId(), null,
                TransactionEventType.REFUND_REQUESTED, room, key, "reason=" + reason, null);
        if (financial != null) {
            if (payment.getPaymentType() == PaymentType.DEPOSIT) {
                // Hoàn hết cọc: mọi buổi coi như đã hoàn đủ để preview / lệnh sau không hoàn thêm.
                for (OccurrenceFinancial occurrenceFinancial : occurrenceFinancialRepository.findAllByBookingId(
                        payment.getBookingId())) {
                    occurrenceFinancial.setRefundedAmount(occurrenceFinancial.getDepositAmount()
                            .add(occurrenceFinancial.getChargedAmount()));
                    if (occurrenceFinancial.getFinalStatus() == null) {
                        occurrenceFinancial.setFinalStatus(OccurrenceFinalStatus.CANCELLED);
                    }
                }
            }
            financial.setStatus(BookingFinancialStatus.CLOSED);
        }
        log.warn("Payment {} fully refunded ({}): {}", paymentId, reason, room);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void refundSeparateAttempt(PaymentAttempt attempt, RefundReason reason) {
        String key = reason.name() + KEY_SEPARATOR + attempt.getId();
        if (refundRepository.existsByIdempotencyKey(key)) {
            return;
        }
        Payment payment = attempt.getPayment();
        Refund refund = refundRepository.save(refundMapper.toRefund(payment, attempt.getId(), null, null,
                attempt.getAmount(), BigDecimal.ZERO, reason, CancelledBy.SYSTEM, null, key, RefundStatus.REFUND_PENDING,
                clock.instant()));
        paymentTransactionService.record(payment.getBookingId(), payment.getId(), refund.getId(), payment.getInvoiceId(),
                TransactionEventType.REFUND_REQUESTED, attempt.getAmount(), key,
                "reason=" + reason + " attempt=" + attempt.getReference(), null);
        log.warn("Attempt {} of payment {} paid late / twice — {} queued for refund ({})", attempt.getReference(),
                payment.getId(), attempt.getAmount(), reason);
    }

    // Hóa đơn phần còn lại chưa trả: giảm nợ trước (khách chưa trả thì không cần hoàn), trả về phần còn phải hoàn.
    private BigDecimal reduceOpenInvoice(BookingFinancial financial, ComplaintRefundRequest request, String key) {
        RemainingInvoice invoice = remainingInvoiceRepository.findForUpdateByBookingId(financial.getBookingId())
                .filter(openInvoice -> OPEN_INVOICE_STATUSES.contains(openInvoice.getStatus()))
                .orElse(null);
        if (invoice == null) {
            return request.getAmount();
        }
        BigDecimal reduction = invoice.getAmount().min(request.getAmount());
        invoice.setAmount(invoice.getAmount().subtract(reduction));
        paymentTransactionService.record(financial.getBookingId(), null, null, invoice.getId(),
                TransactionEventType.INVOICE_REDUCED, reduction, key, "complaint=" + request.getComplaintId(), null);
        if (invoice.getAmount().signum() == 0) {
            invoice.setStatus(InvoiceStatus.WAIVED);
            invoice.setWaivedBy(request.getDecidedBy());
            invoice.setWaiveReason(COMPLAINT_WAIVE_REASON);
            financial.setStatus(BookingFinancialStatus.CLOSED);
            paymentTransactionService.record(financial.getBookingId(), null, null, invoice.getId(),
                    TransactionEventType.INVOICE_WAIVED, BigDecimal.ZERO, key, COMPLAINT_WAIVE_REASON, null);
        }
        return request.getAmount().subtract(reduction);
    }

    // Bất biến: đã hoàn + đang giữ chỗ + mới ≤ đã thu (kiểm trong transaction đã khóa booking_financials).
    private void checkRefundable(Payment payment, BigDecimal newAmount) {
        if (newAmount.compareTo(refundableRoomOf(payment)) > 0) {
            log.warn("Refund on payment {} rejected: {} exceeds refundable room", payment.getId(), newAmount);
            throw new AppException(ErrorCode.REFUND_EXCEEDS_PAID);
        }
    }

    private BigDecimal refundableRoomOf(Payment payment) {
        BigDecimal reserved = refundRepository.sumAmount(payment.getId(), RESERVED_STATUSES);
        return payment.getAmount().subtract(payment.getRefundedAmount()).subtract(reserved).max(BigDecimal.ZERO);
    }

    private boolean isDepositPaid(BookingFinancial financial) {
        return financial != null && financial.getStatus() != BookingFinancialStatus.AWAITING_DEPOSIT;
    }

    private Map<UUID, OccurrenceFinancial> findOccurrenceFinancials(BookingFinancial financial) {
        Map<UUID, OccurrenceFinancial> occurrenceFinancials = new HashMap<>();
        if (financial == null) {
            return occurrenceFinancials;
        }
        for (OccurrenceFinancial occurrenceFinancial : occurrenceFinancialRepository.findAllByBookingId(
                financial.getBookingId())) {
            occurrenceFinancials.put(occurrenceFinancial.getOccurrenceId(), occurrenceFinancial);
        }
        return occurrenceFinancials;
    }

    private BigDecimal priceOf(OccurrenceFinancial occurrenceFinancial) {
        return occurrenceFinancial == null ? BigDecimal.ZERO : occurrenceFinancial.getPriceAmount();
    }

    // paid_i = cọc + đã thu thêm − đã hoàn (gồm hoàn đang chờ).
    private BigDecimal paidAmountOf(OccurrenceFinancial occurrenceFinancial) {
        if (occurrenceFinancial == null) {
            return BigDecimal.ZERO;
        }
        return occurrenceFinancial.getDepositAmount()
                .add(occurrenceFinancial.getChargedAmount())
                .subtract(occurrenceFinancial.getRefundedAmount())
                .max(BigDecimal.ZERO);
    }

    private String resolveIdempotencyKey(String headerKey, String fallbackKey) {
        String key = headerKey != null && !headerKey.isBlank() ? headerKey.trim() : fallbackKey;
        if (key == null || key.isBlank()) {
            throw new AppException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new AppException(ErrorCode.TEXT_TOO_LONG);
        }
        return key;
    }

    private APIResponse<RefundCreateResponse> buildCreateResponse(List<Refund> refunds) {
        List<RefundItemResponse> items = refundMapper.toItemResponseList(refunds);
        BigDecimal totalRefund = BigDecimal.ZERO;
        for (Refund refund : refunds) {
            totalRefund = totalRefund.add(refund.getAmount());
        }
        RefundCreateResponse refundCreateResponse = refundMapper.toCreateResponse(items, totalRefund);
        return APIResponse.success("Đã ghi nhận yêu cầu hoàn tiền", refundCreateResponse);
    }
}
