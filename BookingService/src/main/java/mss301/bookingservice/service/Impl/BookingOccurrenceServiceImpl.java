package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.dto.IdentityUserResponse;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.request.BusyCaregiverRequest;
import mss301.bookingservice.dto.request.NoShowDecisionRequest;
import mss301.bookingservice.dto.request.NoShowReportRequest;
import mss301.bookingservice.dto.request.TimeSlotRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BusyCaregiverResponse;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingDetail;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.entity.CareSession;
import mss301.bookingservice.enums.BookingEventType;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.CancelledBy;
import mss301.bookingservice.enums.MissedReason;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.RefundReason;
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
import mss301.bookingservice.service.IdentityUserService;
import mss301.bookingservice.util.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BookingOccurrenceServiceImpl implements BookingOccurrenceService {

    // Caregiver chỉ thấy đủ SĐT khách khi buổi sắp diễn ra (OPS-01); trước đó chỉ cần biết có liên hệ được.
    static Duration PHONE_REVEAL_WINDOW = Duration.ofHours(24);
    static int PHONE_VISIBLE_DIGITS = 2;
    static int SCAN_BATCH_SIZE = 100;
    static Instant UNBOUNDED_START = Instant.parse("1970-01-01T00:00:00Z");
    static Instant UNBOUNDED_END = Instant.parse("9999-12-31T00:00:00Z");
    static String MISSED_BY_JOB_REASON = "Người chăm sóc không check-in đúng hạn";

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

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CaregiverOccurrenceResponse>> list(UUID actorId, LocalDate from, LocalDate to,
                                                                       OccurrenceStatus status, int page, int size) {
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
                rangeEnd, statuses, BookingStatus.CAREGIVER_VISIBLE, pageRequest);
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
        List<BookingDetail> services = bookingDetailRepository.findAllByBooking(booking.getId());
        String customerPhone = resolveCustomerPhone(booking, occurrence, fullAccess);
        occurrenceMapper.updateDetail(services, customerPhone, findSessionId(occurrence), occurrenceResponse);
        return APIResponse.success("Lấy chi tiết buổi chăm sóc thành công", occurrenceResponse);
    }

    @Override
    public APIResponse<CaregiverOccurrenceResponse> acknowledge(UUID actorId, UUID id) {
        BookingOccurrence occurrence = findAssignedOccurrence(actorId, id);
        BookingOccurrence acknowledgedOccurrence = transactionTemplate.execute(status -> {
            // Khóa booking trước rồi mới tới buổi — cùng thứ tự với đổi trạng thái booking / đổi caregiver.
            Booking lockedBooking = bookingRepository.findForUpdate(occurrence.getBooking().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
            BookingOccurrence lockedOccurrence = bookingOccurrenceRepository.findForUpdate(id)
                    .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
            // Có thể vừa bị đổi caregiver trong lúc chờ khóa.
            if (!actorId.equals(lockedOccurrence.getCaregiverUserId())) {
                throw new AppException(ErrorCode.OCCURRENCE_NOT_FOUND);
            }
            if (!BookingStatus.CAREGIVER_RECIPIENT_ACCESS.contains(lockedBooking.getStatus())) {
                throw new AppException(ErrorCode.BOOKING_NOT_CONFIRMED);
            }
            Instant now = clock.instant();
            if (lockedOccurrence.getStatus() != OccurrenceStatus.SCHEDULED || !lockedOccurrence.getStartAt().isAfter(now)) {
                throw new AppException(ErrorCode.INVALID_OCCURRENCE_TRANSITION);
            }
            lockedOccurrence.setStatus(OccurrenceStatus.ACKNOWLEDGED);
            lockedOccurrence.setAcknowledgedAt(now);
            bookingEventRepository.save(bookingEventMapper.toStatusEvent(lockedOccurrence,
                    BookingEventType.OCCURRENCE_ACKNOWLEDGED, OccurrenceStatus.SCHEDULED, OccurrenceStatus.ACKNOWLEDGED,
                    actorId, null, now));
            return lockedOccurrence;
        });
        log.info("Occurrence {} acknowledged by caregiver {}", id, actorId);
        CaregiverOccurrenceResponse occurrenceResponse = occurrenceMapper.toCaregiverResponse(acknowledgedOccurrence);
        return APIResponse.success("Đã xác nhận nhận ca", occurrenceResponse);
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
        // Đã check-in thì buổi không còn NOT_STARTED — khách có mặt, không báo vắng được.
        if (!OccurrenceStatus.NOT_STARTED.contains(lockedOccurrence.getStatus())) {
            throw new AppException(ErrorCode.INVALID_OCCURRENCE_TRANSITION);
        }
        if (lockedOccurrence.getNoShowReportedAt() != null) {
            throw new AppException(ErrorCode.NO_SHOW_ALREADY_REPORTED);
        }
        Instant now = clock.instant();
        Instant reportDeadline = lockedOccurrence.getEndAt().plus(bookingProperties.getSession().getNoShowReportWindow());
        if (now.isBefore(lockedOccurrence.getStartAt()) || !now.isBefore(reportDeadline)) {
            throw new AppException(ErrorCode.NO_SHOW_WINDOW_INVALID);
        }
        lockedOccurrence.setNoShowReportedAt(now);
        lockedOccurrence.setNoShowReportedBy(actorId);
        lockedOccurrence.setNoShowNote(request.getNote());
        bookingEventRepository.save(bookingEventMapper.toStatusEvent(lockedOccurrence, BookingEventType.NO_SHOW_REPORTED,
                lockedOccurrence.getStatus(), lockedOccurrence.getStatus(), actorId, request.getNote(), now));
        // TODO(NTF-01, Người C): thông báo manager trung tâm NO_SHOW_REPORTED.
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
                || !OccurrenceStatus.NOT_STARTED.contains(lockedOccurrence.getStatus())) {
            throw new AppException(ErrorCode.INVALID_OCCURRENCE_TRANSITION);
        }
        Instant now = clock.instant();
        lockedOccurrence.setNoShowDecidedBy(actorId);
        lockedOccurrence.setNoShowDecidedAt(now);
        if (Boolean.TRUE.equals(request.getApproved())) {
            // Khách chịu phí buổi theo chính sách snapshot — PaymentService tính ở hóa đơn phần còn lại (FINALIZE).
            changeStatus(lockedOccurrence, OccurrenceStatus.CUSTOMER_NO_SHOW, actorId, request.getNote(), now);
        } else {
            markMissedByCaregiver(booking, lockedOccurrence, CancelledBy.CENTER, actorId, request.getNote(), now);
        }
        bookingLifecycleService.finalizeIfDone(booking, actorId);
        // TODO(NTF-01, Người C): thông báo khách kết quả xử lý báo vắng.
        log.info("Occurrence {} no-show report {} by manager {}", id,
                Boolean.TRUE.equals(request.getApproved()) ? "approved" : "rejected", actorId);
        CaregiverOccurrenceResponse occurrenceResponse = occurrenceMapper.toCaregiverResponse(lockedOccurrence);
        return APIResponse.success("Đã xử lý báo cáo khách vắng mặt", occurrenceResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findMissedIds() {
        Instant startedBefore = clock.instant().minus(bookingProperties.getSession().getMissedGrace());
        return bookingOccurrenceRepository.findMissedIds(OccurrenceStatus.NOT_STARTED,
                BookingStatus.CAREGIVER_RECIPIENT_ACCESS, startedBefore, PageRequest.of(0, SCAN_BATCH_SIZE));
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
                && OccurrenceStatus.NOT_STARTED.contains(lockedOccurrence.getStatus())
                && lockedOccurrence.getNoShowReportedAt() == null
                && !careSessionRepository.existsByOccurrenceIdAndDeletedAtIsNull(id)
                && !lockedOccurrence.getStartAt().plus(bookingProperties.getSession().getMissedGrace()).isAfter(now);
        if (!stillMissed) {
            return;
        }
        markMissedByCaregiver(booking, lockedOccurrence, CancelledBy.SYSTEM, null, MISSED_BY_JOB_REASON, now);
        bookingLifecycleService.finalizeIfDone(booking, null);
        // TODO(NTF-01, Người C): thông báo manager + khách buổi MISSED (đã tự hoàn tiền).
        log.warn("Occurrence {} marked MISSED — no check-in within the grace period", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findUndecidedNoShowIds() {
        Instant endedBefore = clock.instant().minus(bookingProperties.getSession().getNoShowDecisionReminderAfter());
        return bookingOccurrenceRepository.findUndecidedNoShowIds(OccurrenceStatus.NOT_STARTED, endedBefore,
                PageRequest.of(0, SCAN_BATCH_SIZE));
    }

    @Override
    @Transactional
    public void remindNoShowDecision(UUID id) {
        bookingOccurrenceRepository.findForUpdate(id)
                .filter(occurrence -> occurrence.getNoShowReportedAt() != null)
                .filter(occurrence -> occurrence.getNoShowDecidedAt() == null)
                .filter(occurrence -> occurrence.getNoShowRemindedAt() == null)
                .ifPresent(occurrence -> {
                    occurrence.setNoShowRemindedAt(clock.instant());
                    // TODO(NTF-01, Người C): nhắc manager trung tâm quyết định báo vắng (không tự quyết).
                    log.warn("Occurrence {} has an undecided no-show report — reminder recorded", id);
                });
    }

    // Khóa booking trước rồi mới tới buổi — cùng thứ tự với đổi trạng thái booking / đổi caregiver.
    private Booking lockBooking(BookingOccurrence occurrence) {
        return bookingRepository.findForUpdate(occurrence.getBooking().getId())
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
    }

    // MISSED do caregiver → hoàn 100% cọc buổi qua outbox; khóa theo id buổi nên job và manager không hoàn hai lần.
    private void markMissedByCaregiver(Booking booking, BookingOccurrence occurrence, CancelledBy cancelledBy,
                                       UUID changedBy, String reason, Instant now) {
        changeStatus(occurrence, OccurrenceStatus.MISSED, changedBy, reason, now);
        occurrence.setMissedReason(MissedReason.CAREGIVER);
        bookingLifecycleService.requestRefund(booking, List.of(occurrence.getId()), RefundReason.CAREGIVER_MISSED,
                cancelledBy, changedBy, occurrence.getId().toString());
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
    private String resolveCustomerPhone(Booking booking, BookingOccurrence occurrence, boolean fullAccess) {
        IdentityUserResponse customer = identityUserService.getProfiles(List.of(booking.getCustomerId()))
                .get(booking.getCustomerId());
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
}
