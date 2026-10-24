package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import java.util.Map;
import java.util.Comparator;
import org.springframework.data.domain.PageRequest;
import mss301.bookingservice.service.IdentityUserService;
import mss301.bookingservice.client.dto.IdentityUserResponse;
import mss301.bookingservice.client.dto.PaymentPolicyResponse;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.client.dto.RefundQuoteRequest;
import mss301.bookingservice.client.dto.RefundQuoteResponse;
import mss301.bookingservice.dto.request.AcceptRequest;
import mss301.bookingservice.dto.request.BookingCancelRequest;
import mss301.bookingservice.dto.request.CancellationPreviewRequest;
import mss301.bookingservice.dto.request.SystemCancellationRequest;
import mss301.bookingservice.dto.request.BookingCreateRequest;
import mss301.bookingservice.dto.request.BookingQuoteRequest;
import mss301.bookingservice.dto.request.BookingSearchRequest;
import mss301.bookingservice.dto.request.BookingServiceRequest;
import mss301.bookingservice.dto.request.CaregiverAssignRequest;
import mss301.bookingservice.dto.request.DepositPaidRequest;
import mss301.bookingservice.dto.request.ReasonRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingHistoryResponse;
import mss301.bookingservice.dto.response.BookingQuote;
import mss301.bookingservice.dto.response.BookingQuoteResponse;
import mss301.bookingservice.dto.response.CancellationPreviewResponse;
import mss301.bookingservice.dto.response.CancellationRefundResponse;
import mss301.bookingservice.dto.response.SystemCancellationResponse;
import mss301.bookingservice.dto.response.PaymentContextResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.CaregiverAccessResponse;
import mss301.bookingservice.dto.response.OccurrenceResponse;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.dto.response.RecipientSnapshot;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingDetail;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingAction;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.CancelledBy;
import mss301.bookingservice.enums.RefundReason;
import mss301.bookingservice.enums.SystemCancellationReason;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.BookingDetailMapper;
import mss301.bookingservice.mapper.BookingEventMapper;
import mss301.bookingservice.mapper.BookingStatusHistoryMapper;
import mss301.bookingservice.repository.BookingEventRepository;
import mss301.bookingservice.repository.BookingStatusHistoryRepository;
import mss301.bookingservice.mapper.BookingMapper;
import mss301.bookingservice.mapper.OccurrenceMapper;
import mss301.bookingservice.repository.BookingDetailRepository;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.repository.CareSessionRepository;
import mss301.bookingservice.repository.OccurrenceStatsView;
import mss301.bookingservice.util.BookingSorts;
import mss301.bookingservice.util.BookingSpecifications;
import mss301.bookingservice.service.BookingCodeGenerator;
import mss301.bookingservice.service.BookingLifecycleService;
import mss301.bookingservice.service.BookingService;
import mss301.bookingservice.service.CancellationTokenService;
import mss301.bookingservice.service.CareRecipientDirectoryService;
import mss301.bookingservice.service.BookingPriceCalculator;
import mss301.bookingservice.service.CareServiceCatalogPort;
import mss301.bookingservice.service.PaymentPort;
import mss301.bookingservice.service.CaregiverPort;
import mss301.bookingservice.service.CenterPort;
import mss301.bookingservice.service.OccurrenceGenerator;
import mss301.bookingservice.util.IdempotencyHasher;
import mss301.bookingservice.util.PageRequests;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import mss301.bookingservice.service.NotificationService;
import mss301.bookingservice.service.BookingNotificationService;
import mss301.bookingservice.enums.NotificationType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Gộp logic CUSTOMER, CENTER_MANAGER và ADMIN vào một class duy nhất.
 * Phân quyền do controller kiểm bằng @PreAuthorize.
 *
 * Gọi CareRecipient / Provider / Catalog (mạng) luôn NGOÀI transaction DB (rule #24):
 * create không có @Transactional, phần ghi chạy trong TransactionTemplate.
 * Các method gọi I/O mạng (accept, updateCaregiver...) cũng không @Transactional;
 * phần ghi dùng TransactionTemplate với booking đã khóa dòng.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BookingServiceImpl implements BookingService {

    static Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    static String PAYMENT_REFERENCE_PREFIX = "paymentReference=";
    static String CAREGIVER_LOCK_PREFIX = "booking:caregiver:";
    static String RECIPIENT_LOCK_PREFIX = "booking:recipient:";
    static String CUSTOMER_LOCK_PREFIX = "booking:customer:";
    static Set<BookingStatus> PENDING_STATUSES = EnumSet.of(BookingStatus.PENDING, BookingStatus.AWAITING_DEPOSIT);
    static int ACCEPT_ATTEMPTS = 2;
    static BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    BookingRepository bookingRepository;
    BookingOccurrenceRepository bookingOccurrenceRepository;
    BookingDetailRepository bookingDetailRepository;
    BookingMapper bookingMapper;
    OccurrenceMapper occurrenceMapper;
    BookingDetailMapper bookingDetailMapper;
    OccurrenceGenerator occurrenceGenerator;
    BookingCodeGenerator bookingCodeGenerator;
    IdentityUserService identityUserService;
    CareRecipientDirectoryService careRecipientDirectoryService;
    CenterPort centerPort;
    CaregiverPort caregiverPort;
    CareServiceCatalogPort careServiceCatalogPort;
    BookingPriceCalculator bookingPriceCalculator;
    BookingLifecycleService bookingLifecycleService;
    CareSessionRepository careSessionRepository;
    PaymentPort paymentPort;
    CancellationTokenService cancellationTokenService;
    BookingEventRepository bookingEventRepository;
    BookingEventMapper bookingEventMapper;
    BookingStatusHistoryRepository bookingStatusHistoryRepository;
    BookingStatusHistoryMapper bookingStatusHistoryMapper;
    BookingProperties bookingProperties;
    TransactionTemplate transactionTemplate;
    Clock clock;
    NotificationService notificationService;
    BookingNotificationService bookingNotificationService;

    // ─── MỌI ROLE (phạm vi theo role) ─────────────────────────────────────────

    // Không @Transactional: kiểm idempotency / sinh lịch / gọi các service khác trước, chỉ ghi trong TransactionTemplate.
    @Override
    public APIResponse<BookingDetailResponse> create(UUID actorId, String idempotencyKey, BookingCreateRequest request) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = normalizedKey == null ? null : IdempotencyHasher.hash(request);
        if (normalizedKey != null) {
            Optional<Booking> existing = bookingRepository.findByCustomerIdAndIdempotencyKeyAndDeletedAtIsNull(actorId, normalizedKey);
            if (existing.isPresent()) {
                return replayCreated(existing.get(), requestHash);
            }
        }
        checkDistinctServices(request.getServices());
        List<OccurrenceSlot> slots = occurrenceGenerator.generate(request);
        // BK-02 — mọi lời gọi mạng trước transaction: nợ (Payment), giới hạn PENDING, người bệnh, trung tâm, caregiver + lịch rảnh.
        paymentPort.checkNoOutstandingDebt();
        checkPendingLimit(actorId);
        RecipientSnapshot recipientSnapshot = careRecipientDirectoryService.getBookableSnapshot(request.getRecipientId(), actorId);
        centerPort.checkBookable(request.getCenterId());
        List<UUID> careServiceIds = collectCareServiceIds(request.getServices());
        AssignedCaregiver caregiver = request.getCaregiverId() == null
                ? null
                : caregiverPort.resolveAssignable(request.getCenterId(), request.getCaregiverId(), careServiceIds);
        if (caregiver != null) {
            caregiverPort.checkAvailable(caregiver.getCaregiverId(), slots);
        }
        // Gán giá từng buổi lên chính các slot sẽ lưu (BK-01); Catalog lỗi → 503 trước khi ghi gì.
        BookingQuote quote = bookingPriceCalculator.calculate(
                careServiceCatalogPort.quote(request.getCenterId(), request.getServices()), slots);
        Booking savedBooking;
        try {
            savedBooking = transactionTemplate.execute(status -> persistBooking(actorId, request, slots, recipientSnapshot,
                    caregiver, quote, normalizedKey, requestHash));
        } catch (DataIntegrityViolationException e) {
            // Hai request cùng Idempotency-Key chạy song song: request thua vi phạm uk_bookings_idempotency → trả booking kia.
            if (normalizedKey == null) {
                throw e;
            }
            Booking existing = bookingRepository.findByCustomerIdAndIdempotencyKeyAndDeletedAtIsNull(actorId, normalizedKey)
                    .orElseThrow(() -> e);
            return replayCreated(existing, requestHash);
        }
        log.info("Booking {} created by customer {} with {} occurrences", savedBooking.getBookingCode(), actorId, slots.size());
        BookingDetailResponse bookingDetailResponse = toDetailResponse(savedBooking, false);
        return APIResponse.success("Đặt lịch thành công, chờ trung tâm xác nhận", bookingDetailResponse);
    }

    // Không @Transactional: response của manager / admin gọi Identity batch (mạng) để hiển thị khách hàng.
    @Override
    public APIResponse<PageResponse<BookingSummaryResponse>> list(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId,
                                                                  BookingSearchRequest request) {
        checkDateFilter(request.getFrom(), request.getTo());
        Specification<Booking> specification;
        boolean includeCustomer = true;
        if (roles.contains(RoleCode.ADMIN)) {
            specification = BookingSpecifications.filter(request.getCustomerId(), request.getCenterId(),
                    request.getRecipientId(), request.getCaregiverId(), request.getStatus(), request.getFrom(), request.getTo(),
                    request.getKeyword());
        } else if (roles.contains(RoleCode.CENTER_MANAGER)) {
            // Trung tâm luôn lấy từ token; centerId trong query bị bỏ qua.
            specification = BookingSpecifications.filter(request.getCustomerId(), findManagerCenter(scopeCenterId),
                    request.getRecipientId(), request.getCaregiverId(), request.getStatus(), request.getFrom(), request.getTo(),
                    request.getKeyword());
        } else {
            // Khách chỉ thấy booking của mình; customerId / centerId trong query bị bỏ qua.
            specification = BookingSpecifications.filter(actorId, null, request.getRecipientId(), request.getCaregiverId(),
                    request.getStatus(), request.getFrom(), request.getTo(), request.getKeyword());
            includeCustomer = false;
        }
        Page<Booking> bookings = bookingRepository.findAll(specification,
                PageRequests.sorted(request.getPage(), request.getSize(), BookingSorts.parse(request.getSort())));
        PageResponse<BookingSummaryResponse> pageResponse = toPageResponse(bookings, includeCustomer);
        return APIResponse.success("Lấy danh sách booking thành công", pageResponse);
    }

    @Override
    public APIResponse<BookingDetailResponse> get(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId, UUID id) {
        Booking booking = findScopedBooking(actorId, roles, scopeCenterId, id);
        BookingDetailResponse bookingDetailResponse = toDetailResponse(booking, !isCustomerScope(roles));
        return APIResponse.success("Lấy chi tiết booking thành công", bookingDetailResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<OccurrenceResponse>> listOccurrences(UUID actorId, Set<RoleCode> roles,
                                                                         UUID scopeCenterId, UUID id,
                                                                         OccurrenceStatus status, int page, int size) {
        PageRequest pageRequest = PageRequests.unsorted(page, size);
        Booking booking = findScopedBooking(actorId, roles, scopeCenterId, id);
        Set<OccurrenceStatus> statuses = status == null ? EnumSet.allOf(OccurrenceStatus.class) : EnumSet.of(status);
        Page<BookingOccurrence> occurrences = bookingOccurrenceRepository.findPageByBooking(booking.getId(), statuses,
                pageRequest);
        PageResponse<OccurrenceResponse> occurrenceResponses = PageResponse.of(occurrences,
                occurrenceMapper.toResponseList(occurrences.getContent()));
        return APIResponse.success("Lấy danh sách buổi chăm sóc thành công", occurrenceResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<BookingHistoryResponse>> listHistory(UUID actorId, Set<RoleCode> roles,
                                                                         UUID scopeCenterId, UUID id, int page,
                                                                         int size) {
        PageRequest pageRequest = PageRequests.unsorted(page, size);
        Booking booking = findScopedBooking(actorId, roles, scopeCenterId, id);
        // Gộp hai bảng khi đọc (không migrate dữ liệu cũ); số dòng mỗi booking nhỏ nên sắp + cắt trang trong bộ nhớ.
        List<BookingHistoryResponse> histories = new ArrayList<>(bookingStatusHistoryMapper.toHistoryResponseList(
                bookingStatusHistoryRepository.findAllByBooking(booking.getId())));
        histories.addAll(bookingEventMapper.toHistoryResponseList(bookingEventRepository.findAllByBooking(booking.getId())));
        histories.sort(Comparator.comparing(BookingHistoryResponse::getChangedAt));
        int fromIndex = (int) Math.min(pageRequest.getOffset(), histories.size());
        int toIndex = Math.min(fromIndex + size, histories.size());
        Page<BookingHistoryResponse> historyPage = new PageImpl<>(histories.subList(fromIndex, toIndex), pageRequest,
                histories.size());
        PageResponse<BookingHistoryResponse> historyResponse = PageResponse.of(historyPage, historyPage.getContent());
        return APIResponse.success("Lấy lịch sử booking thành công", historyResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PaymentContextResponse> getPaymentContext(UUID id) {
        Booking booking = bookingRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findAllByBooking(id);
        Instant firstStartAt = occurrences.stream()
                .map(BookingOccurrence::getStartAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
        PaymentContextResponse paymentContextResponse = bookingMapper.toPaymentContextResponse(booking, occurrences,
                firstStartAt, findOccurrenceIdsWithSession(id));
        return APIResponse.success("Lấy dữ liệu thanh toán của booking thành công", paymentContextResponse);
    }

    // Không @Transactional và không ghi DB: chỉ sinh lịch + gọi Catalog / Provider / Payment (mạng).
    @Override
    public APIResponse<BookingQuoteResponse> quote(UUID actorId, BookingQuoteRequest request) {
        checkDistinctServices(request.getServices());
        List<OccurrenceSlot> slots = occurrenceGenerator.generate(request);
        centerPort.checkBookable(request.getCenterId());
        BookingQuote bookingQuote = bookingPriceCalculator.calculate(
                careServiceCatalogPort.quote(request.getCenterId(), request.getServices()), slots);
        PaymentPolicyResponse policy = paymentPort.findEffective(request.getCenterId(), clock.instant())
                .orElse(null);
        BigDecimal estimatedDepositAmount = policy == null
                ? null
                : bookingQuote.getTotalAmount().multiply(policy.getDepositPercent())
                .divide(ONE_HUNDRED, 0, RoundingMode.HALF_UP);
        BookingQuoteResponse bookingQuoteResponse = bookingMapper.toQuoteResponse(bookingQuote, policy,
                estimatedDepositAmount);
        log.info("Quote for customer {} at center {}: {} occurrences, total={}", actorId, request.getCenterId(),
                slots.size(), bookingQuote.getTotalAmount());
        return APIResponse.success("Báo giá tạm tính thành công", bookingQuoteResponse);
    }

    // Không @Transactional: hỏi PaymentService (mạng); chỉ đọc DB, không ghi.
    @Override
    public APIResponse<CancellationPreviewResponse> previewCancellation(UUID actorId, UUID id,
                                                                        CancellationPreviewRequest request) {
        Booking booking = bookingRepository.findByIdAndDeletedAtIsNull(id)
                .filter(found -> found.getCustomerId().equals(actorId))
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
        Instant now = clock.instant();
        List<UUID> requestedIds = request == null ? null : request.getOccurrenceIds();
        List<BookingOccurrence> targets = findCancellableOccurrences(booking, requestedIds, now);
        List<UUID> targetIds = collectIds(targets);
        Instant expiresAt = now.plus(bookingProperties.getCancellation().getPreviewTtl());
        CancellationPreviewResponse previewResponse;
        if (BookingStatus.DEPOSIT_PAID.contains(booking.getStatus())) {
            RefundQuoteResponse refundQuote = quoteCustomerRefund(booking, targets, now);
            String previewToken = cancellationTokenService.sign(id, targetIds, refundQuote.getTotalRefund(), expiresAt);
            previewResponse = bookingMapper.toCancellationPreviewResponse(refundQuote, previewToken, expiresAt,
                    collectStartTimes(targets));
        } else {
            String previewToken = cancellationTokenService.sign(id, targetIds, BigDecimal.ZERO, expiresAt);
            previewResponse = bookingMapper.toUnpaidPreviewResponse(targets, previewToken, expiresAt);
        }
        return APIResponse.success("Xem trước hoàn tiền thành công", previewResponse);
    }

    // Khách hủy booking của mình, manager hủy booking trung tâm mình (PENDING thì dùng reject). Ngoài phạm vi → 404.
    // Không @Transactional: kiểm previewToken cần hỏi lại PaymentService (mạng) trước khi khóa booking.
    @Override
    public APIResponse<BookingDetailResponse> cancel(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId, UUID id,
                                                     BookingCancelRequest request) {
        String reason = request.getReason().strip();
        Booking booking = bookingRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
        boolean byCenter = isManagerOf(roles, scopeCenterId, booking);
        if (!byCenter && !(roles.contains(RoleCode.CUSTOMER) && booking.getCustomerId().equals(actorId))) {
            throw new AppException(ErrorCode.BOOKING_NOT_FOUND);
        }
        // Khách gửi kèm token: số tiền tính lại phải khớp lúc preview (vd chưa qua mốc 24h), khác → hỏi lại khách.
        BigDecimal verifiedRefund = byCenter || request.getPreviewToken() == null
                ? null
                : verifyPreview(booking, request, clock.instant());
        CancellationRefundResponse refundResponse = transactionTemplate.execute(status ->
                cancelInTransaction(actorId, byCenter, id, reason, request, verifiedRefund));
        log.info("Booking {} cancelled by {} ({})", id, actorId, byCenter ? CancelledBy.CENTER : CancelledBy.CUSTOMER);
        Booking cancelledBooking = bookingRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
        BookingDetailResponse bookingDetailResponse = toDetailResponse(cancelledBooking, !isCustomerScope(roles));
        bookingMapper.updateRefund(refundResponse, bookingDetailResponse);
        return APIResponse.success("Hủy booking thành công", bookingDetailResponse);
    }

    // Không @Transactional: mỗi booking một transaction ngắn — lỗi một booking (log) không dừng cả lô; chạy lại không hủy /
    // hoàn tiền lần hai vì buổi đã hủy không còn được chọn.
    @Override
    public APIResponse<SystemCancellationResponse> cancelBySystem(SystemCancellationRequest request) {
        String note = request.getNote() == null || request.getNote().isBlank()
                ? request.getReason().name()
                : request.getNote().strip();
        SystemCancellationResponse systemCancellationResponse;
        if (request.getReason() == SystemCancellationReason.CENTER_LOCKED) {
            if (request.getCenterId() == null) {
                throw new AppException(ErrorCode.CENTER_ID_REQUIRED);
            }
            systemCancellationResponse = cancelCenterBookings(request.getCenterId(), note);
        } else {
            if (request.getCaregiverId() == null) {
                throw new AppException(ErrorCode.CAREGIVER_ID_REQUIRED);
            }
            systemCancellationResponse = transactionTemplate.execute(status ->
                    flagForReassignment(request.getCaregiverId()));
        }
        log.info("System cancellation {} processed: {} bookings, {} occurrences", request.getReason(),
                systemCancellationResponse.getAffectedBookings(), systemCancellationResponse.getAffectedOccurrences());
        return APIResponse.success("Đã xử lý hủy hệ thống", systemCancellationResponse);
    }

    // ─── CENTER_MANAGER ───────────────────────────────────────────────────────

    @Override
    public APIResponse<BookingDetailResponse> accept(UUID actorId, UUID centerId, UUID id, AcceptRequest request) {
        UUID requestedCaregiverId = request == null ? null : request.getCaregiverId();
        // SEC-11: caregiver xác minh ngoài transaction có thể đã bị đổi trước khi khóa booking → thử lại một lần.
        for (int attempt = 1; attempt <= ACCEPT_ATTEMPTS; attempt++) {
            Optional<Booking> acceptedBooking = tryAccept(actorId, centerId, id, requestedCaregiverId);
            if (acceptedBooking.isPresent()) {
                log.info("Booking {} accepted by manager {} with caregiver {}", id, actorId,
                        acceptedBooking.get().getCaregiverId());
                BookingDetailResponse bookingDetailResponse = toDetailResponse(acceptedBooking.get(), true);
                return APIResponse.success("Đã chấp nhận booking, chờ khách đặt cọc", bookingDetailResponse);
            }
        }
        throw new AppException(ErrorCode.CONCURRENT_UPDATE);
    }

    @Override
    public APIResponse<BookingDetailResponse> reject(UUID actorId, UUID centerId, UUID id, ReasonRequest request) {
        Booking rejectedBooking = transactionTemplate.execute(status -> {
            Booking lockedBooking = lockCenterBooking(centerId, id);
            bookingLifecycleService.apply(lockedBooking, BookingAction.REJECT, actorId, request.getReason().strip());
            return lockedBooking;
        });
        log.info("Booking {} rejected by manager {}", id, actorId);
        BookingDetailResponse bookingDetailResponse = toDetailResponse(rejectedBooking, true);
        return APIResponse.success("Đã từ chối booking", bookingDetailResponse);
    }

    @Override
    public APIResponse<BookingDetailResponse> updateCaregiver(UUID actorId, UUID centerId, UUID id,
                                                              CaregiverAssignRequest request) {
        Booking booking = findCenterBooking(centerId, id);
        checkCaregiverChangeable(booking);
        AssignedCaregiver caregiver = caregiverPort.resolveAssignable(centerId, request.getCaregiverId(), findCareServiceIds(id));
        List<OccurrenceSlot> reassignedSlots = findReassignableSlots(booking);
        if (!reassignedSlots.isEmpty()) {
            caregiverPort.checkAvailable(caregiver.getCaregiverId(), reassignedSlots);
        }
        Booking updatedBooking = transactionTemplate.execute(status -> {
            Booking lockedBooking = lockCenterBooking(centerId, id);
            checkCaregiverChangeable(lockedBooking);
            assignCaregiver(lockedBooking, caregiver, actorId);
            return lockedBooking;
        });
        log.info("Booking {} caregiver changed to {} by manager {}", id, caregiver.getCaregiverId(), actorId);
        BookingDetailResponse bookingDetailResponse = toDetailResponse(updatedBooking, true);
        return APIResponse.success("Đổi người chăm sóc cho booking thành công", bookingDetailResponse);
    }

    @Override
    public APIResponse<OccurrenceResponse> updateOccurrenceCaregiver(UUID actorId, UUID centerId, UUID id, UUID occurrenceId,
                                                                     CaregiverAssignRequest request) {
        // Buổi phải thuộc đúng booking trên path và trung tâm của manager — khác thì 404 như không tồn tại.
        BookingOccurrence occurrence = bookingOccurrenceRepository.findWithBooking(occurrenceId)
                .filter(found -> found.getBooking().getId().equals(id))
                .filter(found -> found.getBooking().getCenterId().equals(centerId))
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        UUID bookingId = occurrence.getBooking().getId();
        AssignedCaregiver caregiver = caregiverPort.resolveAssignable(centerId, request.getCaregiverId(),
                findCareServiceIds(bookingId));
        caregiverPort.checkAvailable(caregiver.getCaregiverId(), occurrenceMapper.toSlotList(List.of(occurrence)));
        BookingOccurrence updatedOccurrence = transactionTemplate.execute(status -> {
            // Khóa booking trước (cùng thứ tự với đổi trạng thái) rồi mới khóa buổi.
            Booking lockedBooking = lockCenterBooking(centerId, bookingId);
            checkCaregiverChangeable(lockedBooking);
            BookingOccurrence lockedOccurrence = bookingOccurrenceRepository.findForUpdate(occurrenceId)
                    .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
            if (!isFutureScheduled(lockedOccurrence, clock.instant())) {
                throw new AppException(ErrorCode.OCCURRENCE_NOT_MODIFIABLE);
            }
            lockSchedule(caregiver.getCaregiverId(), null);
            checkCaregiverAvailable(caregiver.getCaregiverId(),
                    occurrenceMapper.toSlotList(List.of(lockedOccurrence)), bookingId);
            Instant now = clock.instant();
            recordCaregiverChange(lockedBooking, lockedOccurrence, caregiver, actorId, now);
            lockedOccurrence.assignCaregiver(caregiver.getCaregiverId(), caregiver.getCaregiverUserId());
            syncBookingCaregiver(lockedBooking, caregiver, now);
            return lockedOccurrence;
        });
        log.info("Occurrence {} of booking {} reassigned to caregiver {} by manager {}", occurrenceId, bookingId,
                caregiver.getCaregiverId(), actorId);
        OccurrenceResponse occurrenceResponse = occurrenceMapper.toResponse(updatedOccurrence);
        return APIResponse.success("Đổi người chăm sóc cho buổi thành công", occurrenceResponse);
    }

    // ─── SERVICE / THANH TOÁN (tạm) ───────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverAccessResponse> checkCaregiverAccess(UUID caregiverUserId, UUID recipientId) {
        Instant completedSince = clock.instant().minus(bookingProperties.getSchedule().getCaregiverAccessGrace());
        boolean allowed = bookingOccurrenceRepository.existsCaregiverAccess(caregiverUserId, recipientId,
                BookingStatus.CAREGIVER_RECIPIENT_ACCESS, OccurrenceStatus.ACTIVE, OccurrenceStatus.COMPLETED,
                completedSince);
        CaregiverAccessResponse caregiverAccessResponse = bookingMapper.toCaregiverAccessResponse(caregiverUserId,
                recipientId, allowed);
        return APIResponse.success("Kiểm tra phân công thành công", caregiverAccessResponse);
    }

    @Override
    @Transactional
    public APIResponse<BookingDetailResponse> markDepositPaid(UUID id, DepositPaidRequest request) {
        Booking booking = bookingRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            BookingDetailResponse confirmedResponse = toDetailResponse(booking, false);
            return APIResponse.success("Booking đã được xác nhận đặt cọc trước đó", confirmedResponse);
        }
        BookingAction.PAY_DEPOSIT.apply(booking.getStatus());
        if (!clock.instant().isBefore(booking.getDepositDueAt())) {
            throw new AppException(ErrorCode.INVALID_BOOKING_TRANSITION);
        }
        String paymentReference = request == null ? null : request.getPaymentReference();
        String reason = paymentReference == null || paymentReference.isBlank()
                ? null
                : PAYMENT_REFERENCE_PREFIX + paymentReference.strip();
        bookingLifecycleService.apply(booking, BookingAction.PAY_DEPOSIT, null, reason);
        log.info("Booking {} deposit confirmed", id);
        BookingDetailResponse bookingDetailResponse = toDetailResponse(booking, false);
        return APIResponse.success("Xác nhận đặt cọc thành công", bookingDetailResponse);
    }

    // ─── HẾT HẠN (scheduler) ─────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findExpirableIds() {
        return bookingRepository.findExpirableIds(BookingStatus.PENDING, BookingStatus.AWAITING_DEPOSIT, clock.instant(),
                PageRequest.of(0, bookingProperties.getSchedule().getExpiryBatchSize()));
    }

    @Override
    @Transactional
    public void expire(UUID id) {
        Optional<Booking> lockedBooking = bookingRepository.findForUpdateSkipLocked(id);
        // Instance khác đang xử lý → rỗng, bỏ qua. Booking có thể vừa được duyệt / đặt cọc / hủy → kiểm lại.
        if (lockedBooking.isEmpty() || !isOverdue(lockedBooking.get(), clock.instant())) {
            return;
        }
        bookingLifecycleService.apply(lockedBooking.get(), BookingAction.EXPIRE, null, null);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private Booking persistBooking(UUID actorId, BookingCreateRequest request, List<OccurrenceSlot> slots,
                                   RecipientSnapshot recipientSnapshot, AssignedCaregiver caregiver, BookingQuote quote,
                                   String idempotencyKey, String requestHash) {
        UUID caregiverId = caregiver == null ? null : caregiver.getCaregiverId();
        // Hai booking song song của cùng khách không cùng lọt giới hạn PENDING: khóa theo khách rồi đếm lại.
        bookingRepository.lockScheduleKey(CUSTOMER_LOCK_PREFIX + actorId);
        checkPendingLimit(actorId);
        lockSchedule(caregiverId, request.getRecipientId());
        checkCaregiverAvailable(caregiverId, slots, null);
        checkRecipientAvailable(request.getRecipientId(), slots, null);
        Booking booking = bookingMapper.toBooking(request, actorId, recipientSnapshot, caregiver, quote);
        booking.setBookingCode(bookingCodeGenerator.generate());
        booking.setStatus(BookingStatus.PENDING);
        booking.setTimezone(bookingProperties.getSchedule().getTimezone().getId());
        booking.setAcceptanceDueAt(calculateAcceptanceDueAt(slots));
        booking.setIdempotencyKey(idempotencyKey);
        booking.setIdempotencyHash(requestHash);
        Booking savedBooking = bookingRepository.save(booking);
        bookingOccurrenceRepository.saveAll(occurrenceMapper.toOccurrenceList(slots, savedBooking, OccurrenceStatus.SCHEDULED));
        bookingDetailRepository.saveAll(bookingDetailMapper.toBookingDetailList(quote.getItems(), savedBooking));
        bookingLifecycleService.recordCreated(savedBooking, actorId);
        return savedBooking;
    }

    /**
     * Gán caregiver mặc định cho booking và mọi buổi SCHEDULED chưa bắt đầu đang theo caregiver mặc định cũ.
     * Hai id (Provider + Identity) luôn ghi cùng nhau. Chạy trong transaction, booking đã khóa.
     */
    private void assignCaregiver(Booking booking, AssignedCaregiver caregiver, UUID changedBy) {
        Instant now = clock.instant();
        UUID previousCaregiverId = booking.getCaregiverId();
        List<BookingOccurrence> targetOccurrences = bookingOccurrenceRepository.findAllByBooking(booking.getId()).stream()
                .filter(occurrence -> isFutureScheduled(occurrence, now))
                .filter(occurrence -> Objects.equals(occurrence.getCaregiverId(), previousCaregiverId))
                .toList();
        lockSchedule(caregiver.getCaregiverId(), null);
        checkCaregiverAvailable(caregiver.getCaregiverId(),
                occurrenceMapper.toSlotList(targetOccurrences), booking.getId());
        booking.setCaregiverId(caregiver.getCaregiverId());
        booking.setCaregiverUserId(caregiver.getCaregiverUserId());
        for (BookingOccurrence occurrence : targetOccurrences) {
            recordCaregiverChange(booking, occurrence, caregiver, changedBy, now);
            occurrence.assignCaregiver(caregiver.getCaregiverId(), caregiver.getCaregiverUserId());
        }
    }

    // BK-05: mỗi buổi đổi người một dòng CAREGIVER_CHANGED (khác người mới ghi).
    // caregiver cũ ("lịch bị hủy giao"), caregiver mới ("lịch mới") và khách.
    private void recordCaregiverChange(Booking booking, BookingOccurrence occurrence, AssignedCaregiver caregiver,
                                       UUID changedBy, Instant now) {
        if (Objects.equals(occurrence.getCaregiverId(), caregiver.getCaregiverId())) {
            return;
        }
        if (occurrence.getCaregiverUserId() != null) {
            notificationService.notify(List.of(occurrence.getCaregiverUserId()), NotificationType.CAREGIVER_UNASSIGNED,
                    Map.of(), "BOOKING_OCCURRENCE", occurrence.getId());
        }
        notificationService.notify(List.of(booking.getCustomerId(), caregiver.getCaregiverUserId()), NotificationType.CAREGIVER_ASSIGNED,
                Map.of(), "BOOKING_OCCURRENCE", occurrence.getId());
        bookingEventRepository.save(bookingEventMapper.toCaregiverChangedEvent(booking, occurrence.getId(),
                occurrence.getCaregiverId(), caregiver.getCaregiverId(), changedBy, null, now));
    }

    // Đổi người từng buổi: khi mọi buổi tương lai đã thuộc caregiver mới thì cập nhật luôn caregiver cấp booking để danh
    // sách / bộ lọc theo caregiver đúng.
    private void syncBookingCaregiver(Booking booking, AssignedCaregiver caregiver, Instant now) {
        boolean allFutureAssigned = bookingOccurrenceRepository.findAllByBooking(booking.getId()).stream()
                .filter(occurrence -> isFutureScheduled(occurrence, now))
                .allMatch(occurrence -> caregiver.getCaregiverId().equals(occurrence.getCaregiverId()));
        if (allFutureAssigned) {
            booking.setCaregiverId(caregiver.getCaregiverId());
            booking.setCaregiverUserId(caregiver.getCaregiverUserId());
        }
    }

    // Một lượt accept: xác minh caregiver + lịch rảnh ngoài transaction rồi khóa booking. Trả empty khi caregiver mặc định
    // của booking đã bị đổi giữa chừng (caller thử lại với giá trị mới).
    private Optional<Booking> tryAccept(UUID actorId, UUID centerId, UUID id, UUID requestedCaregiverId) {
        Booking booking = findCenterBooking(centerId, id);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_BOOKING_TRANSITION);
        }
        // Caregiver mới (nếu truyền) hoặc caregiver khách đã chọn — luôn xác minh lại với Provider trước khi chấp nhận.
        UUID caregiverId = requestedCaregiverId == null ? booking.getCaregiverId() : requestedCaregiverId;
        if (caregiverId == null) {
            throw new AppException(ErrorCode.CAREGIVER_REQUIRED);
        }
        AssignedCaregiver caregiver = caregiverPort.resolveAssignable(centerId, caregiverId, findCareServiceIds(id));
        caregiverPort.checkAvailable(caregiverId, findReassignableSlots(booking));
        Booking acceptedBooking = transactionTemplate.execute(status -> {
            Booking lockedBooking = lockCenterBooking(centerId, id);
            if (requestedCaregiverId == null && !Objects.equals(lockedBooking.getCaregiverId(), caregiverId)) {
                return null;
            }
            BookingAction.ACCEPT.apply(lockedBooking.getStatus());
            // Quá hạn duyệt nhưng scheduler chưa kịp chuyển EXPIRED → không cho chấp nhận.
            if (!clock.instant().isBefore(lockedBooking.getAcceptanceDueAt())) {
                throw new AppException(ErrorCode.INVALID_BOOKING_TRANSITION);
            }
            assignCaregiver(lockedBooking, caregiver, actorId);
            bookingLifecycleService.apply(lockedBooking, BookingAction.ACCEPT, actorId, null);
            return lockedBooking;
        });
        return Optional.ofNullable(acceptedBooking);
    }

    // Buổi chưa bắt đầu đang theo caregiver mặc định của booking — những buổi sẽ chuyển sang caregiver mới.
    private List<OccurrenceSlot> findReassignableSlots(Booking booking) {
        Instant now = clock.instant();
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findAllByBooking(booking.getId()).stream()
                .filter(occurrence -> isFutureScheduled(occurrence, now))
                .filter(occurrence -> Objects.equals(occurrence.getCaregiverId(), booking.getCaregiverId()))
                .toList();
        return occurrenceMapper.toSlotList(occurrences);
    }

    // SEC-12: chặn khách giữ quá nhiều booking chưa xác nhận (spam chiếm lịch caregiver).
    private void checkPendingLimit(UUID customerId) {
        long pendingCount = bookingRepository.countByCustomerIdAndStatusInAndDeletedAtIsNull(customerId, PENDING_STATUSES);
        if (pendingCount >= bookingProperties.getMaxPendingPerCustomer()) {
            throw new AppException(ErrorCode.TOO_MANY_PENDING_BOOKINGS);
        }
    }

    // Hạn trung tâm duyệt = tạo + acceptanceTimeout, nhưng không quá giờ buổi đầu.
    private Instant calculateAcceptanceDueAt(List<OccurrenceSlot> slots) {
        Instant dueAt = clock.instant().plus(bookingProperties.getSchedule().getAcceptanceTimeout());
        Instant firstStart = slots.getFirst().getStartAt();
        return firstStart.isBefore(dueAt) ? firstStart : dueAt;
    }

    private APIResponse<BookingDetailResponse> replayCreated(Booking existing, String requestHash) {
        if (!existing.getIdempotencyHash().equals(requestHash)) {
            throw new AppException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        BookingDetailResponse bookingDetailResponse = toDetailResponse(existing, false);
        return APIResponse.success("Booking đã được tạo trước đó với cùng Idempotency-Key", bookingDetailResponse);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String normalizedKey = idempotencyKey.strip();
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(normalizedKey).matches()) {
            throw new AppException(ErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
        return normalizedKey;
    }

    private void checkDistinctServices(List<BookingServiceRequest> services) {
        Set<UUID> careServiceIds = new HashSet<>();
        for (BookingServiceRequest service : services) {
            if (!careServiceIds.add(service.getCareServiceId())) {
                throw new AppException(ErrorCode.DUPLICATE_SERVICE);
            }
        }
    }

    private List<UUID> collectCareServiceIds(List<BookingServiceRequest> services) {
        return services.stream()
                .map(BookingServiceRequest::getCareServiceId)
                .toList();
    }

    private List<UUID> findCareServiceIds(UUID bookingId) {
        return bookingDetailRepository.findAllByBooking(bookingId).stream()
                .map(BookingDetail::getCareServiceId)
                .toList();
    }

    // Chạy trong transaction, booking đã khóa: kiểm lại phạm vi hủy (trạng thái có thể vừa đổi), hủy buổi, xếp hoàn tiền,
    // đóng booking khi không còn buổi nào. Trả null khi booking chưa đặt cọc (không có tiền để hoàn).
    private CancellationRefundResponse cancelInTransaction(UUID actorId, boolean byCenter, UUID id, String reason,
                                                           BookingCancelRequest request, BigDecimal verifiedRefund) {
        Booking lockedBooking = bookingRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
        if (byCenter && lockedBooking.getStatus() == BookingStatus.PENDING) {
            // Booking chưa duyệt thì trung tâm từ chối (reject), không "hủy".
            throw new AppException(ErrorCode.INVALID_BOOKING_TRANSITION);
        }
        Instant now = clock.instant();
        List<BookingOccurrence> targets = findCancellableOccurrences(lockedBooking, request.getOccurrenceIds(), now);
        List<UUID> targetIds = collectIds(targets);
        if (verifiedRefund != null) {
            // Buổi hủy được có thể đã đổi trong lúc chờ khóa → token không còn khớp.
            cancellationTokenService.verify(request.getPreviewToken(), id, targetIds, verifiedRefund, now);
        }
        BookingAction cancelAction = byCenter ? BookingAction.CANCEL_BY_CENTER : BookingAction.CANCEL_BY_CUSTOMER;
        if (!BookingStatus.DEPOSIT_PAID.contains(lockedBooking.getStatus())) {
            bookingLifecycleService.apply(lockedBooking, cancelAction, actorId, reason);
            return null;
        }
        CancelledBy cancelledBy = byCenter ? CancelledBy.CENTER : CancelledBy.CUSTOMER;
        RefundReason refundReason = byCenter ? RefundReason.CENTER_CANCEL : RefundReason.CUSTOMER_CANCEL;
        bookingLifecycleService.cancelOccurrences(lockedBooking, targets, cancelledBy, actorId, reason);
        // Ghi outbox cùng transaction: Payment lỗi thì dispatcher gửi lại, không rollback việc hủy.
        bookingLifecycleService.requestRefund(lockedBooking, targetIds, refundReason, cancelledBy, actorId,
                buildRefundKeySuffix(id, targetIds));
        closeAfterCancellation(lockedBooking, cancelAction, actorId, reason);
        return bookingMapper.toCancellationRefundResponse(refundReason, targetIds, verifiedRefund);
    }

    private SystemCancellationResponse cancelCenterBookings(UUID centerId, String note) {
        List<UUID> bookingIds = bookingRepository.findIdsByCenter(centerId, BookingStatus.SCHEDULE_HOLDING);
        int affectedBookings = 0;
        int affectedOccurrences = 0;
        boolean failed = false;
        for (UUID bookingId : bookingIds) {
            try {
                Integer cancelledOccurrences = transactionTemplate.execute(status ->
                        cancelForLockedCenter(bookingId, note));
                if (cancelledOccurrences != null) {
                    affectedBookings++;
                    affectedOccurrences += cancelledOccurrences;
                }
            } catch (RuntimeException e) {
                failed = true;
                log.error("Failed to cancel booking {} of locked center {} ({})", bookingId, centerId,
                        e.getClass().getSimpleName(), e);
            }
        }
        if (failed) {
            // Cho Provider retry các booking còn lỗi; booking đã hủy được bỏ qua ở lần sau.
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        return SystemCancellationResponse.of(affectedBookings, affectedOccurrences);
    }

    // Trung tâm bị khóa: hủy mọi buổi chưa check-in (kể cả đã quá giờ); đã cọc → hoàn 100% (CENTER_LOCKED). Trả số buổi
    // bị hủy, null khi booking không đổi gì (đã xử lý ở lần gọi trước).
    private Integer cancelForLockedCenter(UUID bookingId, String note) {
        Optional<Booking> foundBooking = bookingRepository.findForUpdate(bookingId);
        if (foundBooking.isEmpty() || !BookingStatus.SCHEDULE_HOLDING.contains(foundBooking.get().getStatus())) {
            return null;
        }
        Booking lockedBooking = foundBooking.get();
        Set<UUID> occurrenceIdsWithSession = findOccurrenceIdsWithSession(bookingId);
        List<BookingOccurrence> targets = bookingOccurrenceRepository.findAllByBooking(bookingId).stream()
                .filter(occurrence -> OccurrenceStatus.NOT_STARTED.contains(occurrence.getStatus()))
                .filter(occurrence -> !occurrenceIdsWithSession.contains(occurrence.getId()))
                .toList();
        if (!BookingStatus.DEPOSIT_PAID.contains(lockedBooking.getStatus())) {
            bookingLifecycleService.apply(lockedBooking, BookingAction.CANCEL_BY_SYSTEM, null, note);
            return targets.size();
        }
        if (targets.isEmpty()) {
            return null;
        }
        List<UUID> targetIds = collectIds(targets);
        bookingLifecycleService.cancelOccurrences(lockedBooking, targets, CancelledBy.SYSTEM, null, note);
        bookingLifecycleService.requestRefund(lockedBooking, targetIds, RefundReason.CENTER_LOCKED, CancelledBy.SYSTEM,
                null, buildRefundKeySuffix(bookingId, targetIds));
        closeAfterCancellation(lockedBooking, BookingAction.CANCEL_BY_SYSTEM, null, note);
        return targets.size();
    }

    // Caregiver bị khóa: không hủy — đánh dấu buổi tương lai để manager đổi người (đổi xong cờ tự bỏ); quá giờ chưa đổi
    // thì job MISSED xử lý (OPS-06).
    private SystemCancellationResponse flagForReassignment(UUID caregiverId) {
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findReassignmentCandidates(caregiverId,
                OccurrenceStatus.NOT_STARTED, BookingStatus.SCHEDULE_HOLDING, clock.instant());
        Set<UUID> bookingIds = new HashSet<>();
        for (BookingOccurrence occurrence : occurrences) {
            occurrence.setNeedsReassignment(true);
            bookingIds.add(occurrence.getBooking().getId());
        }
        for (UUID bookingId : bookingIds) {
            Booking booking = occurrences.stream().map(BookingOccurrence::getBooking)
                    .filter(candidate -> candidate.getId().equals(bookingId)).findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
            bookingNotificationService.notifyCenter(booking.getCenterId(), NotificationType.CAREGIVER_UNASSIGNED,
                    "BOOKING", bookingId);
        }
        return SystemCancellationResponse.of(bookingIds.size(), occurrences.size());
    }

    // Không còn buổi nào chưa kết thúc: chưa phục vụ buổi nào → hủy booking (đúng bên hủy, lý do); có buổi đã phục vụ
    // → FINALIZE (OPS-04). Còn buổi → booking giữ trạng thái.
    private void closeAfterCancellation(Booking booking, BookingAction cancelAction, UUID actorId, String reason) {
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findAllByBooking(booking.getId());
        boolean unfinished = occurrences.stream()
                .anyMatch(occurrence -> OccurrenceStatus.ACTIVE.contains(occurrence.getStatus()));
        if (unfinished) {
            return;
        }
        boolean served = occurrences.stream()
                .anyMatch(occurrence -> occurrence.getStatus() == OccurrenceStatus.COMPLETED
                        || occurrence.getStatus() == OccurrenceStatus.CUSTOMER_NO_SHOW);
        if (!served && cancelAction.isAllowedFrom(booking.getStatus())) {
            bookingLifecycleService.apply(booking, cancelAction, actorId, reason);
            return;
        }
        bookingLifecycleService.finalizeIfDone(booking, actorId);
    }

    // Buổi sẽ bị hủy. occurrenceIds rỗng = mọi buổi chưa diễn ra; có buổi không hủy được trong danh sách → từ chối cả lệnh.
    private List<BookingOccurrence> findCancellableOccurrences(Booking booking, List<UUID> occurrenceIds, Instant now) {
        if (!BookingStatus.SCHEDULE_HOLDING.contains(booking.getStatus())) {
            throw new AppException(ErrorCode.INVALID_BOOKING_TRANSITION);
        }
        boolean partial = occurrenceIds != null && !occurrenceIds.isEmpty();
        boolean paid = BookingStatus.DEPOSIT_PAID.contains(booking.getStatus());
        if (partial && !paid) {
            throw new AppException(ErrorCode.CANCEL_SCOPE_NOT_ALLOWED);
        }
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findAllByBooking(booking.getId());
        Set<UUID> occurrenceIdsWithSession = findOccurrenceIdsWithSession(booking.getId());
        if (!partial) {
            List<BookingOccurrence> cancellable = occurrences.stream()
                    .filter(occurrence -> isCancellable(occurrence, occurrenceIdsWithSession, now))
                    .toList();
            // Chưa cọc thì vẫn hủy được cả booking dù không còn buổi tương lai (chờ hết hạn); đã cọc phải có buổi để hủy.
            if (cancellable.isEmpty() && paid) {
                throw new AppException(ErrorCode.OCCURRENCE_NOT_CANCELLABLE);
            }
            return cancellable;
        }
        Map<UUID, BookingOccurrence> occurrencesById = occurrences.stream()
                .collect(Collectors.toMap(BookingOccurrence::getId, Function.identity()));
        List<BookingOccurrence> selected = new ArrayList<>();
        for (UUID occurrenceId : new LinkedHashSet<>(occurrenceIds)) {
            BookingOccurrence occurrence = occurrenceId == null ? null : occurrencesById.get(occurrenceId);
            if (occurrence == null) {
                throw new AppException(ErrorCode.OCCURRENCE_NOT_IN_BOOKING);
            }
            if (!isCancellable(occurrence, occurrenceIdsWithSession, now)) {
                throw new AppException(ErrorCode.OCCURRENCE_NOT_CANCELLABLE);
            }
            selected.add(occurrence);
        }
        selected.sort(Comparator.comparing(BookingOccurrence::getStartAt));
        return selected;
    }

    // Hủy được: chưa bắt đầu (chưa check-in, chưa tới giờ) — buổi trễ giờ chưa check-in để job MISSED xử lý.
    private boolean isCancellable(BookingOccurrence occurrence, Set<UUID> occurrenceIdsWithSession, Instant now) {
        return isFutureScheduled(occurrence, now) && !occurrenceIdsWithSession.contains(occurrence.getId());
    }

    private BigDecimal verifyPreview(Booking booking, BookingCancelRequest request, Instant now) {
        List<BookingOccurrence> targets = findCancellableOccurrences(booking, request.getOccurrenceIds(), now);
        BigDecimal totalRefund = BookingStatus.DEPOSIT_PAID.contains(booking.getStatus())
                ? quoteCustomerRefund(booking, targets, now).getTotalRefund()
                : BigDecimal.ZERO;
        cancellationTokenService.verify(request.getPreviewToken(), booking.getId(), collectIds(targets), totalRefund, now);
        return totalRefund;
    }

    // C2.2: Payment tính theo chính sách đã snapshot; mốc % theo buổi chưa diễn ra gần nhất.
    private RefundQuoteResponse quoteCustomerRefund(Booking booking, List<BookingOccurrence> targets, Instant now) {
        Instant firstUpcomingStartAt = bookingOccurrenceRepository.findAllByBooking(booking.getId()).stream()
                .filter(occurrence -> isFutureScheduled(occurrence, now))
                .map(BookingOccurrence::getStartAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
        RefundQuoteRequest refundQuoteRequest = bookingMapper.toRefundQuoteRequest(booking, targets,
                CancelledBy.CUSTOMER, RefundReason.CUSTOMER_CANCEL, now, firstUpcomingStartAt);
        return paymentPort.quoteRefund(refundQuoteRequest);
    }

    private List<UUID> collectIds(List<BookingOccurrence> occurrences) {
        return occurrences.stream()
                .map(BookingOccurrence::getId)
                .toList();
    }

    private Map<UUID, Instant> collectStartTimes(List<BookingOccurrence> occurrences) {
        return occurrences.stream()
                .collect(Collectors.toMap(BookingOccurrence::getId, BookingOccurrence::getStartAt));
    }

    // Idempotency key cố định theo tập buổi: retry outbox cùng một key; một buổi chỉ hủy được một lần nên không trùng.
    private String buildRefundKeySuffix(UUID bookingId, List<UUID> occurrenceIds) {
        String sortedIds = occurrenceIds.stream()
                .sorted()
                .map(UUID::toString)
                .collect(Collectors.joining(","));
        return bookingId + ":" + UUID.nameUUIDFromBytes(sortedIds.getBytes(StandardCharsets.UTF_8));
    }

    private void checkCaregiverChangeable(Booking booking) {
        if (!BookingStatus.CAREGIVER_CHANGEABLE.contains(booking.getStatus())) {
            throw new AppException(ErrorCode.INVALID_BOOKING_TRANSITION);
        }
    }

    private void checkDateFilter(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new AppException(ErrorCode.INVALID_DATE_FILTER);
        }
    }

    private boolean isFutureScheduled(BookingOccurrence occurrence, Instant now) {
        return OccurrenceStatus.NOT_STARTED.contains(occurrence.getStatus()) && occurrence.getStartAt().isAfter(now);
    }

    // Phạm vi đọc theo role: ADMIN mọi booking, manager cùng trung tâm, khách của mình; ngoài phạm vi → 404.
    private Booking findScopedBooking(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId, UUID id) {
        Booking booking = bookingRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
        boolean visible = roles.contains(RoleCode.ADMIN)
                || isManagerOf(roles, scopeCenterId, booking)
                || (roles.contains(RoleCode.CUSTOMER) && booking.getCustomerId().equals(actorId));
        if (!visible) {
            throw new AppException(ErrorCode.BOOKING_NOT_FOUND);
        }
        return booking;
    }

    // Buổi đã có care session — PaymentService không hoàn tiền cho buổi đã bắt đầu.
    private Set<UUID> findOccurrenceIdsWithSession(UUID bookingId) {
        return Set.copyOf(careSessionRepository.findOccurrenceIdsByBooking(bookingId));
    }

    private boolean isManagerOf(Set<RoleCode> roles, UUID scopeCenterId, Booking booking) {
        return roles.contains(RoleCode.CENTER_MANAGER) && scopeCenterId != null
                && scopeCenterId.equals(booking.getCenterId());
    }

    // Khách (không kèm quyền manager / admin) không cần thông tin liên hệ của chính mình từ Identity.
    private boolean isCustomerScope(Set<RoleCode> roles) {
        return !roles.contains(RoleCode.ADMIN) && !roles.contains(RoleCode.CENTER_MANAGER);
    }

    // Manager phải có center_id trong token mới liệt kê được booking.
    private UUID findManagerCenter(UUID scopeCenterId) {
        if (scopeCenterId == null) {
            throw new AppException(ErrorCode.CENTER_SCOPE_REQUIRED);
        }
        return scopeCenterId;
    }

    private Booking findCenterBooking(UUID centerId, UUID id) {
        return bookingRepository.findByIdAndCenterIdAndDeletedAtIsNull(id, centerId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
    }

    // Booking trung tâm khác → 404 giống không tồn tại.
    private Booking lockCenterBooking(UUID centerId, UUID id) {
        Booking booking = bookingRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
        if (!booking.getCenterId().equals(centerId)) {
            throw new AppException(ErrorCode.BOOKING_NOT_FOUND);
        }
        return booking;
    }

    private boolean isOverdue(Booking booking, Instant now) {
        return switch (booking.getStatus()) {
            case PENDING -> booking.getAcceptanceDueAt() != null && !now.isBefore(booking.getAcceptanceDueAt());
            case AWAITING_DEPOSIT -> booking.getDepositDueAt() != null && !now.isBefore(booking.getDepositDueAt());
            default -> false;
        };
    }

    // ─── ĐỌC / DỰNG RESPONSE ─────────────────────────────────────────────────
    private BookingDetailResponse toDetailResponse(Booking booking, boolean includeCustomer) {
        List<BookingDetail> details = bookingDetailRepository.findAllByBooking(booking.getId());
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findAllByBooking(booking.getId());
        IdentityUserResponse customer = includeCustomer
                ? identityUserService.getProfiles(List.of(booking.getCustomerId())).get(booking.getCustomerId())
                : null;
        return bookingMapper.toDetailResponse(booking, details, occurrences, customer);
    }
    private PageResponse<BookingSummaryResponse> toPageResponse(Page<Booking> bookings, boolean includeCustomer) {
        Map<UUID, IdentityUserResponse> customerProfiles = includeCustomer
                ? identityUserService.getProfiles(bookings.map(Booking::getCustomerId).getContent())
                : Map.of();
        Map<UUID, OccurrenceStatsView> occurrenceStats = bookings.isEmpty()
                ? Map.of()
                : bookingOccurrenceRepository.summarizeByBookings(bookings.map(Booking::getId).getContent(),
                                OccurrenceStatus.ACTIVE, OccurrenceStatus.COMPLETED, clock.instant()).stream()
                        .collect(Collectors.toMap(OccurrenceStatsView::getBookingId, Function.identity()));
        List<BookingSummaryResponse> items = bookingMapper.toSummaryResponseList(bookings.getContent(), customerProfiles,
                occurrenceStats);
        return PageResponse.of(bookings, items);
    }

    // ─── TRÙNG LỊCH ──────────────────────────────────────────────────────────
    // Chạy trong transaction ghi của caller; một lần truy vấn cho cả danh sách buổi rồi lọc giao chính xác.
    void lockSchedule(UUID caregiverId, UUID recipientId) {
        if (caregiverId != null) {
            bookingRepository.lockScheduleKey(CAREGIVER_LOCK_PREFIX + caregiverId);
        }
        if (recipientId != null) {
            bookingRepository.lockScheduleKey(RECIPIENT_LOCK_PREFIX + recipientId);
        }
    }
    void checkCaregiverAvailable(UUID caregiverId, List<OccurrenceSlot> slots, UUID excludeBookingId) {
        if (caregiverId == null || slots.isEmpty()) {
            return;
        }
        List<BookingOccurrence> candidates = bookingOccurrenceRepository.findCaregiverOverlaps(caregiverId,
                findRangeStart(slots), findRangeEnd(slots), OccurrenceStatus.ACTIVE, BookingStatus.SCHEDULE_HOLDING);
        if (hasOverlap(candidates, slots, excludeBookingId)) {
            throw new AppException(ErrorCode.CAREGIVER_SCHEDULE_CONFLICT);
        }
    }
    void checkRecipientAvailable(UUID recipientId, List<OccurrenceSlot> slots, UUID excludeBookingId) {
        if (recipientId == null || slots.isEmpty()) {
            return;
        }
        List<BookingOccurrence> candidates = bookingOccurrenceRepository.findRecipientOverlaps(recipientId,
                findRangeStart(slots), findRangeEnd(slots), OccurrenceStatus.ACTIVE, BookingStatus.SCHEDULE_HOLDING);
        if (hasOverlap(candidates, slots, excludeBookingId)) {
            throw new AppException(ErrorCode.RECIPIENT_SCHEDULE_CONFLICT);
        }
    }

    // Khoảng nửa mở [start, end): buổi kết thúc đúng lúc buổi khác bắt đầu KHÔNG tính là trùng.
    static boolean overlaps(Instant firstStart, Instant firstEnd, Instant secondStart, Instant secondEnd) {
        return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
    }

    private boolean hasOverlap(List<BookingOccurrence> candidates, List<OccurrenceSlot> slots, UUID excludeBookingId) {
        for (BookingOccurrence candidate : candidates) {
            if (excludeBookingId != null && excludeBookingId.equals(candidate.getBooking().getId())) {
                continue;
            }
            for (OccurrenceSlot slot : slots) {
                if (overlaps(candidate.getStartAt(), candidate.getEndAt(), slot.getStartAt(), slot.getEndAt())) {
                    return true;
                }
            }
        }
        return false;
    }

    private Instant findRangeStart(List<OccurrenceSlot> slots) {
        return slots.stream()
                .map(OccurrenceSlot::getStartAt)
                .min(Comparator.naturalOrder())
                .orElseThrow();
    }

    private Instant findRangeEnd(List<OccurrenceSlot> slots) {
        return slots.stream()
                .map(OccurrenceSlot::getEndAt)
                .max(Comparator.naturalOrder())
                .orElseThrow();
    }
}
