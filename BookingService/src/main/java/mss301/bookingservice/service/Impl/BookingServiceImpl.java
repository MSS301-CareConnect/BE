package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import java.util.Map;
import java.util.Comparator;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.data.domain.PageRequest;
import mss301.bookingservice.service.RefundPolicy;
import mss301.bookingservice.service.IdentityUserService;
import mss301.bookingservice.repository.BookingStatusHistoryRepository;
import mss301.bookingservice.mapper.BookingStatusHistoryMapper;
import mss301.bookingservice.enums.CancelledBy;
import mss301.bookingservice.dto.response.RefundQuote;
import mss301.bookingservice.client.dto.IdentityUserResponse;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.request.AcceptRequest;
import mss301.bookingservice.dto.request.BookingCreateRequest;
import mss301.bookingservice.dto.request.BookingServiceRequest;
import mss301.bookingservice.dto.request.CaregiverAssignRequest;
import mss301.bookingservice.dto.request.DepositPaidRequest;
import mss301.bookingservice.dto.request.ReasonRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingQuote;
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
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.BookingDetailMapper;
import mss301.bookingservice.mapper.BookingMapper;
import mss301.bookingservice.mapper.OccurrenceMapper;
import mss301.bookingservice.repository.BookingDetailRepository;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.repository.BookingSpecifications;
import mss301.bookingservice.service.BookingCodeGenerator;
import mss301.bookingservice.service.BookingService;
import mss301.bookingservice.service.CareRecipientDirectoryService;
import mss301.bookingservice.service.CareServiceCatalogPort;
import mss301.bookingservice.service.CaregiverPort;
import mss301.bookingservice.service.CenterPort;
import mss301.bookingservice.service.OccurrenceGenerator;
import mss301.bookingservice.util.IdempotencyHasher;
import mss301.bookingservice.util.PageRequests;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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
    static String EXPIRED_REASON = "Quá hạn duyệt hoặc quá hạn đặt cọc";
    static String REJECTED_OCCURRENCE_REASON = "Trung tâm từ chối booking";
    static String CAREGIVER_LOCK_PREFIX = "booking:caregiver:";
    static String RECIPIENT_LOCK_PREFIX = "booking:recipient:";

    BookingRepository bookingRepository;
    BookingOccurrenceRepository bookingOccurrenceRepository;
    BookingDetailRepository bookingDetailRepository;
    BookingMapper bookingMapper;
    OccurrenceMapper occurrenceMapper;
    BookingDetailMapper bookingDetailMapper;
    OccurrenceGenerator occurrenceGenerator;
    BookingCodeGenerator bookingCodeGenerator;
    BookingStatusHistoryRepository bookingStatusHistoryRepository;
    BookingStatusHistoryMapper bookingStatusHistoryMapper;
    RefundPolicy refundPolicy;
    IdentityUserService identityUserService;
    CareRecipientDirectoryService careRecipientDirectoryService;
    CenterPort centerPort;
    CaregiverPort caregiverPort;
    CareServiceCatalogPort careServiceCatalogPort;
    BookingProperties bookingProperties;
    TransactionTemplate transactionTemplate;
    Clock clock;

    // ─── CUSTOMER ────────────────────────────────────────────────────────────

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
        RecipientSnapshot recipientSnapshot = careRecipientDirectoryService.getBookableSnapshot(request.getRecipientId(), actorId);
        centerPort.checkBookable(request.getCenterId());
        List<UUID> careServiceIds = collectCareServiceIds(request.getServices());
        AssignedCaregiver caregiver = request.getCaregiverId() == null
                ? null
                : caregiverPort.resolveAssignable(request.getCenterId(), request.getCaregiverId(), careServiceIds);
        BookingQuote quote = careServiceCatalogPort.quote(request.getCenterId(), request.getServices(), slots);
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

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<BookingSummaryResponse>> listByCustomer(UUID actorId, BookingStatus status, UUID recipientId,
                                                                            int page, int size) {
        Page<Booking> bookings = bookingRepository.findAll(
                BookingSpecifications.filter(actorId, null, recipientId, null, status, null, null),
                PageRequests.newestFirst(page, size));
        PageResponse<BookingSummaryResponse> pageResponse = toPageResponse(bookings, false);
        return APIResponse.success("Lấy danh sách booking thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<BookingDetailResponse> getByCustomer(UUID actorId, UUID id) {
        Booking booking = findCustomerBooking(actorId, id);
        BookingDetailResponse bookingDetailResponse = toDetailResponse(booking, false);
        return APIResponse.success("Lấy chi tiết booking thành công", bookingDetailResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<OccurrenceResponse>> listOccurrences(UUID actorId, UUID id) {
        Booking booking = findCustomerBooking(actorId, id);
        List<OccurrenceResponse> occurrenceResponses = occurrenceMapper.toResponseList(
                bookingOccurrenceRepository.findAllByBooking(booking.getId()));
        return APIResponse.success("Lấy danh sách buổi chăm sóc thành công", occurrenceResponses);
    }

    @Override
    public APIResponse<BookingDetailResponse> cancelByCustomer(UUID actorId, UUID id, ReasonRequest request) {
        Booking cancelledBooking = transactionTemplate.execute(status -> {
            Booking booking = lockCustomerBooking(actorId, id);
            if (!BookingAction.CANCEL_BY_CUSTOMER.isAllowedFrom(booking.getStatus())) {
                throw new AppException(ErrorCode.INVALID_BOOKING_TRANSITION);
            }
            checkNotStarted(booking);
            applyAction(booking, BookingAction.CANCEL_BY_CUSTOMER, actorId, request.getReason().strip());
            return booking;
        });
        log.info("Booking {} cancelled by customer {}", id, actorId);
        BookingDetailResponse bookingDetailResponse = toDetailResponse(cancelledBooking, false);
        return APIResponse.success("Hủy booking thành công", bookingDetailResponse);
    }

    // ─── CENTER_MANAGER ───────────────────────────────────────────────────────

    @Override
    // Không @Transactional: response gọi Identity batch (mạng).
    public APIResponse<PageResponse<BookingSummaryResponse>> listByCenter(UUID centerId, BookingStatus status, UUID caregiverId,
                                                                          LocalDate from, LocalDate to, int page, int size) {
        checkDateFilter(from, to);
        Page<Booking> bookings = bookingRepository.findAll(
                BookingSpecifications.filter(null, centerId, null, caregiverId, status, from, to),
                PageRequests.newestFirst(page, size));
        PageResponse<BookingSummaryResponse> pageResponse = toPageResponse(bookings, true);
        return APIResponse.success("Lấy danh sách booking của trung tâm thành công", pageResponse);
    }

    @Override
    public APIResponse<BookingDetailResponse> getByCenter(UUID centerId, UUID id) {
        Booking booking = findCenterBooking(centerId, id);
        BookingDetailResponse bookingDetailResponse = toDetailResponse(booking, true);
        return APIResponse.success("Lấy chi tiết booking thành công", bookingDetailResponse);
    }

    @Override
    public APIResponse<BookingDetailResponse> accept(UUID actorId, UUID centerId, UUID id, AcceptRequest request) {
        Booking booking = findCenterBooking(centerId, id);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_BOOKING_TRANSITION);
        }
        // Caregiver mới (nếu truyền) hoặc caregiver khách đã chọn — luôn xác minh lại với Provider trước khi chấp nhận.
        UUID caregiverId = request == null || request.getCaregiverId() == null ? booking.getCaregiverId() : request.getCaregiverId();
        if (caregiverId == null) {
            throw new AppException(ErrorCode.CAREGIVER_REQUIRED);
        }
        AssignedCaregiver caregiver = caregiverPort.resolveAssignable(centerId, caregiverId, findCareServiceIds(id));
        Booking acceptedBooking = transactionTemplate.execute(status -> {
            Booking lockedBooking = lockCenterBooking(centerId, id);
            BookingAction.ACCEPT.apply(lockedBooking.getStatus());
            // Quá hạn duyệt nhưng scheduler chưa kịp chuyển EXPIRED → không cho chấp nhận.
            if (!clock.instant().isBefore(lockedBooking.getAcceptanceDueAt())) {
                throw new AppException(ErrorCode.INVALID_BOOKING_TRANSITION);
            }
            assignCaregiver(lockedBooking, caregiver);
            applyAction(lockedBooking, BookingAction.ACCEPT, actorId, null);
            return lockedBooking;
        });
        log.info("Booking {} accepted by manager {} with caregiver {}", id, actorId, caregiver.getCaregiverId());
        BookingDetailResponse bookingDetailResponse = toDetailResponse(acceptedBooking, true);
        return APIResponse.success("Đã chấp nhận booking, chờ khách đặt cọc", bookingDetailResponse);
    }

    @Override
    public APIResponse<BookingDetailResponse> reject(UUID actorId, UUID centerId, UUID id, ReasonRequest request) {
        Booking rejectedBooking = transactionTemplate.execute(status -> {
            Booking lockedBooking = lockCenterBooking(centerId, id);
            applyAction(lockedBooking, BookingAction.REJECT, actorId, request.getReason().strip());
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
        Booking updatedBooking = transactionTemplate.execute(status -> {
            Booking lockedBooking = lockCenterBooking(centerId, id);
            checkCaregiverChangeable(lockedBooking);
            assignCaregiver(lockedBooking, caregiver);
            return lockedBooking;
        });
        log.info("Booking {} caregiver changed to {} by manager {}", id, caregiver.getCaregiverId(), actorId);
        BookingDetailResponse bookingDetailResponse = toDetailResponse(updatedBooking, true);
        return APIResponse.success("Đổi người chăm sóc cho booking thành công", bookingDetailResponse);
    }

    @Override
    public APIResponse<OccurrenceResponse> updateOccurrenceCaregiver(UUID actorId, UUID centerId, UUID occurrenceId,
                                                                     CaregiverAssignRequest request) {
        BookingOccurrence occurrence = bookingOccurrenceRepository.findWithBooking(occurrenceId)
                .filter(found -> found.getBooking().getCenterId().equals(centerId))
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        UUID bookingId = occurrence.getBooking().getId();
        AssignedCaregiver caregiver = caregiverPort.resolveAssignable(centerId, request.getCaregiverId(),
                findCareServiceIds(bookingId));
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
            lockedOccurrence.setCaregiverId(caregiver.getCaregiverId());
            lockedOccurrence.setCaregiverUserId(caregiver.getCaregiverUserId());
            return lockedOccurrence;
        });
        log.info("Occurrence {} of booking {} reassigned to caregiver {} by manager {}", occurrenceId, bookingId,
                caregiver.getCaregiverId(), actorId);
        OccurrenceResponse occurrenceResponse = occurrenceMapper.toResponse(updatedOccurrence);
        return APIResponse.success("Đổi người chăm sóc cho buổi thành công", occurrenceResponse);
    }

    @Override
    public APIResponse<BookingDetailResponse> cancelByCenter(UUID actorId, UUID centerId, UUID id, ReasonRequest request) {
        Booking cancelledBooking = transactionTemplate.execute(status -> {
            Booking lockedBooking = lockCenterBooking(centerId, id);
            applyAction(lockedBooking, BookingAction.CANCEL_BY_CENTER, actorId, request.getReason().strip());
            return lockedBooking;
        });
        log.info("Booking {} cancelled by manager {}", id, actorId);
        BookingDetailResponse bookingDetailResponse = toDetailResponse(cancelledBooking, true);
        return APIResponse.success("Trung tâm đã hủy booking", bookingDetailResponse);
    }

    // ─── ADMIN ───────────────────────────────────────────────────────────────

    // Không @Transactional: response gọi Identity batch (mạng) để hiển thị khách hàng.
    @Override
    public APIResponse<PageResponse<BookingSummaryResponse>> listAll(BookingStatus status, UUID centerId, UUID customerId,
                                                                     UUID recipientId, LocalDate from, LocalDate to,
                                                                     int page, int size) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new AppException(ErrorCode.INVALID_DATE_FILTER);
        }
        Page<Booking> bookings = bookingRepository.findAll(
                BookingSpecifications.filter(customerId, centerId, recipientId, null, status, from, to),
                PageRequests.newestFirst(page, size));
        PageResponse<BookingSummaryResponse> pageResponse = toPageResponse(bookings, true);
        return APIResponse.success("Lấy danh sách booking thành công", pageResponse);
    }

    @Override
    public APIResponse<BookingDetailResponse> getAny(UUID id) {
        Booking booking = bookingRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
        BookingDetailResponse bookingDetailResponse = toDetailResponse(booking, true);
        return APIResponse.success("Lấy chi tiết booking thành công", bookingDetailResponse);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private Booking persistBooking(UUID actorId, BookingCreateRequest request, List<OccurrenceSlot> slots,
                                   RecipientSnapshot recipientSnapshot, AssignedCaregiver caregiver, BookingQuote quote,
                                   String idempotencyKey, String requestHash) {
        UUID caregiverId = caregiver == null ? null : caregiver.getCaregiverId();
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
        recordCreated(savedBooking, actorId);
        return savedBooking;
    }

    /**
     * Gán caregiver mặc định cho booking và mọi buổi SCHEDULED chưa bắt đầu đang theo caregiver mặc định cũ.
     * Hai id (Provider + Identity) luôn ghi cùng nhau. Chạy trong transaction, booking đã khóa.
     */
    private void assignCaregiver(Booking booking, AssignedCaregiver caregiver) {
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
            occurrence.setCaregiverId(caregiver.getCaregiverId());
            occurrence.setCaregiverUserId(caregiver.getCaregiverUserId());
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

    // Khách chỉ hủy được trước giờ buổi đầu và khi chưa buổi nào bắt đầu.
    private void checkNotStarted(Booking booking) {
        Instant now = clock.instant();
        for (BookingOccurrence occurrence : bookingOccurrenceRepository.findAllByBooking(booking.getId())) {
            boolean started = occurrence.getStatus() == OccurrenceStatus.IN_PROGRESS
                    || occurrence.getStatus() == OccurrenceStatus.COMPLETED
                    || !occurrence.getStartAt().isAfter(now);
            if (started && occurrence.getStatus() != OccurrenceStatus.CANCELLED) {
                throw new AppException(ErrorCode.BOOKING_ALREADY_STARTED);
            }
        }
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
        return occurrence.getStatus() == OccurrenceStatus.SCHEDULED && occurrence.getStartAt().isAfter(now);
    }

    private Booking findCustomerBooking(UUID actorId, UUID id) {
        return bookingRepository.findByIdAndCustomerIdAndDeletedAtIsNull(id, actorId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
    }

    // Booking của người khác → 404 giống không tồn tại (không lộ booking tồn tại).
    private Booking lockCustomerBooking(UUID actorId, UUID id) {
        Booking booking = bookingRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
        if (!booking.getCustomerId().equals(actorId)) {
            throw new AppException(ErrorCode.BOOKING_NOT_FOUND);
        }
        return booking;
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

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverAccessResponse> checkCaregiverAccess(UUID caregiverUserId, UUID recipientId) {
        boolean allowed = bookingRepository.existsCaregiverAssignment(caregiverUserId, recipientId,
                BookingStatus.CAREGIVER_RECIPIENT_ACCESS, OccurrenceStatus.ACTIVE);
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
        applyAction(booking, BookingAction.PAY_DEPOSIT, null, reason);
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
        Optional<Booking> lockedBooking = bookingRepository.findForUpdate(id);
        // Trong lúc chờ khóa, booking có thể vừa được duyệt / đặt cọc / hủy → kiểm lại, không đúng thì bỏ qua.
        if (lockedBooking.isEmpty() || !isOverdue(lockedBooking.get(), clock.instant())) {
            return;
        }
        applyAction(lockedBooking.get(), BookingAction.EXPIRE, null, null);
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
        List<BookingSummaryResponse> items = bookingMapper.toSummaryResponseList(bookings.getContent(), customerProfiles);
        return PageResponse.of(bookings, items);
    }

    // ─── VÒNG ĐỜI TRẠNG THÁI (chỉ gọi trong transaction ghi, booking đã khóa dòng) ────
    private void recordCreated(Booking booking, UUID customerId) {
        bookingStatusHistoryRepository.save(bookingStatusHistoryMapper.toHistory(booking, null, booking.getStatus(),
                customerId, null, clock.instant()));
    }
    private void applyAction(Booking booking, BookingAction action, UUID changedBy, String reason) {
        BookingStatus fromStatus = booking.getStatus();
        BookingStatus toStatus = action.apply(fromStatus);
        Instant now = clock.instant();
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findAllByBooking(booking.getId());
        booking.setStatus(toStatus);
        String historyReason = reason;
        switch (action) {
            case ACCEPT -> {
                booking.setAcceptedBy(changedBy);
                booking.setAcceptedAt(now);
                booking.setDepositDueAt(calculateDepositDueAt(now, occurrences));
            }
            case REJECT -> {
                booking.setRejectedBy(changedBy);
                booking.setRejectedAt(now);
                booking.setRejectedReason(reason);
                cancelScheduledOccurrences(occurrences, REJECTED_OCCURRENCE_REASON);
            }
            case PAY_DEPOSIT -> booking.setConfirmedAt(now);
            case CANCEL_BY_CUSTOMER, CANCEL_BY_CENTER -> {
                booking.setCancelledAt(now);
                booking.setCancelledBy(changedBy);
                booking.setCancelReason(reason);
                cancelScheduledOccurrences(occurrences, reason);
                quoteRefund(booking, action == BookingAction.CANCEL_BY_CENTER ? CancelledBy.CENTER : CancelledBy.CUSTOMER, now);
            }
            case EXPIRE -> {
                historyReason = reason == null ? EXPIRED_REASON : reason;
                cancelScheduledOccurrences(occurrences, historyReason);
                quoteRefund(booking, CancelledBy.SYSTEM, now);
            }
            // TODO(giai đoạn ca chăm sóc): START / COMPLETE do bắt đầu / kết thúc buổi kích hoạt — chưa có API gọi.
            case START, COMPLETE -> {
            }
        }
        bookingStatusHistoryRepository.save(bookingStatusHistoryMapper.toHistory(booking, fromStatus, toStatus,
                changedBy, historyReason, now));
        log.info("Booking {} {} -> {} ({})", booking.getId(), fromStatus, toStatus, action);
    }

    // Hạn đặt cọc = accept + depositTimeout, nhưng không quá giờ buổi đầu.
    private Instant calculateDepositDueAt(Instant now, List<BookingOccurrence> occurrences) {
        Instant dueAt = now.plus(bookingProperties.getSchedule().getDepositTimeout());
        for (BookingOccurrence occurrence : occurrences) {
            if (occurrence.getStartAt().isBefore(dueAt)) {
                dueAt = occurrence.getStartAt();
            }
        }
        return dueAt;
    }

    // Booking kết thúc sớm → các buổi chưa diễn ra bị hủy (giải phóng caregiver); buổi đang / đã diễn ra giữ nguyên.
    private void cancelScheduledOccurrences(List<BookingOccurrence> occurrences, String reason) {
        for (BookingOccurrence occurrence : occurrences) {
            if (occurrence.getStatus().canTransitionTo(OccurrenceStatus.CANCELLED)) {
                occurrence.setStatus(OccurrenceStatus.CANCELLED);
                occurrence.setCancelReason(reason);
            }
        }
    }

    // TODO(Payment): gửi yêu cầu hoàn tiền sang Payment khi có (event + outbox). Hiện chỉ tính và ghi log.
    private void quoteRefund(Booking booking, CancelledBy cancelledBy, Instant cancelledAt) {
        RefundQuote refundQuote = refundPolicy.quote(booking, cancelledBy, cancelledAt);
        log.info("Booking {} refund quote: cancelledBy={}, percent={}", booking.getId(),
                refundQuote.getCancelledBy(), refundQuote.getRefundPercent());
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
