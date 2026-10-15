package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.dto.request.AcceptRequest;
import mss301.bookingservice.dto.request.CaregiverAssignRequest;
import mss301.bookingservice.dto.request.ReasonRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.OccurrenceResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingDetail;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingAction;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.OccurrenceMapper;
import mss301.bookingservice.repository.BookingDetailRepository;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.repository.BookingSpecifications;
import mss301.bookingservice.service.BookingLifecycleService;
import mss301.bookingservice.service.BookingReadService;
import mss301.bookingservice.service.CaregiverPort;
import mss301.bookingservice.service.CenterBookingService;
import mss301.bookingservice.service.ScheduleConflictService;
import mss301.bookingservice.util.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Gọi Provider (xác minh caregiver) và Identity (thông tin khách) NGOÀI transaction; mọi thay đổi chạy trong
 * TransactionTemplate với booking đã khóa dòng. Không có method nào @Transactional vì đều có I/O mạng.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CenterBookingServiceImpl implements CenterBookingService {

    BookingRepository bookingRepository;
    BookingOccurrenceRepository bookingOccurrenceRepository;
    BookingDetailRepository bookingDetailRepository;
    OccurrenceMapper occurrenceMapper;
    BookingLifecycleService bookingLifecycleService;
    BookingReadService bookingReadService;
    ScheduleConflictService scheduleConflictService;
    CaregiverPort caregiverPort;
    TransactionTemplate transactionTemplate;
    Clock clock;

    @Override
    public APIResponse<PageResponse<BookingSummaryResponse>> list(UUID centerId, BookingStatus status, UUID caregiverId,
                                                                  LocalDate from, LocalDate to, int page, int size) {
        checkDateFilter(from, to);
        Page<Booking> bookings = bookingRepository.findAll(
                BookingSpecifications.filter(null, centerId, null, caregiverId, status, from, to),
                PageRequests.newestFirst(page, size));
        PageResponse<BookingSummaryResponse> pageResponse = bookingReadService.toPageResponse(bookings, true);
        return APIResponse.success("Lấy danh sách booking của trung tâm thành công", pageResponse);
    }

    @Override
    public APIResponse<BookingDetailResponse> get(UUID centerId, UUID id) {
        Booking booking = findCenterBooking(centerId, id);
        BookingDetailResponse bookingDetailResponse = bookingReadService.toDetailResponse(booking, true);
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
            bookingLifecycleService.apply(lockedBooking, BookingAction.ACCEPT, actorId, null);
            return lockedBooking;
        });
        log.info("Booking {} accepted by manager {} with caregiver {}", id, actorId, caregiver.getCaregiverId());
        BookingDetailResponse bookingDetailResponse = bookingReadService.toDetailResponse(acceptedBooking, true);
        return APIResponse.success("Đã chấp nhận booking, chờ khách đặt cọc", bookingDetailResponse);
    }

    @Override
    public APIResponse<BookingDetailResponse> reject(UUID actorId, UUID centerId, UUID id, ReasonRequest request) {
        Booking rejectedBooking = transactionTemplate.execute(status -> {
            Booking lockedBooking = lockCenterBooking(centerId, id);
            bookingLifecycleService.apply(lockedBooking, BookingAction.REJECT, actorId, request.getReason().strip());
            return lockedBooking;
        });
        log.info("Booking {} rejected by manager {}", id, actorId);
        BookingDetailResponse bookingDetailResponse = bookingReadService.toDetailResponse(rejectedBooking, true);
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
        BookingDetailResponse bookingDetailResponse = bookingReadService.toDetailResponse(updatedBooking, true);
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
            scheduleConflictService.lock(caregiver.getCaregiverId(), null);
            scheduleConflictService.checkCaregiverAvailable(caregiver.getCaregiverId(),
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
    public APIResponse<BookingDetailResponse> cancel(UUID actorId, UUID centerId, UUID id, ReasonRequest request) {
        Booking cancelledBooking = transactionTemplate.execute(status -> {
            Booking lockedBooking = lockCenterBooking(centerId, id);
            bookingLifecycleService.apply(lockedBooking, BookingAction.CANCEL_BY_CENTER, actorId, request.getReason().strip());
            return lockedBooking;
        });
        log.info("Booking {} cancelled by manager {}", id, actorId);
        BookingDetailResponse bookingDetailResponse = bookingReadService.toDetailResponse(cancelledBooking, true);
        return APIResponse.success("Trung tâm đã hủy booking", bookingDetailResponse);
    }

    /**
     * Gán caregiver mặc định cho booking và mọi buổi SCHEDULED chưa bắt đầu đang theo caregiver mặc định cũ (buổi đã
     * đổi riêng giữ nguyên). Hai id (Provider + Identity) luôn ghi cùng nhau. Chạy trong transaction, booking đã khóa.
     */
    private void assignCaregiver(Booking booking, AssignedCaregiver caregiver) {
        Instant now = clock.instant();
        UUID previousCaregiverId = booking.getCaregiverId();
        List<BookingOccurrence> targetOccurrences = bookingOccurrenceRepository.findAllByBooking(booking.getId()).stream()
                .filter(occurrence -> isFutureScheduled(occurrence, now))
                .filter(occurrence -> Objects.equals(occurrence.getCaregiverId(), previousCaregiverId))
                .toList();
        scheduleConflictService.lock(caregiver.getCaregiverId(), null);
        scheduleConflictService.checkCaregiverAvailable(caregiver.getCaregiverId(),
                occurrenceMapper.toSlotList(targetOccurrences), booking.getId());
        booking.setCaregiverId(caregiver.getCaregiverId());
        booking.setCaregiverUserId(caregiver.getCaregiverUserId());
        for (BookingOccurrence occurrence : targetOccurrences) {
            occurrence.setCaregiverId(caregiver.getCaregiverId());
            occurrence.setCaregiverUserId(caregiver.getCaregiverUserId());
        }
    }

    private boolean isFutureScheduled(BookingOccurrence occurrence, Instant now) {
        return occurrence.getStatus() == OccurrenceStatus.SCHEDULED && occurrence.getStartAt().isAfter(now);
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

    private List<UUID> findCareServiceIds(UUID bookingId) {
        return bookingDetailRepository.findAllByBooking(bookingId).stream()
                .map(BookingDetail::getCareServiceId)
                .toList();
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
}
