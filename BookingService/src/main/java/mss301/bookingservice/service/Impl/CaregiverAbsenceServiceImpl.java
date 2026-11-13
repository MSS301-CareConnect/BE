package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.request.CaregiverAbsenceConclusionRequest;
import mss301.bookingservice.dto.request.CaregiverAbsenceExplanationRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CaregiverAbsenceResponse;
import mss301.bookingservice.dto.response.CaregiverAbsenceStatisticsResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.entity.CaregiverAbsence;
import mss301.bookingservice.enums.CaregiverAbsenceSource;
import mss301.bookingservice.enums.CaregiverAbsenceStatus;
import mss301.bookingservice.enums.NotificationType;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.CaregiverAbsenceMapper;
import mss301.bookingservice.repository.CaregiverAbsenceRepository;
import mss301.bookingservice.repository.projection.CaregiverAbsenceStatusCountView;
import mss301.bookingservice.service.BookingNotificationService;
import mss301.bookingservice.service.CaregiverAbsenceService;
import mss301.bookingservice.service.NotificationService;
import mss301.bookingservice.util.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CaregiverAbsenceServiceImpl implements CaregiverAbsenceService {

    static Instant UNBOUNDED_START = Instant.parse("1970-01-01T00:00:00Z");
    static Instant UNBOUNDED_END = Instant.parse("9999-12-31T00:00:00Z");
    static DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    static String OCCURRENCE_REF = "BOOKING_OCCURRENCE";

    CaregiverAbsenceRepository caregiverAbsenceRepository;
    CaregiverAbsenceMapper caregiverAbsenceMapper;
    NotificationService notificationService;
    BookingNotificationService bookingNotificationService;
    BookingProperties bookingProperties;
    Clock clock;

    // Danh sách lần vắng mặt theo phạm vi người gọi và bộ lọc.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CaregiverAbsenceResponse>> list(UUID actorId, Set<RoleCode> roles,
                                                                    UUID scopeCenterId, CaregiverAbsenceStatus status,
                                                                    UUID centerId, UUID caregiverId, UUID bookingId,
                                                                    LocalDate from, LocalDate to, int page, int size) {
        PageRequest pageRequest = PageRequests.unsorted(page, size);
        Scope scope = resolveScope(actorId, roles, scopeCenterId, centerId, caregiverId);
        Range range = resolveRange(from, to);
        Page<CaregiverAbsence> absences = caregiverAbsenceRepository.findPage(scope.centerId(), scope.caregiverUserId(),
                scope.caregiverId(), bookingId, status, range.from(), range.to(), pageRequest);
        PageResponse<CaregiverAbsenceResponse> absenceResponses = PageResponse.of(absences,
                caregiverAbsenceMapper.toResponseList(absences.getContent()));
        return APIResponse.success("Lấy danh sách vắng ca thành công", absenceResponses);
    }

    // Chi tiết một lần vắng mặt trong phạm vi người gọi.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverAbsenceResponse> get(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId, UUID id) {
        CaregiverAbsence absence = caregiverAbsenceRepository.findWithOccurrence(id)
                .filter(found -> canRead(actorId, roles, scopeCenterId, found))
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_ABSENCE_NOT_FOUND));
        CaregiverAbsenceResponse absenceResponse = caregiverAbsenceMapper.toResponse(absence);
        return APIResponse.success("Lấy hồ sơ vắng ca thành công", absenceResponse);
    }

    // Thống kê vắng mặt theo trạng thái trong khoảng thời gian.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverAbsenceStatisticsResponse> getStatistics(UUID actorId, Set<RoleCode> roles,
                                                                         UUID scopeCenterId, UUID centerId,
                                                                         UUID caregiverId, LocalDate from,
                                                                         LocalDate to) {
        Scope scope = resolveScope(actorId, roles, scopeCenterId, centerId, caregiverId);
        Range range = resolveRange(from, to);
        Map<CaregiverAbsenceStatus, Long> totals = new EnumMap<>(CaregiverAbsenceStatus.class);
        for (CaregiverAbsenceStatusCountView count : caregiverAbsenceRepository.countByStatus(scope.centerId(),
                scope.caregiverUserId(), scope.caregiverId(), range.from(), range.to())) {
            totals.put(count.getStatus(), count.getTotal());
        }
        long pending = totals.getOrDefault(CaregiverAbsenceStatus.PENDING, 0L);
        long excused = totals.getOrDefault(CaregiverAbsenceStatus.EXCUSED, 0L);
        long confirmed = totals.getOrDefault(CaregiverAbsenceStatus.CONFIRMED, 0L);
        CaregiverAbsenceStatisticsResponse statisticsResponse = caregiverAbsenceMapper.toStatisticsResponse(pending,
                excused, confirmed, pending + excused + confirmed);
        return APIResponse.success("Thống kê vắng ca thành công", statisticsResponse);
    }

    // Caregiver gửi giải trình cho lần vắng mặt đang chờ.
    @Override
    @Transactional
    public APIResponse<CaregiverAbsenceResponse> explain(UUID actorId, UUID id,
                                                         CaregiverAbsenceExplanationRequest request) {
        // Hồ sơ của người khác → 404 (không lộ tồn tại).
        CaregiverAbsence absence = caregiverAbsenceRepository.findForUpdate(id)
                .filter(found -> actorId.equals(found.getCaregiverUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_ABSENCE_NOT_FOUND));
        if (absence.getStatus() != CaregiverAbsenceStatus.PENDING) {
            throw new AppException(ErrorCode.CAREGIVER_ABSENCE_ALREADY_CONCLUDED);
        }
        absence.setExplanation(request.getExplanation().strip());
        absence.setExplainedAt(clock.instant());
        String body = "Người chăm sóc đã gửi giải trình vắng " + describeVisit(absence.getOccurrence())
                + " — vui lòng xem và kết luận";
        bookingNotificationService.notifyCenter(absence.getCenterId(), NotificationType.CAREGIVER_ABSENCE_UPDATED,
                Map.of("body", body), OCCURRENCE_REF, absence.getOccurrence().getId());
        log.info("Caregiver {} explained absence {}", actorId, id);
        CaregiverAbsenceResponse absenceResponse = caregiverAbsenceMapper.toResponse(absence);
        return APIResponse.success("Đã gửi giải trình cho trung tâm", absenceResponse);
    }

    // Trung tâm kết luận lần vắng mặt (có / không chấp nhận lý do).
    @Override
    @Transactional
    public APIResponse<CaregiverAbsenceResponse> conclude(UUID actorId, UUID centerId, UUID id,
                                                          CaregiverAbsenceConclusionRequest request) {
        CaregiverAbsenceStatus decision = request.getDecision();
        if (decision == CaregiverAbsenceStatus.PENDING) {
            throw new AppException(ErrorCode.ABSENCE_DECISION_REQUIRED);
        }
        String note = request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().strip();
        if (decision == CaregiverAbsenceStatus.EXCUSED && note == null) {
            throw new AppException(ErrorCode.ABSENCE_CONCLUSION_NOTE_REQUIRED);
        }
        // Ngoài trung tâm của manager → 404 (không lộ tồn tại).
        CaregiverAbsence absence = caregiverAbsenceRepository.findForUpdate(id)
                .filter(found -> centerId != null && centerId.equals(found.getCenterId()))
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_ABSENCE_NOT_FOUND));
        if (absence.getStatus() != CaregiverAbsenceStatus.PENDING) {
            throw new AppException(ErrorCode.CAREGIVER_ABSENCE_ALREADY_CONCLUDED);
        }
        absence.setStatus(decision);
        absence.setConclusionNote(note);
        absence.setConcludedBy(actorId);
        absence.setConcludedAt(clock.instant());
        String visit = describeVisit(absence.getOccurrence());
        String body = decision == CaregiverAbsenceStatus.EXCUSED
                ? "Trung tâm ghi nhận bạn vắng " + visit + " có lý do chính đáng, không tính vi phạm"
                : "Trung tâm kết luận bạn vắng " + visit + " không có lý do chính đáng — đã ghi một lần vi phạm";
        notificationService.notify(List.of(absence.getCaregiverUserId()), NotificationType.CAREGIVER_ABSENCE_UPDATED,
                Map.of("body", body), OCCURRENCE_REF, absence.getOccurrence().getId());
        log.info("Absence {} concluded {} by manager {}", id, decision, actorId);
        CaregiverAbsenceResponse absenceResponse = caregiverAbsenceMapper.toResponse(absence);
        return APIResponse.success("Đã kết luận lần vắng ca", absenceResponse);
    }

    // Ghi một lần vắng mặt của caregiver cho buổi và báo các bên.
    @Override
    public void record(BookingOccurrence occurrence, CaregiverAbsenceSource source, Instant refundAt) {
        Booking booking = occurrence.getBooking();
        String visit = describeVisit(occurrence);
        // 2026-11-13: khoản hoàn chờ tới khi xong các buổi trong ngày (refundAt) để trung tâm kịp ghi nhận check-in hộ
        // nếu caregiver đã làm mà quên bấm.
        String refundTime = TIME_FORMAT.withZone(bookingProperties.getSchedule().getTimezone()).format(refundAt);
        // Khách không cần biết chuyện kỷ luật — chỉ biết buổi chưa được ghi nhận và khi nào tiền được hoàn (PaymentService
        // hoàn 100 % vào ví cho lý do CAREGIVER_MISSED).
        String customerBody = "Chưa có người chăm sóc check-in " + visit + ". Nếu trung tâm xác nhận buổi không diễn ra,"
                + " toàn bộ số tiền bạn đã trả cho buổi này được hoàn vào ví CareConnect lúc " + refundTime;
        notificationService.notify(List.of(booking.getCustomerId()), NotificationType.OCCURRENCE_MISSED,
                Map.of("body", customerBody), OCCURRENCE_REF, occurrence.getId());
        boolean recordable = occurrence.getCaregiverId() != null
                && !caregiverAbsenceRepository.existsByOccurrenceIdAndDeletedAtIsNull(occurrence.getId());
        if (!recordable) {
            bookingNotificationService.notifyCenter(booking.getCenterId(), NotificationType.OCCURRENCE_MISSED,
                    Map.of("body", "Không có người chăm sóc check-in " + visit + ", khách được hoàn 100 % lúc " + refundTime),
                    OCCURRENCE_REF, occurrence.getId());
            return;
        }
        CaregiverAbsence absence = caregiverAbsenceRepository.save(caregiverAbsenceMapper.toCaregiverAbsence(occurrence,
                source, CaregiverAbsenceStatus.PENDING));
        String caregiverBody = "Bạn bị ghi nhận vắng " + visit + ". Nếu bạn đã làm buổi này mà quên check-in, hãy gửi"
                + " giải trình trước " + refundTime + " để trung tâm ghi nhận check-in hộ";
        notificationService.notify(List.of(occurrence.getCaregiverUserId()), NotificationType.CAREGIVER_ABSENCE_UPDATED,
                Map.of("body", caregiverBody), OCCURRENCE_REF, occurrence.getId());
        String centerBody = "Người chăm sóc vắng " + visit + ", khách được hoàn 100 % lúc " + refundTime + " — xem giải"
                + " trình; nếu người chăm sóc đã làm thì ghi nhận check-in hộ trước giờ đó";
        bookingNotificationService.notifyCenter(booking.getCenterId(), NotificationType.CAREGIVER_ABSENCE_UPDATED,
                Map.of("body", centerBody), OCCURRENCE_REF, occurrence.getId());
        log.warn("Caregiver {} recorded absent for occurrence {} ({}) — absence {}", occurrence.getCaregiverId(),
                occurrence.getId(), source, absence.getId());
    }

    // Hồ sơ vắng của buổi (nếu có) thành EXCUSED: caregiver thực ra đã làm buổi.
    @Override
    public void excuseByCenterCheckIn(BookingOccurrence occurrence, UUID actorId, String note) {
        caregiverAbsenceRepository.findByOccurrenceIdAndDeletedAtIsNull(occurrence.getId())
                .flatMap(found -> caregiverAbsenceRepository.findForUpdate(found.getId()))
                .ifPresent(absence -> {
                    absence.setStatus(CaregiverAbsenceStatus.EXCUSED);
                    absence.setConclusionNote(note);
                    absence.setConcludedBy(actorId);
                    absence.setConcludedAt(clock.instant());
                    notificationService.notify(List.of(absence.getCaregiverUserId()),
                            NotificationType.CAREGIVER_ABSENCE_UPDATED,
                            Map.of("body", "Trung tâm đã ghi nhận check-in hộ " + describeVisit(occurrence)
                                    + ", không tính vắng. Mở ca để làm checklist và check-out"),
                            OCCURRENCE_REF, occurrence.getId());
                });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    // ADMIN: mọi trung tâm (lọc tùy chọn); CENTER_MANAGER: trung tâm trong token (bỏ qua centerId truyền lên); CAREGIVER:
    // chỉ hồ sơ của chính mình (bỏ qua mọi bộ lọc caregiver / trung tâm).
    private Scope resolveScope(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId, UUID centerId, UUID caregiverId) {
        if (roles.contains(RoleCode.ADMIN)) {
            return new Scope(centerId, null, caregiverId);
        }
        if (roles.contains(RoleCode.CENTER_MANAGER)) {
            if (scopeCenterId == null) {
                throw new AppException(ErrorCode.CENTER_SCOPE_REQUIRED);
            }
            return new Scope(scopeCenterId, null, caregiverId);
        }
        return new Scope(null, actorId, null);
    }

    // Người gọi được xem lần vắng mặt này.
    private boolean canRead(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId, CaregiverAbsence absence) {
        if (roles.contains(RoleCode.ADMIN)) {
            return true;
        }
        if (roles.contains(RoleCode.CENTER_MANAGER)) {
            return scopeCenterId != null && scopeCenterId.equals(absence.getCenterId());
        }
        return roles.contains(RoleCode.CAREGIVER) && actorId.equals(absence.getCaregiverUserId());
    }

    // Ngày theo múi giờ booking; bỏ trống một đầu = không giới hạn đầu đó.
    private Range resolveRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new AppException(ErrorCode.INVALID_DATE_FILTER);
        }
        ZoneId zone = bookingProperties.getSchedule().getTimezone();
        Instant rangeStart = from == null ? UNBOUNDED_START : from.atStartOfDay(zone).toInstant();
        Instant rangeEnd = to == null ? UNBOUNDED_END : to.plusDays(1).atStartOfDay(zone).toInstant();
        return new Range(rangeStart, rangeEnd);
    }

    // Mô tả ngắn buổi (số thứ tự + giờ bắt đầu) cho thông báo.
    private String describeVisit(BookingOccurrence occurrence) {
        return "buổi " + occurrence.getOccurrenceNo() + " ("
                + TIME_FORMAT.withZone(bookingProperties.getSchedule().getTimezone()).format(occurrence.getStartAt())
                + ") của lịch " + occurrence.getBooking().getBookingCode();
    }

    // Phạm vi dữ liệu người gọi được xem: trung tâm, caregiver.
    private record Scope(UUID centerId, UUID caregiverUserId, UUID caregiverId) {
    }

    // Khoảng thời gian thống kê.
    private record Range(Instant from, Instant to) {
    }
}
