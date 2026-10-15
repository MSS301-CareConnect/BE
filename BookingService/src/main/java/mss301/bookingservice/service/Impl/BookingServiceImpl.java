package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.request.BookingCreateRequest;
import mss301.bookingservice.dto.request.BookingServiceRequest;
import mss301.bookingservice.dto.request.ReasonRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingQuote;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.OccurrenceResponse;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.dto.response.RecipientSnapshot;
import mss301.bookingservice.entity.Booking;
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
import mss301.bookingservice.service.BookingLifecycleService;
import mss301.bookingservice.service.BookingReadService;
import mss301.bookingservice.service.BookingService;
import mss301.bookingservice.service.CareRecipientDirectoryService;
import mss301.bookingservice.service.CareServiceCatalogPort;
import mss301.bookingservice.service.CaregiverPort;
import mss301.bookingservice.service.CenterPort;
import mss301.bookingservice.service.OccurrenceGenerator;
import mss301.bookingservice.service.ScheduleConflictService;
import mss301.bookingservice.util.IdempotencyHasher;
import mss301.bookingservice.util.PageRequests;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gọi CareRecipient / Provider / Catalog (mạng) luôn NGOÀI transaction DB (rule #24): create không có @Transactional,
 * phần ghi (khóa lịch → kiểm trùng → lưu booking + buổi + dịch vụ + lịch sử) chạy trong một TransactionTemplate.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BookingServiceImpl implements BookingService {

    static Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    BookingRepository bookingRepository;
    BookingOccurrenceRepository bookingOccurrenceRepository;
    BookingDetailRepository bookingDetailRepository;
    BookingMapper bookingMapper;
    OccurrenceMapper occurrenceMapper;
    BookingDetailMapper bookingDetailMapper;
    OccurrenceGenerator occurrenceGenerator;
    ScheduleConflictService scheduleConflictService;
    BookingCodeGenerator bookingCodeGenerator;
    BookingLifecycleService bookingLifecycleService;
    BookingReadService bookingReadService;
    CareRecipientDirectoryService careRecipientDirectoryService;
    CenterPort centerPort;
    CaregiverPort caregiverPort;
    CareServiceCatalogPort careServiceCatalogPort;
    BookingProperties bookingProperties;
    TransactionTemplate transactionTemplate;
    Clock clock;

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
        BookingDetailResponse bookingDetailResponse = bookingReadService.toDetailResponse(savedBooking, false);
        return APIResponse.success("Đặt lịch thành công, chờ trung tâm xác nhận", bookingDetailResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<BookingSummaryResponse>> list(UUID actorId, BookingStatus status, UUID recipientId,
                                                                  int page, int size) {
        Page<Booking> bookings = bookingRepository.findAll(
                BookingSpecifications.filter(actorId, null, recipientId, null, status, null, null),
                PageRequests.newestFirst(page, size));
        PageResponse<BookingSummaryResponse> pageResponse = bookingReadService.toPageResponse(bookings, false);
        return APIResponse.success("Lấy danh sách booking thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<BookingDetailResponse> get(UUID actorId, UUID id) {
        Booking booking = findCustomerBooking(actorId, id);
        BookingDetailResponse bookingDetailResponse = bookingReadService.toDetailResponse(booking, false);
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
    public APIResponse<BookingDetailResponse> cancel(UUID actorId, UUID id, ReasonRequest request) {
        Booking cancelledBooking = transactionTemplate.execute(status -> {
            Booking booking = lockCustomerBooking(actorId, id);
            if (!BookingAction.CANCEL_BY_CUSTOMER.isAllowedFrom(booking.getStatus())) {
                throw new AppException(ErrorCode.INVALID_BOOKING_TRANSITION);
            }
            checkNotStarted(booking);
            bookingLifecycleService.apply(booking, BookingAction.CANCEL_BY_CUSTOMER, actorId, request.getReason().strip());
            return booking;
        });
        log.info("Booking {} cancelled by customer {}", id, actorId);
        BookingDetailResponse bookingDetailResponse = bookingReadService.toDetailResponse(cancelledBooking, false);
        return APIResponse.success("Hủy booking thành công", bookingDetailResponse);
    }

    private Booking persistBooking(UUID actorId, BookingCreateRequest request, List<OccurrenceSlot> slots,
                                   RecipientSnapshot recipientSnapshot, AssignedCaregiver caregiver, BookingQuote quote,
                                   String idempotencyKey, String requestHash) {
        UUID caregiverId = caregiver == null ? null : caregiver.getCaregiverId();
        scheduleConflictService.lock(caregiverId, request.getRecipientId());
        scheduleConflictService.checkCaregiverAvailable(caregiverId, slots, null);
        scheduleConflictService.checkRecipientAvailable(request.getRecipientId(), slots, null);
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
        BookingDetailResponse bookingDetailResponse = bookingReadService.toDetailResponse(existing, false);
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
}
