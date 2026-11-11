package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.dto.IdentityUserResponse;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.request.BusyCaregiverRequest;
import mss301.bookingservice.dto.request.CustomerVisitReportRequest;
import mss301.bookingservice.dto.request.NoShowDecisionRequest;
import mss301.bookingservice.dto.request.NoShowReportRequest;
import mss301.bookingservice.dto.request.OccurrenceAcknowledgementRequest;
import mss301.bookingservice.dto.request.TimeSlotRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BusyCaregiverResponse;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;
import mss301.bookingservice.dto.response.OccurrenceResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingDetail;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.dto.request.EmergencyReassignmentRequest;
import mss301.bookingservice.dto.request.OccurrenceReschedulePreviewRequest;
import mss301.bookingservice.dto.request.OccurrenceRescheduleRequest;
import mss301.bookingservice.dto.request.ReplacementConsentRequest;
import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.dto.response.OccurrenceRescheduleResponse;
import mss301.bookingservice.entity.CareSession;
import mss301.bookingservice.enums.BookingEventType;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.CancelledBy;
import mss301.bookingservice.enums.CaregiverAbsenceSource;
import mss301.bookingservice.enums.MissedReason;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.RefundReason;
import mss301.bookingservice.enums.ReplacementConsentStatus;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.BookingEventMapper;
import mss301.bookingservice.mapper.OccurrenceMapper;
import mss301.bookingservice.repository.BookingDetailRepository;
import mss301.bookingservice.repository.BookingEventRepository;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.repository.CareSessionRepository;
import mss301.bookingservice.service.BookingLifecycleService;
import mss301.bookingservice.service.BookingOccurrenceService;
import mss301.bookingservice.service.CaregiverAbsenceService;
import mss301.bookingservice.service.CaregiverPort;
import mss301.bookingservice.service.CaregiverScheduleGuard;
import mss301.bookingservice.service.IdentityUserService;
import mss301.bookingservice.util.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import mss301.bookingservice.service.NotificationService;
import java.util.Map;
import mss301.bookingservice.service.BookingNotificationService;
import mss301.bookingservice.enums.NotificationType;
import mss301.bookingservice.enums.SessionCompletionReview;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BookingOccurrenceServiceImpl implements BookingOccurrenceService {

    static String AUTO_NO_SHOW_REASON = "Tự xác nhận khách vắng: trung tâm không quyết định trong 24 giờ";

    // Caregiver chỉ thấy đủ SĐT khách khi buổi sắp diễn ra (OPS-01); trước đó chỉ cần biết có liên hệ được.
    static Duration PHONE_REVEAL_WINDOW = Duration.ofHours(24);
    static int PHONE_VISIBLE_DIGITS = 2;
    static int CUSTOMER_REPORT_NOTE_MIN_LENGTH = 10;
    static int SCAN_BATCH_SIZE = 100;
    static Instant UNBOUNDED_START = Instant.parse("1970-01-01T00:00:00Z");
    static Instant UNBOUNDED_END = Instant.parse("9999-12-31T00:00:00Z");
    static String MISSED_BY_JOB_REASON = "Người chăm sóc không check-in đúng hạn";
    static String REPLACEMENT_REJECTED_REASON = "Khách từ chối người thay thế khẩn cấp";
    static String OCCURRENCE_REF = "BOOKING_OCCURRENCE";
    static DateTimeFormatter VISIT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    static DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    BookingOccurrenceRepository bookingOccurrenceRepository;
    BookingRepository bookingRepository;
    BookingDetailRepository bookingDetailRepository;
    BookingEventRepository bookingEventRepository;
    CareSessionRepository careSessionRepository;
    OccurrenceMapper occurrenceMapper;
    BookingEventMapper bookingEventMapper;
    IdentityUserService identityUserService;
    BookingLifecycleService bookingLifecycleService;
    BookingProperties bookingProperties;
    TransactionTemplate transactionTemplate;
    Clock clock;
    BookingNotificationService bookingNotificationService;
    NotificationService notificationService;
    CaregiverPort caregiverPort;
    CaregiverAbsenceService caregiverAbsenceService;
    CaregiverScheduleGuard caregiverScheduleGuard;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CaregiverOccurrenceResponse>> list(UUID actorId, LocalDate from, LocalDate to,
                                                                       OccurrenceStatus status, UUID bookingId, int page,
                                                                       int size) {
        PageRequest pageRequest = PageRequests.unsorted(page, size);
        if (from != null && to != null && to.isBefore(from)) {
            throw new AppException(ErrorCode.INVALID_DATE_FILTER);
        }
        // Có phân trang nên không còn giới hạn độ dài khoảng ngày; bỏ trống một đầu = không giới hạn đầu đó.
        ZoneId zone = bookingProperties.getSchedule().getTimezone();
        Instant rangeStart = from == null ? UNBOUNDED_START : from.atStartOfDay(zone).toInstant();
        Instant rangeEnd = to == null ? UNBOUNDED_END : to.plusDays(1).atStartOfDay(zone).toInstant();
        Set<OccurrenceStatus> statuses = status == null ? EnumSet.allOf(OccurrenceStatus.class) : EnumSet.of(status);
        Page<BookingOccurrence> occurrences = bookingOccurrenceRepository.findCaregiverSchedule(actorId, rangeStart,
                rangeEnd, bookingId, statuses, BookingStatus.CAREGIVER_VISIBLE, pageRequest);
        PageResponse<CaregiverOccurrenceResponse> occurrenceResponses = PageResponse.of(occurrences,
                occurrenceMapper.toCaregiverResponseList(occurrences.getContent()));
        return APIResponse.success("Lấy lịch chăm sóc thành công", occurrenceResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<BusyCaregiverResponse> listBusyCaregivers(BusyCaregiverRequest request) {
        for (TimeSlotRequest slot : request.getSlots()) {
            if (!slot.getEndAt().isAfter(slot.getStartAt())) {
                throw new AppException(ErrorCode.INVALID_TIME_SLOT);
            }
        }
        Instant rangeStart = request.getSlots().stream()
                .map(TimeSlotRequest::getStartAt)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        Instant rangeEnd = request.getSlots().stream()
                .map(TimeSlotRequest::getEndAt)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        List<BookingOccurrence> candidates = bookingOccurrenceRepository.findCaregiversOverlaps(
                Set.copyOf(request.getCaregiverIds()), rangeStart, rangeEnd, OccurrenceStatus.ACTIVE,
                BookingStatus.SCHEDULE_HOLDING);
        // Lọc giao chính xác từng slot (nửa mở): buổi kết thúc đúng lúc slot bắt đầu không tính là bận.
        Set<UUID> busyCaregiverIds = new TreeSet<>();
        for (BookingOccurrence occurrence : candidates) {
            for (TimeSlotRequest slot : request.getSlots()) {
                if (occurrence.getStartAt().isBefore(slot.getEndAt()) && slot.getStartAt().isBefore(occurrence.getEndAt())) {
                    busyCaregiverIds.add(occurrence.getCaregiverId());
                }
            }
        }
        BusyCaregiverResponse busyCaregiverResponse = BusyCaregiverResponse.of(List.copyOf(busyCaregiverIds));
        return APIResponse.success("Kiểm tra lịch bận thành công", busyCaregiverResponse);
    }

    // Không @Transactional: lấy SĐT khách từ Identity (mạng) — mỗi truy vấn repository tự chạy transaction ngắn.
    @Override
    public APIResponse<CaregiverOccurrenceResponse> get(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID id) {
        BookingOccurrence occurrence = bookingOccurrenceRepository.findWithBooking(id)
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        Booking booking = occurrence.getBooking();
        boolean fullAccess = hasFullAccess(actorId, roles, centerId, booking);
        CaregiverOccurrenceResponse occurrenceResponse;
        if (fullAccess) {
            occurrenceResponse = occurrenceMapper.toCaregiverDetailResponse(occurrence);
        } else if (isAssignedCaregiver(actorId, roles, occurrence)) {
            // Caregiver vẫn thấy buổi của booking đã hủy / hoàn tất nhưng không còn thấy hồ sơ y tế (SEC-09).
            occurrenceResponse = canCaregiverSeeRecipient(occurrence)
                    ? occurrenceMapper.toCaregiverDetailResponse(occurrence)
                    : occurrenceMapper.toCaregiverResponse(occurrence);
        } else {
            throw new AppException(ErrorCode.OCCURRENCE_NOT_FOUND);
        }
        // Buổi gắn một dịch vụ (2026-11-05) chỉ hiện dịch vụ đó.
        List<BookingDetail> services = bookingDetailRepository.findAllByBooking(booking.getId()).stream()
                .filter(detail -> occurrence.getCareServiceId() == null
                        || occurrence.getCareServiceId().equals(detail.getCareServiceId()))
                .toList();
        IdentityUserResponse customer = identityUserService.getProfiles(List.of(booking.getCustomerId()))
                .get(booking.getCustomerId());
        String customerPhone = resolveCustomerPhone(customer, occurrence, fullAccess);
        String customerName = customer == null ? null : customer.getFullName();
        // Các buổi của cùng caregiver trong booking (manager / admin: theo caregiver của buổi đang xem).
        List<BookingOccurrence> bookingVisits = bookingOccurrenceRepository.findAllByBooking(booking.getId()).stream()
                .filter(visit -> Objects.equals(visit.getCaregiverUserId(), occurrence.getCaregiverUserId()))
                .sorted(Comparator.comparing(BookingOccurrence::getStartAt))
                .toList();
        occurrenceMapper.updateDetail(services, customerPhone, customerName, findSessionId(occurrence), bookingVisits,
                occurrenceResponse);
        return APIResponse.success("Lấy chi tiết buổi chăm sóc thành công", occurrenceResponse);
    }

    @Override
    public APIResponse<CaregiverOccurrenceResponse> acknowledge(UUID actorId, UUID id) {
        BookingOccurrence occurrence = findAssignedOccurrence(actorId, id);
        BookingOccurrence acknowledgedOccurrence = transactionTemplate.execute(status -> {
            // Khóa booking trước rồi mới tới buổi — cùng thứ tự với đổi trạng thái booking / đổi caregiver.
            Booking lockedBooking = bookingRepository.findForUpdate(occurrence.getBooking().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
            if (!BookingStatus.CAREGIVER_RECIPIENT_ACCESS.contains(lockedBooking.getStatus())) {
                throw new AppException(ErrorCode.BOOKING_NOT_CONFIRMED);
            }
            return markAcknowledged(actorId, id, clock.instant());
        });
        log.info("Occurrence {} acknowledged by caregiver {}", id, actorId);
        CaregiverOccurrenceResponse occurrenceResponse = occurrenceMapper.toCaregiverResponse(acknowledgedOccurrence);
        return APIResponse.success("Đã xác nhận nhận ca", occurrenceResponse);
    }

    @Override
    public APIResponse<List<CaregiverOccurrenceResponse>> acknowledgeMany(UUID actorId,
                                                                          OccurrenceAcknowledgementRequest request) {
        Set<UUID> occurrenceIds = new LinkedHashSet<>(request.getOccurrenceIds());
        UUID bookingId = findCommonBooking(actorId, occurrenceIds);
        List<BookingOccurrence> acknowledgedOccurrences = transactionTemplate.execute(status -> {
            // Cùng thứ tự khóa booking → buổi như nhận từng buổi; một buổi sai thì rollback cả lệnh.
            Booking lockedBooking = bookingRepository.findForUpdate(bookingId)
                    .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
            if (!BookingStatus.CAREGIVER_RECIPIENT_ACCESS.contains(lockedBooking.getStatus())) {
                throw new AppException(ErrorCode.BOOKING_NOT_CONFIRMED);
            }
            Instant now = clock.instant();
            List<BookingOccurrence> occurrences = new ArrayList<>();
            for (UUID occurrenceId : occurrenceIds) {
                occurrences.add(markAcknowledged(actorId, occurrenceId, now));
            }
            return occurrences;
        });
        log.info("Caregiver {} acknowledged {} occurrence(s) of booking {}", actorId, occurrenceIds.size(), bookingId);
        List<CaregiverOccurrenceResponse> occurrenceResponses = acknowledgedOccurrences.stream()
                .map(occurrenceMapper::toCaregiverResponse)
                .toList();
        return APIResponse.success("Đã nhận " + occurrenceResponses.size() + " buổi", occurrenceResponses);
    }

    @Override
    @Transactional
    public APIResponse<CaregiverOccurrenceResponse> reportNoShow(UUID actorId, UUID id, NoShowReportRequest request) {
        BookingOccurrence occurrence = findAssignedOccurrence(actorId, id);
        Booking booking = lockBooking(occurrence);
        BookingOccurrence lockedOccurrence = bookingOccurrenceRepository.findForUpdate(id)
                .filter(found -> actorId.equals(found.getCaregiverUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        if (!BookingStatus.CAREGIVER_RECIPIENT_ACCESS.contains(booking.getStatus())) {
            throw new AppException(ErrorCode.BOOKING_NOT_CONFIRMED);
        }
        // Đã check-in thì buổi không còn SCHEDULED / ACKNOWLEDGED — khách có mặt, không báo vắng được.
        if (!OccurrenceStatus.WORKABLE.contains(lockedOccurrence.getStatus())) {
            throw new AppException(ErrorCode.INVALID_OCCURRENCE_TRANSITION);
        }
        if (lockedOccurrence.getNoShowReportedAt() != null) {
            throw new AppException(ErrorCode.NO_SHOW_ALREADY_REPORTED);
        }
        Instant now = clock.instant();
        // D22 / Q12: báo vắng trước mốc MISSED, cùng mốc với hạn check-in của booking.
        Instant reportDeadline = lockedOccurrence.getStartAt().plus(Duration.ofMinutes(booking.getCheckInLateMinutes()));
        if (now.isBefore(lockedOccurrence.getStartAt()) || !now.isBefore(reportDeadline)) {
            throw new AppException(ErrorCode.NO_SHOW_WINDOW_INVALID);
        }
        lockedOccurrence.setNoShowReportedAt(now);
        lockedOccurrence.setNoShowReportedBy(actorId);
        lockedOccurrence.setNoShowNote(request.getNote());
        bookingEventRepository.save(bookingEventMapper.toStatusEvent(lockedOccurrence, BookingEventType.NO_SHOW_REPORTED,
                lockedOccurrence.getStatus(), lockedOccurrence.getStatus(), actorId, request.getNote(), now));
        bookingNotificationService.notifyOccurrence(lockedOccurrence, NotificationType.NO_SHOW_REPORTED);
        log.info("Occurrence {} reported customer no-show by caregiver {}", id, actorId);
        CaregiverOccurrenceResponse occurrenceResponse = occurrenceMapper.toCaregiverResponse(lockedOccurrence);
        return APIResponse.success("Đã báo khách vắng mặt, chờ trung tâm xác nhận", occurrenceResponse);
    }

    @Override
    @Transactional
    public APIResponse<CaregiverOccurrenceResponse> decideNoShow(UUID actorId, UUID centerId, UUID id,
                                                                 NoShowDecisionRequest request) {
        // Ngoài trung tâm của manager → 404 (không lộ tồn tại).
        BookingOccurrence occurrence = bookingOccurrenceRepository.findWithBooking(id)
                .filter(found -> centerId != null && centerId.equals(found.getBooking().getCenterId()))
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        Booking booking = lockBooking(occurrence);
        BookingOccurrence lockedOccurrence = bookingOccurrenceRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        if (lockedOccurrence.getNoShowReportedAt() == null) {
            throw new AppException(ErrorCode.NO_SHOW_NOT_REPORTED);
        }
        // Đã quyết định rồi (hoặc buổi bị hủy cùng booking) → không mở lại.
        if (lockedOccurrence.getNoShowDecidedAt() != null
                || !OccurrenceStatus.WORKABLE.contains(lockedOccurrence.getStatus())) {
            throw new AppException(ErrorCode.INVALID_OCCURRENCE_TRANSITION);
        }
        Instant now = clock.instant();
        lockedOccurrence.setNoShowDecidedBy(actorId);
        lockedOccurrence.setNoShowDecidedAt(now);
        if (Boolean.TRUE.equals(request.getApproved())) {
            // Khách chịu phí buổi theo chính sách snapshot — PaymentService tính ở hóa đơn phần còn lại (FINALIZE).
            changeStatus(lockedOccurrence, OccurrenceStatus.CUSTOMER_NO_SHOW, actorId, request.getNote(), now);
            bookingNotificationService.notifyOccurrence(lockedOccurrence, NotificationType.NO_SHOW_REPORTED);
        } else {
            // Bác báo cáo = caregiver mới là người vắng: hoàn tiền khách + ghi hồ sơ vắng ca (thông báo đi kèm ở đó).
            markMissedByCaregiver(booking, lockedOccurrence, CancelledBy.CENTER, actorId, request.getNote(), now,
                    CaregiverAbsenceSource.NO_SHOW_REPORT_REJECTED);
        }
        bookingLifecycleService.finalizeIfDone(booking, actorId);
        log.info("Occurrence {} no-show report {} by manager {}", id,
                Boolean.TRUE.equals(request.getApproved()) ? "approved" : "rejected", actorId);
        CaregiverOccurrenceResponse occurrenceResponse = occurrenceMapper.toCaregiverResponse(lockedOccurrence);
        return APIResponse.success("Đã xử lý báo cáo khách vắng mặt", occurrenceResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findMissedIds() {
        return bookingOccurrenceRepository.findMissedIds(OccurrenceStatus.WORKABLE,
                BookingStatus.CAREGIVER_RECIPIENT_ACCESS, clock.instant(), PageRequest.of(0, SCAN_BATCH_SIZE));
    }

    @Override
    @Transactional
    public void markMissed(UUID id) {
        BookingOccurrence occurrence = bookingOccurrenceRepository.findWithBooking(id)
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        // Booking đang bị instance khác / request khác giữ khóa → bỏ qua, lượt quét sau xét lại.
        Optional<Booking> foundBooking = bookingRepository.findForUpdateSkipLocked(occurrence.getBooking().getId());
        if (foundBooking.isEmpty()) {
            return;
        }
        Booking booking = foundBooking.get();
        BookingOccurrence lockedOccurrence = bookingOccurrenceRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        Instant now = clock.instant();
        // Kiểm lại sau khi khóa: caregiver có thể vừa check-in / báo vắng, booking vừa bị hủy.
        boolean stillMissed = BookingStatus.CAREGIVER_RECIPIENT_ACCESS.contains(booking.getStatus())
                && OccurrenceStatus.WORKABLE.contains(lockedOccurrence.getStatus())
                && lockedOccurrence.getNoShowReportedAt() == null
                && !careSessionRepository.existsByOccurrenceIdAndDeletedAtIsNull(id)
                && !lockedOccurrence.getStartAt().plus(Duration.ofMinutes(booking.getCheckInLateMinutes())).isAfter(now);
        if (!stillMissed) {
            return;
        }
        markMissedByCaregiver(booking, lockedOccurrence, CancelledBy.SYSTEM, null, MISSED_BY_JOB_REASON, now,
                CaregiverAbsenceSource.MISSED_CHECK_IN);
        bookingLifecycleService.finalizeIfDone(booking, null);
        log.warn("Occurrence {} marked MISSED — no check-in within the grace period", id);
    }

    @Override
    @Transactional
    public APIResponse<OccurrenceResponse> reportByCustomer(UUID actorId, UUID occurrenceId,
                                                           CustomerVisitReportRequest request) {
        // Buổi của khách khác → 404 (không lộ tồn tại).
        BookingOccurrence snapshot = bookingOccurrenceRepository.findWithBooking(occurrenceId)
                .filter(found -> actorId.equals(found.getBooking().getCustomerId()))
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        Booking booking = lockBooking(snapshot);
        BookingOccurrence occurrence = bookingOccurrenceRepository.findForUpdate(occurrenceId)
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        if (occurrence.getCustomerReportedAt() != null) {
            throw new AppException(ErrorCode.CUSTOMER_REPORT_ALREADY_SENT);
        }
        Instant now = clock.instant();
        String note = request == null || request.getNote() == null ? "" : request.getNote().strip();
        String visit = describeVisit(booking, occurrence);
        String body;
        if (OccurrenceStatus.WORKABLE.contains(occurrence.getStatus()) && !now.isBefore(occurrence.getStartAt())
                && !careSessionRepository.existsByOccurrenceIdAndDeletedAtIsNull(occurrenceId)) {
            // Tới giờ mà chưa ai check-in: chỉ báo trung tâm; quá hạn check-in thì job MISSED tự hoàn tiền.
            body = "Khách báo chưa thấy người chăm sóc đến: " + visit;
        } else if (occurrence.getStatus() == OccurrenceStatus.COMPLETED && occurrence.getActualEndAt() != null
                && now.isBefore(occurrence.getActualEndAt().plus(bookingProperties.getSession().getCustomerReportWindow()))) {
            if (note.length() < CUSTOMER_REPORT_NOTE_MIN_LENGTH) {
                throw new AppException(ErrorCode.CUSTOMER_REPORT_NOTE_INVALID);
            }
            CareSession session = careSessionRepository.findByOccurrenceIdAndDeletedAtIsNull(occurrenceId)
                    .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_REPORT_NOT_ALLOWED));
            // Trung tâm đã kết luận ca này rồi thì không mở lại.
            if (session.getCompletionReview() != null && session.getCompletionReview() != SessionCompletionReview.PENDING) {
                throw new AppException(ErrorCode.CUSTOMER_REPORT_NOT_ALLOWED);
            }
            session.setCompletionReview(SessionCompletionReview.PENDING);
            body = "Khách phản ánh buổi không được thực hiện đúng: " + visit;
        } else {
            throw new AppException(ErrorCode.CUSTOMER_REPORT_NOT_ALLOWED);
        }
        occurrence.setCustomerReportedAt(now);
        occurrence.setCustomerReportNote(note.isEmpty() ? null : note);
        bookingNotificationService.notifyCenter(booking.getCenterId(), NotificationType.CUSTOMER_VISIT_REPORT,
                Map.of("body", body), "BOOKING_OCCURRENCE", occurrenceId);
        log.info("Occurrence {} reported by customer {} ({})", occurrenceId, actorId, occurrence.getStatus());
        OccurrenceResponse occurrenceResponse = occurrenceMapper.toResponse(occurrence);
        return APIResponse.success("Đã gửi phản ánh tới trung tâm", occurrenceResponse);
    }

    @Override
    public void markNotDelivered(Booking booking, BookingOccurrence occurrence, UUID actorId, String note,
                                 Instant now) {
        markMissedByCaregiver(booking, occurrence, CancelledBy.CENTER, actorId, note, now,
                CaregiverAbsenceSource.SESSION_NOT_DELIVERED);
        log.warn("Occurrence {} marked CAREGIVER_MISSED — the center found the auto-closed session not delivered",
                occurrence.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findLateCheckInIds() {
        return bookingOccurrenceRepository.findLateCheckInIds(OccurrenceStatus.WORKABLE,
                BookingStatus.CAREGIVER_RECIPIENT_ACCESS, clock.instant(), PageRequest.of(0, SCAN_BATCH_SIZE));
    }

    @Override
    @Transactional
    public void warnLateCheckIn(UUID id) {
        BookingOccurrence snapshot = bookingOccurrenceRepository.findWithBooking(id).orElse(null);
        if (snapshot == null) {
            return;
        }
        // Booking đang bị request khác giữ khóa (vd caregiver đang check-in) → bỏ qua, lượt quét sau xét lại.
        Optional<Booking> foundBooking = bookingRepository.findForUpdateSkipLocked(snapshot.getBooking().getId());
        if (foundBooking.isEmpty()) {
            return;
        }
        Booking booking = foundBooking.get();
        BookingOccurrence occurrence = bookingOccurrenceRepository.findForUpdate(id).orElse(null);
        if (occurrence == null) {
            return;
        }
        Instant now = clock.instant();
        Instant checkInDeadline = occurrence.getStartAt().plus(Duration.ofMinutes(booking.getCheckInLateMinutes()));
        // Kiểm lại sau khi khóa: có thể vừa check-in / báo vắng / đổi người; quá hạn rồi thì để job MISSED xử lý.
        boolean stillLate = BookingStatus.CAREGIVER_RECIPIENT_ACCESS.contains(booking.getStatus())
                && OccurrenceStatus.WORKABLE.contains(occurrence.getStatus())
                && occurrence.getCaregiverUserId() != null
                && occurrence.getLateWarnedAt() == null
                && occurrence.getNoShowReportedAt() == null
                && !occurrence.getStartAt().isAfter(now)
                && checkInDeadline.isAfter(now);
        if (!stillLate) {
            return;
        }
        occurrence.setLateWarnedAt(now);
        String visit = describeVisit(booking, occurrence);
        String deadline = CLOCK_FORMAT.withZone(bookingProperties.getSchedule().getTimezone()).format(checkInDeadline);
        notificationService.notify(List.of(occurrence.getCaregiverUserId()), NotificationType.CHECK_IN_LATE,
                Map.of("body", "Đã tới giờ " + visit + " mà bạn chưa check-in. Sau " + deadline
                        + " buổi sẽ bị ghi nhận vắng ca"), OCCURRENCE_REF, id);
        bookingNotificationService.notifyCenter(booking.getCenterId(), NotificationType.CHECK_IN_LATE,
                Map.of("body", "Người chăm sóc chưa check-in " + visit + ". Hãy liên hệ hoặc bố trí người thay trước "
                        + deadline), OCCURRENCE_REF, id);
        notificationService.notify(List.of(booking.getCustomerId()), NotificationType.CHECK_IN_LATE,
                Map.of("body", "Người chăm sóc chưa bắt đầu " + visit + ". Trung tâm đã được báo; nếu tới " + deadline
                        + " vẫn chưa có người, buổi sẽ được hoàn 100 % tiền"), OCCURRENCE_REF, id);
        log.warn("Occurrence {} has no check-in at its start time — caregiver, center and customer warned", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findUndecidedNoShowIds() {
        Instant endedBefore = clock.instant().minus(bookingProperties.getSession().getNoShowDecisionReminderAfter());
        return bookingOccurrenceRepository.findUndecidedNoShowIds(OccurrenceStatus.WORKABLE, endedBefore,
                PageRequest.of(0, SCAN_BATCH_SIZE));
    }

    @Override
    @Transactional
    public void autoAcceptNoShow(UUID id) {
        BookingOccurrence snapshot = bookingOccurrenceRepository.findWithBooking(id).orElse(null);
        if (snapshot == null) {
            return;
        }
        // Khóa booking trước (cùng thứ tự với decideNoShow) để manager quyết cùng lúc không bị ghi đè.
        Booking booking = lockBooking(snapshot);
        BookingOccurrence occurrence = bookingOccurrenceRepository.findForUpdate(id).orElse(null);
        boolean undecided = occurrence != null
                && occurrence.getNoShowReportedAt() != null
                && occurrence.getNoShowDecidedAt() == null
                && OccurrenceStatus.WORKABLE.contains(occurrence.getStatus());
        if (!undecided) {
            return;
        }
        Instant now = clock.instant();
        occurrence.setNoShowRemindedAt(now);
        occurrence.setNoShowDecidedAt(now);
        changeStatus(occurrence, OccurrenceStatus.CUSTOMER_NO_SHOW, null, AUTO_NO_SHOW_REASON, now);
        bookingLifecycleService.finalizeIfDone(booking, null);
        // Khách phản đối qua khiếu nại (D21); trung tâm biết hệ thống đã tự quyết.
        bookingNotificationService.notifyOccurrence(occurrence, NotificationType.NO_SHOW_AUTO_ACCEPTED);
        bookingNotificationService.notifyCenter(booking.getCenterId(), NotificationType.NO_SHOW_AUTO_ACCEPTED,
                "BOOKING_OCCURRENCE", id);
        log.warn("Occurrence {} no-show report auto-accepted after the decision window", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findShiftReminderIds() {
        Instant now = clock.instant();
        return bookingOccurrenceRepository.findShiftReminderIds(OccurrenceStatus.WORKABLE,
                BookingStatus.CAREGIVER_RECIPIENT_ACCESS, now, now.plusSeconds(7200), PageRequest.of(0, 100));
    }

    @Override
    @Transactional
    public void remindShift(UUID id) {
        BookingOccurrence snapshot = bookingOccurrenceRepository.findWithBooking(id)
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        Booking booking = lockBooking(snapshot);
        BookingOccurrence occurrence = bookingOccurrenceRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        Instant now = clock.instant();
        if (occurrence.getReminderSentAt() != null || occurrence.getCaregiverUserId() == null
                || !OccurrenceStatus.WORKABLE.contains(occurrence.getStatus())
                || !BookingStatus.CAREGIVER_RECIPIENT_ACCESS.contains(booking.getStatus())
                || !occurrence.getStartAt().isAfter(now) || occurrence.getStartAt().isAfter(now.plusSeconds(7200))) {
            return;
        }
        occurrence.setReminderSentAt(now);
        notificationService.notify(List.of(occurrence.getCaregiverUserId()), NotificationType.SHIFT_REMINDER,
                Map.of(), "BOOKING_OCCURRENCE", id);
        if (occurrence.getStatus() == OccurrenceStatus.SCHEDULED) {
            // 2026-11-10: còn dưới 2 giờ mà caregiver chưa bấm nhận ca → trung tâm biết để gọi xác nhận / bố trí người khác.
            bookingNotificationService.notifyCenter(booking.getCenterId(), NotificationType.SHIFT_REMINDER,
                    Map.of("body", "Người chăm sóc chưa xác nhận nhận ca " + describeVisit(booking, occurrence)
                            + " — hãy liên hệ để chắc chắn có người đến"), OCCURRENCE_REF, id);
        }
    }

    // Khóa booking trước rồi mới tới buổi — cùng thứ tự với đổi trạng thái booking / đổi caregiver.
    private Booking lockBooking(BookingOccurrence occurrence) {
        return bookingRepository.findForUpdate(occurrence.getBooking().getId())
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
    }

    // MISSED do caregiver → hoàn 100 % tiền đã trả của buổi qua outbox (khóa theo id buổi nên job và manager không hoàn
    // hai lần) và ghi hồ sơ vắng ca để caregiver giải trình, trung tâm kết luận (2026-11-10).
    private void markMissedByCaregiver(Booking booking, BookingOccurrence occurrence, CancelledBy cancelledBy,
                                       UUID changedBy, String reason, Instant now, CaregiverAbsenceSource source) {
        changeStatus(occurrence, OccurrenceStatus.CAREGIVER_MISSED, changedBy, reason, now);
        occurrence.setMissedReason(MissedReason.CAREGIVER);
        bookingLifecycleService.requestRefund(booking, List.of(occurrence.getId()), RefundReason.CAREGIVER_MISSED,
                cancelledBy, changedBy, occurrence.getId().toString());
        caregiverAbsenceService.record(occurrence, source);
    }

    private String describeVisit(Booking booking, BookingOccurrence occurrence) {
        return "buổi " + occurrence.getOccurrenceNo() + " ("
                + VISIT_TIME_FORMAT.withZone(bookingProperties.getSchedule().getTimezone()).format(occurrence.getStartAt())
                + ") của lịch " + booking.getBookingCode();
    }

    private void changeStatus(BookingOccurrence occurrence, OccurrenceStatus toStatus, UUID changedBy, String reason,
                              Instant now) {
        OccurrenceStatus fromStatus = occurrence.getStatus();
        if (!fromStatus.canTransitionTo(toStatus)) {
            throw new AppException(ErrorCode.INVALID_OCCURRENCE_TRANSITION);
        }
        occurrence.setStatus(toStatus);
        bookingEventRepository.save(bookingEventMapper.toStatusEvent(occurrence,
                BookingEventType.OCCURRENCE_STATUS_CHANGED, fromStatus, toStatus, changedBy, reason, now));
    }

    // Buổi được giao cho chính caregiver này (theo buổi, không theo caregiver cấp booking); khác → 404.
    private BookingOccurrence findAssignedOccurrence(UUID actorId, UUID id) {
        return bookingOccurrenceRepository.findWithBooking(id)
                .filter(found -> actorId.equals(found.getCaregiverUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
    }

    // Mọi buổi phải được giao cho caregiver này và thuộc cùng một booking; khác → 404 (không lộ tồn tại).
    private UUID findCommonBooking(UUID actorId, Set<UUID> occurrenceIds) {
        UUID bookingId = null;
        for (UUID occurrenceId : occurrenceIds) {
            UUID occurrenceBookingId = findAssignedOccurrence(actorId, occurrenceId).getBooking().getId();
            if (bookingId != null && !bookingId.equals(occurrenceBookingId)) {
                throw new AppException(ErrorCode.OCCURRENCE_NOT_FOUND);
            }
            bookingId = occurrenceBookingId;
        }
        return bookingId;
    }

    // Gọi khi đã khóa booking: khóa buổi, kiểm lại người được giao (có thể vừa bị đổi lúc chờ khóa) rồi SCHEDULED →
    // ACKNOWLEDGED kèm event. 2026-11-11: quên bấm nhận trước giờ vẫn nhận được tới khi đóng cửa sổ check-in (giờ bắt
    // đầu + check_in_late_minutes của booking, và trước giờ kết thúc); sau mốc đó buổi chờ job đánh MISSED.
    private BookingOccurrence markAcknowledged(UUID actorId, UUID occurrenceId, Instant now) {
        BookingOccurrence lockedOccurrence = bookingOccurrenceRepository.findForUpdate(occurrenceId)
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        if (!actorId.equals(lockedOccurrence.getCaregiverUserId())) {
            throw new AppException(ErrorCode.OCCURRENCE_NOT_FOUND);
        }
        Instant latestCheckIn = lockedOccurrence.getStartAt()
                .plus(Duration.ofMinutes(lockedOccurrence.getBooking().getCheckInLateMinutes()));
        if (lockedOccurrence.getStatus() != OccurrenceStatus.SCHEDULED || !now.isBefore(latestCheckIn)
                || !now.isBefore(lockedOccurrence.getEndAt())) {
            throw new AppException(ErrorCode.INVALID_OCCURRENCE_TRANSITION);
        }
        lockedOccurrence.setStatus(OccurrenceStatus.ACKNOWLEDGED);
        lockedOccurrence.setAcknowledgedAt(now);
        bookingEventRepository.save(bookingEventMapper.toStatusEvent(lockedOccurrence,
                BookingEventType.OCCURRENCE_ACKNOWLEDGED, OccurrenceStatus.SCHEDULED, OccurrenceStatus.ACKNOWLEDGED,
                actorId, null, now));
        return lockedOccurrence;
    }

    // ADMIN / SERVICE không giới hạn; manager cùng trung tâm (center_id trong token); khách chủ booking.
    private boolean hasFullAccess(UUID actorId, Set<RoleCode> roles, UUID centerId, Booking booking) {
        if (roles.contains(RoleCode.ADMIN) || roles.contains(RoleCode.SERVICE)) {
            return true;
        }
        if (roles.contains(RoleCode.CENTER_MANAGER) && centerId != null && centerId.equals(booking.getCenterId())) {
            return true;
        }
        return roles.contains(RoleCode.CUSTOMER) && actorId.equals(booking.getCustomerId());
    }

    // Caregiver chỉ thấy buổi được giao CHO CHÍNH BUỔI ĐÓ (không theo caregiver cấp booking) khi booking đã xác nhận.
    private boolean isAssignedCaregiver(UUID actorId, Set<RoleCode> roles, BookingOccurrence occurrence) {
        return roles.contains(RoleCode.CAREGIVER)
                && actorId.equals(occurrence.getCaregiverUserId())
                && BookingStatus.CAREGIVER_VISIBLE.contains(occurrence.getBooking().getStatus());
    }

    // Cùng quy tắc với BookingOccurrenceRepository.existsCaregiverAccess: booking còn hiệu lực và buổi còn hoạt động
    // hoặc COMPLETED chưa quá caregiverAccessGrace.
    private boolean canCaregiverSeeRecipient(BookingOccurrence occurrence) {
        if (!BookingStatus.CAREGIVER_RECIPIENT_ACCESS.contains(occurrence.getBooking().getStatus())) {
            return false;
        }
        if (OccurrenceStatus.ACTIVE.contains(occurrence.getStatus())) {
            return true;
        }
        Instant completedSince = clock.instant().minus(bookingProperties.getSchedule().getCaregiverAccessGrace());
        return occurrence.getStatus() == OccurrenceStatus.COMPLETED && occurrence.getEndAt().isAfter(completedSince);
    }

    // Identity lỗi chỉ làm thiếu SĐT (không chặn caregiver xem ca). Caregiver: đủ số khi buổi bắt đầu trong 24h tới và
    // chưa kết thúc; còn lại che giữa.
    private String resolveCustomerPhone(IdentityUserResponse customer, BookingOccurrence occurrence, boolean fullAccess) {
        String phoneNumber = customer == null ? null : customer.getPhoneNumber();
        if (phoneNumber == null || fullAccess) {
            return phoneNumber;
        }
        Instant now = clock.instant();
        boolean upcoming = occurrence.getStartAt().isBefore(now.plus(PHONE_REVEAL_WINDOW)) && occurrence.getEndAt().isAfter(now);
        return upcoming ? phoneNumber : maskPhone(phoneNumber);
    }

    private String maskPhone(String phoneNumber) {
        if (phoneNumber.length() <= PHONE_VISIBLE_DIGITS * 2) {
            return "*".repeat(phoneNumber.length());
        }
        return phoneNumber.substring(0, PHONE_VISIBLE_DIGITS)
                + "*".repeat(phoneNumber.length() - PHONE_VISIBLE_DIGITS * 2)
                + phoneNumber.substring(phoneNumber.length() - PHONE_VISIBLE_DIGITS);
    }

    private UUID findSessionId(BookingOccurrence occurrence) {
        return careSessionRepository.findByOccurrenceIdAndDeletedAtIsNull(occurrence.getId())
                .map(CareSession::getId)
                .orElse(null);
    }

    // Không @Transactional: hỏi Provider (đủ kỹ năng, có lịch làm việc, không nghỉ) qua mạng trước, rồi transaction ngắn khóa
    // booking → buổi → lịch người mới và kiểm trùng giờ với booking khác (2026-11-10 — trước đó không kiểm, có thể giao một
    // người hai nơi cùng lúc, và gọi Provider trong transaction).
    @Override
    public APIResponse<Void> emergencyReassign(UUID actorId, UUID centerId, UUID bookingId,
                                              EmergencyReassignmentRequest request) {
        Booking booking = bookingRepository.findByIdAndDeletedAtIsNull(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
        if (centerId != null && !centerId.equals(booking.getCenterId())) {
            throw new AppException(ErrorCode.BOOKING_NOT_FOUND);
        }
        Instant requestedAt = clock.instant();
        Set<UUID> requestedIds = Set.copyOf(request.getOccurrenceIds());
        List<BookingOccurrence> candidates = bookingOccurrenceRepository.findAllByBooking(bookingId).stream()
                .filter(occurrence -> requestedIds.contains(occurrence.getId()))
                .filter(occurrence -> isReplaceable(occurrence, request.getNewCaregiverId(), requestedAt))
                .toList();
        if (candidates.isEmpty()) {
            throw new AppException(ErrorCode.NO_REASSIGNABLE_OCCURRENCES);
        }
        List<UUID> careServiceIds = candidates.stream()
                .map(BookingOccurrence::getCareServiceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        AssignedCaregiver assigned = caregiverPort.resolveAssignable(booking.getCenterId(), request.getNewCaregiverId(),
                careServiceIds.isEmpty() ? findBookingServiceIds(bookingId) : careServiceIds);
        caregiverPort.checkAvailable(assigned.getCaregiverId(), occurrenceMapper.toSlotList(candidates));
        String reason = request.getReason().strip();
        List<UUID> previousCaregiverUserIds = new ArrayList<>();
        Integer replacedCount = transactionTemplate.execute(status -> {
            Booking lockedBooking = bookingRepository.findForUpdate(bookingId)
                    .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
            if (!BookingStatus.CAREGIVER_CHANGEABLE.contains(lockedBooking.getStatus())) {
                throw new AppException(ErrorCode.INVALID_BOOKING_TRANSITION);
            }
            Instant now = clock.instant();
            List<BookingOccurrence> targets = new ArrayList<>();
            for (BookingOccurrence candidate : candidates) {
                bookingOccurrenceRepository.findForUpdate(candidate.getId())
                        .filter(occurrence -> isReplaceable(occurrence, assigned.getCaregiverId(), now))
                        .ifPresent(targets::add);
            }
            if (targets.isEmpty()) {
                throw new AppException(ErrorCode.NO_REASSIGNABLE_OCCURRENCES);
            }
            caregiverScheduleGuard.lockCaregiver(assigned.getCaregiverId());
            caregiverScheduleGuard.checkCaregiverFree(assigned.getCaregiverId(), occurrenceMapper.toSlotList(targets),
                    bookingId);
            for (BookingOccurrence occurrence : targets) {
                if (occurrence.getCaregiverUserId() != null) {
                    previousCaregiverUserIds.add(occurrence.getCaregiverUserId());
                }
                bookingEventRepository.save(bookingEventMapper.toCaregiverChangedEvent(lockedBooking, occurrence.getId(),
                        occurrence.getCaregiverId(), assigned.getCaregiverId(), actorId, reason, now));
                // Đổi người qua entity: buổi đã nhận ca quay về SCHEDULED, bỏ cờ chờ đổi người, xóa mốc nhắc / cảnh báo.
                occurrence.assignCaregiver(assigned.getCaregiverId(), assigned.getCaregiverUserId());
                occurrence.setIsEmergencyReplacement(true);
                occurrence.setReplacementReason(reason);
                occurrence.setReplacementConsentStatus(ReplacementConsentStatus.PENDING_CUSTOMER);
            }
            bookingEventRepository.save(bookingEventMapper.toStatusEvent(targets.getFirst(),
                    BookingEventType.EMERGENCY_REPLACEMENT_ASSIGNED, targets.getFirst().getStatus(),
                    targets.getFirst().getStatus(), actorId, reason, now));
            notificationService.notify(List.of(lockedBooking.getCustomerId()),
                    NotificationType.EMERGENCY_REPLACEMENT_NOTIFIED,
                    Map.of("body", "Trung tâm đã bố trí người chăm sóc thay thế cho " + targets.size() + " buổi của lịch "
                            + lockedBooking.getBookingCode() + ": " + reason
                            + ". Bạn có thể đồng ý, dời buổi hoặc từ chối và được hoàn 100 %"),
                    "BOOKING", bookingId);
            notificationService.notify(List.of(assigned.getCaregiverUserId()), NotificationType.CAREGIVER_ASSIGNED,
                    Map.of("body", "Bạn được bố trí thay thế " + targets.size() + " buổi của lịch "
                            + lockedBooking.getBookingCode()), "BOOKING", bookingId);
            List<UUID> previous = previousCaregiverUserIds.stream()
                    .filter(userId -> !userId.equals(assigned.getCaregiverUserId()))
                    .distinct()
                    .toList();
            notificationService.notify(previous, NotificationType.CAREGIVER_UNASSIGNED,
                    Map.of("body", "Trung tâm đã bố trí người khác cho các buổi của lịch " + lockedBooking.getBookingCode()),
                    "BOOKING", bookingId);
            return targets.size();
        });
        log.info("Booking {}: {} occurrence(s) given to emergency replacement {} by {}", bookingId, replacedCount,
                assigned.getCaregiverId(), actorId);
        return APIResponse.success("Bố trí chuyên viên thay thế khẩn cấp thành công", null);
    }

    // Buổi thay được: chưa bắt đầu (đã lên lịch, chưa tới giờ), chưa thuộc chính người thay thế.
    private boolean isReplaceable(BookingOccurrence occurrence, UUID newCaregiverId, Instant now) {
        return OccurrenceStatus.WORKABLE.contains(occurrence.getStatus())
                && occurrence.getStartAt().isAfter(now)
                && !Objects.equals(occurrence.getCaregiverId(), newCaregiverId);
    }

    private List<UUID> findBookingServiceIds(UUID bookingId) {
        return bookingDetailRepository.findAllByBooking(bookingId).stream()
                .map(BookingDetail::getCareServiceId)
                .toList();
    }

    @Override
    @Transactional
    public APIResponse<Void> consentReplacement(UUID actorId, UUID occurrenceId,
                                                ReplacementConsentRequest request) {
        // Buổi của khách khác → 404 (không lộ tồn tại).
        BookingOccurrence snapshot = bookingOccurrenceRepository.findWithBooking(occurrenceId)
                .filter(found -> actorId.equals(found.getBooking().getCustomerId()))
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        // Khóa booking → buổi như mọi luồng hủy buổi / đổi caregiver: hai lần bấm không hủy + hoàn hai lần.
        Booking booking = lockBooking(snapshot);
        BookingOccurrence occurrence = bookingOccurrenceRepository.findForUpdate(occurrenceId)
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        if (occurrence.getReplacementConsentStatus() != ReplacementConsentStatus.PENDING_CUSTOMER) {
            throw new AppException(ErrorCode.REPLACEMENT_CONSENT_INVALID);
        }

        Instant now = clock.instant();
        String action = request.getAction() == null ? "" : request.getAction().toUpperCase();
        switch (action) {
            case "ACCEPT" -> {
                occurrence.setReplacementConsentStatus(ReplacementConsentStatus.ACCEPTED);
                bookingOccurrenceRepository.save(occurrence);
                bookingEventRepository.save(bookingEventMapper.toStatusEvent(occurrence,
                        BookingEventType.OCCURRENCE_STATUS_CHANGED, occurrence.getStatus(), occurrence.getStatus(),
                        actorId, "Khách hàng đồng ý chuyên viên thay thế", now));
            }
            case "RESCHEDULE" -> {
                checkReplacementReschedule(occurrence, request.getNewStartAt(), request.getNewEndAt(), now);
                if (occurrence.getOriginalStartAt() == null) {
                    occurrence.setOriginalStartAt(occurrence.getStartAt());
                    occurrence.setOriginalEndAt(occurrence.getEndAt());
                }
                occurrence.setStartAt(request.getNewStartAt());
                occurrence.setEndAt(request.getNewEndAt());
                occurrence.setRescheduleCount(occurrence.getRescheduleCount() + 1);
                occurrence.setReplacementConsentStatus(ReplacementConsentStatus.REJECTED_RESCHEDULE);
                occurrence.setReminderSentAt(null);
                occurrence.setLateWarnedAt(null);
                bookingOccurrenceRepository.save(occurrence);
                bookingEventRepository.save(bookingEventMapper.toStatusEvent(occurrence,
                        BookingEventType.OCCURRENCE_RESCHEDULED, occurrence.getStatus(), occurrence.getStatus(),
                        actorId, "Khách hàng từ chối người thay thế & dời lịch buổi " + occurrence.getOccurrenceNo(), now));
            }
            case "REJECT_AND_CANCEL" -> {
                occurrence.setReplacementConsentStatus(ReplacementConsentStatus.REJECTED_CANCEL);
                // Trung tâm đổi người sát giờ nên khách được hoàn 100 % (CENTER_CANCEL). Đi qua lifecycle để ghi bên hủy, giờ
                // hủy, sự kiện và thông báo; khóa hoàn tiền theo id buổi — trước 2026-11-10 khóa là một chuỗi cố định nên
                // chỉ lần từ chối đầu tiên trên toàn hệ thống được xếp hoàn tiền.
                bookingLifecycleService.cancelOccurrences(booking, List.of(occurrence), CancelledBy.CENTER, actorId,
                        REPLACEMENT_REJECTED_REASON);
                bookingLifecycleService.requestRefund(booking, List.of(occurrence.getId()),
                        RefundReason.CENTER_CANCEL, CancelledBy.CENTER, actorId, occurrence.getId().toString());
                bookingLifecycleService.finalizeIfDone(booking, actorId);
            }
            default -> throw new AppException(ErrorCode.REPLACEMENT_CONSENT_INVALID);
        }
        return APIResponse.success("Xử lý phản hồi chuyên viên thay thế thành công", null);
    }

    // Khách dời buổi thay vì nhận người thay thế khẩn cấp: không áp hạn báo trước 24h / giới hạn số lần của dời lịch
    // thường, nhưng giờ mới phải ở tương lai, cùng thời lượng (giá buổi đã chốt) và caregiver hiện tại phải rảnh.
    private void checkReplacementReschedule(BookingOccurrence occurrence, Instant newStartAt, Instant newEndAt,
                                            Instant now) {
        if (newStartAt == null || newEndAt == null || !newEndAt.isAfter(newStartAt) || !newStartAt.isAfter(now)) {
            throw new AppException(ErrorCode.INVALID_TIME_SLOT);
        }
        Duration plannedDuration = Duration.between(occurrence.getStartAt(), occurrence.getEndAt());
        if (!plannedDuration.equals(Duration.between(newStartAt, newEndAt))) {
            throw new AppException(ErrorCode.INVALID_TIME_SLOT);
        }
        if (occurrence.getCaregiverId() == null) {
            return;
        }
        boolean busy = bookingOccurrenceRepository.findCaregiverOverlaps(occurrence.getCaregiverId(), newStartAt,
                        newEndAt, OccurrenceStatus.ACTIVE, BookingStatus.SCHEDULE_HOLDING).stream()
                .anyMatch(other -> !other.getId().equals(occurrence.getId()));
        if (busy) {
            throw new AppException(ErrorCode.CAREGIVER_NOT_AVAILABLE);
        }
    }

    private void validateReschedule(BookingOccurrence occurrence, Instant newStartAt, Instant newEndAt, Instant now) {
        if (occurrence.getStatus() != OccurrenceStatus.SCHEDULED && occurrence.getStatus() != OccurrenceStatus.ACKNOWLEDGED) {
            throw new AppException(ErrorCode.INVALID_OCCURRENCE_TRANSITION);
        }
        if (newStartAt == null || newEndAt == null || !newEndAt.isAfter(newStartAt)) {
            throw new AppException(ErrorCode.INVALID_TIME_SLOT);
        }
        if (!occurrence.getStartAt().isAfter(now.plus(Duration.ofHours(24)))) {
            throw new AppException(ErrorCode.RESCHEDULE_LEAD_TIME_TOO_SHORT);
        }
        int totalOccurrences = bookingOccurrenceRepository.findAllByBooking(occurrence.getBooking().getId()).size();
        int limit = totalOccurrences >= 20 ? 4 : 2;
        if (occurrence.getRescheduleCount() != null && occurrence.getRescheduleCount() >= limit) {
            throw new AppException(ErrorCode.RESCHEDULE_LIMIT_REACHED);
        }
        if (!newStartAt.isAfter(now.plus(Duration.ofHours(12)))) {
            throw new AppException(ErrorCode.INVALID_TIME_SLOT);
        }
        Duration originalDuration = Duration.between(occurrence.getStartAt(), occurrence.getEndAt());
        Duration newDuration = Duration.between(newStartAt, newEndAt);
        if (!originalDuration.equals(newDuration)) {
            throw new AppException(ErrorCode.INVALID_TIME_SLOT);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<OccurrenceRescheduleResponse> reschedulePreview(UUID actorId, Set<RoleCode> roles, UUID centerId,
                                                                      UUID occurrenceId,
                                                                      OccurrenceReschedulePreviewRequest request) {
        BookingOccurrence occurrence = bookingOccurrenceRepository.findWithBooking(occurrenceId)
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        Booking booking = occurrence.getBooking();
        if (!hasFullAccess(actorId, roles, centerId, booking)) {
            throw new AppException(ErrorCode.OCCURRENCE_NOT_FOUND);
        }
        Instant now = clock.instant();
        validateReschedule(occurrence, request.getNewStartAt(), request.getNewEndAt(), now);

        boolean busy = false;
        if (occurrence.getCaregiverId() != null) {
            List<BookingOccurrence> overlaps = bookingOccurrenceRepository.findCaregiverOverlaps(
                    occurrence.getCaregiverId(), request.getNewStartAt(), request.getNewEndAt(),
                    OccurrenceStatus.ACTIVE, BookingStatus.SCHEDULE_HOLDING);
            busy = overlaps.stream().anyMatch(o -> !o.getId().equals(occurrence.getId()));
        }

        OccurrenceRescheduleResponse res = OccurrenceRescheduleResponse.builder()
                .occurrenceId(occurrence.getId())
                .bookingId(booking.getId())
                .originalStartAt(occurrence.getOriginalStartAt() != null ? occurrence.getOriginalStartAt() : occurrence.getStartAt())
                .originalEndAt(occurrence.getOriginalEndAt() != null ? occurrence.getOriginalEndAt() : occurrence.getEndAt())
                .newStartAt(request.getNewStartAt())
                .newEndAt(request.getNewEndAt())
                .rescheduleCount(occurrence.getRescheduleCount())
                .available(!busy)
                .message(!busy ? "Khung giờ mới khả dụng" : "Chuyên viên đã có lịch khác trong khung giờ này")
                .build();
        return APIResponse.success("Kiểm tra dời lịch thành công", res);
    }

    @Override
    @Transactional
    public APIResponse<OccurrenceRescheduleResponse> reschedule(UUID actorId, Set<RoleCode> roles, UUID centerId,
                                                               UUID occurrenceId,
                                                               OccurrenceRescheduleRequest request) {
        BookingOccurrence occurrence = bookingOccurrenceRepository.findWithBooking(occurrenceId)
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        Booking booking = occurrence.getBooking();
        if (!hasFullAccess(actorId, roles, centerId, booking)) {
            throw new AppException(ErrorCode.OCCURRENCE_NOT_FOUND);
        }
        Instant now = clock.instant();
        validateReschedule(occurrence, request.getNewStartAt(), request.getNewEndAt(), now);

        if (occurrence.getCaregiverId() != null) {
            List<BookingOccurrence> overlaps = bookingOccurrenceRepository.findCaregiverOverlaps(
                    occurrence.getCaregiverId(), request.getNewStartAt(), request.getNewEndAt(),
                    OccurrenceStatus.ACTIVE, BookingStatus.SCHEDULE_HOLDING);
            boolean busy = overlaps.stream().anyMatch(o -> !o.getId().equals(occurrence.getId()));
            if (busy) {
                throw new AppException(ErrorCode.CAREGIVER_NOT_AVAILABLE);
            }
        }

        if (occurrence.getOriginalStartAt() == null) {
            occurrence.setOriginalStartAt(occurrence.getStartAt());
            occurrence.setOriginalEndAt(occurrence.getEndAt());
        }
        occurrence.setStartAt(request.getNewStartAt());
        occurrence.setEndAt(request.getNewEndAt());
        occurrence.setRescheduleCount(occurrence.getRescheduleCount() + 1);
        occurrence.setStatus(OccurrenceStatus.SCHEDULED);
        occurrence.setAcknowledgedAt(null);
        occurrence.setReminderSentAt(null);
        occurrence.setLateWarnedAt(null);
        bookingOccurrenceRepository.save(occurrence);

        String note = request.getReason() != null ? request.getReason() : "Dời lịch buổi " + occurrence.getOccurrenceNo();
        bookingEventRepository.save(bookingEventMapper.toStatusEvent(occurrence,
                BookingEventType.OCCURRENCE_RESCHEDULED, occurrence.getStatus(), occurrence.getStatus(),
                actorId, note, now));

        notificationService.notify(
                List.of(booking.getCustomerId()),
                NotificationType.OCCURRENCE_RESCHEDULED_NOTIFICATION,
                Map.of("body", "Buổi chăm sóc #" + occurrence.getOccurrenceNo() + " đã được dời lịch thành công."),
                "BOOKING",
                booking.getId());

        OccurrenceRescheduleResponse res = OccurrenceRescheduleResponse.builder()
                .occurrenceId(occurrence.getId())
                .bookingId(booking.getId())
                .originalStartAt(occurrence.getOriginalStartAt())
                .originalEndAt(occurrence.getOriginalEndAt())
                .newStartAt(occurrence.getStartAt())
                .newEndAt(occurrence.getEndAt())
                .rescheduleCount(occurrence.getRescheduleCount())
                .available(true)
                .message("Dời lịch thành công")
                .build();
        return APIResponse.success("Dời lịch buổi chăm sóc thành công", res);
    }
}
