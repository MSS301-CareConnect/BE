package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.request.CareSessionEndRequest;
import mss301.bookingservice.dto.request.CareSessionForceEndRequest;
import mss301.bookingservice.dto.request.CareSessionStartRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.request.CareTaskCreateRequest;
import mss301.bookingservice.dto.request.CareTaskUpdateRequest;
import mss301.bookingservice.dto.request.SessionNoteRequest;
import mss301.bookingservice.dto.response.CareTaskResponse;
import mss301.bookingservice.dto.response.SessionNoteResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.dto.response.CareSessionResponse;
import mss301.bookingservice.dto.response.RecipientSnapshot;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingDetail;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.entity.CareSession;
import mss301.bookingservice.entity.CareTask;
import mss301.bookingservice.enums.BookingAction;
import mss301.bookingservice.enums.BookingEventType;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.CareSessionStatus;
import mss301.bookingservice.enums.CareTaskStatus;
import mss301.bookingservice.enums.CareTaskType;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.BookingEventMapper;
import mss301.bookingservice.mapper.CareSessionMapper;
import mss301.bookingservice.repository.BookingDetailRepository;
import mss301.bookingservice.repository.BookingEventRepository;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.repository.CareSessionRepository;
import mss301.bookingservice.repository.CareTaskRepository;
import mss301.bookingservice.service.BookingLifecycleService;
import mss301.bookingservice.service.CareSessionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import mss301.bookingservice.entity.CareSessionNote;
import mss301.bookingservice.repository.CareSessionNoteRepository;
import mss301.bookingservice.service.NotificationService;
import mss301.bookingservice.service.BookingNotificationService;
import mss301.bookingservice.enums.NotificationType;
import mss301.bookingservice.dto.response.SessionUpdateResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CareSessionServiceImpl implements CareSessionService {

    static String AUTO_CLOSE_REASON = "Hệ thống tự đóng ca: quá 4 giờ sau giờ kết thúc mà chưa check-out";

    static String FORCE_END_SKIP_REASON = "Buộc kết thúc bởi trung tâm";
    static DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    static int OVERDUE_BATCH_SIZE = 100;

    CareSessionRepository careSessionRepository;
    CareTaskRepository careTaskRepository;
    BookingRepository bookingRepository;
    BookingOccurrenceRepository bookingOccurrenceRepository;
    BookingDetailRepository bookingDetailRepository;
    BookingEventRepository bookingEventRepository;
    CareSessionMapper careSessionMapper;
    BookingEventMapper bookingEventMapper;
    BookingLifecycleService bookingLifecycleService;
    BookingProperties bookingProperties;
    TransactionTemplate transactionTemplate;
    Clock clock;
    NotificationService notificationService;
    BookingNotificationService bookingNotificationService;
    CareSessionNoteRepository careSessionNoteRepository;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CareSessionResponse> get(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId, UUID id) {
        CareSession session = careSessionRepository.findWithBooking(id)
                .filter(found -> canRead(actorId, roles, scopeCenterId, found))
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
        CareSessionResponse careSessionResponse = careSessionMapper.toResponse(session,
                careTaskRepository.findAllBySession(id));
        return APIResponse.success("Lấy ca chăm sóc thành công", careSessionResponse);
    }

    @Override
    public APIResponse<CareSessionResponse> start(UUID actorId, CareSessionStartRequest request) {
        BookingOccurrence occurrence = bookingOccurrenceRepository.findWithBooking(request.getOccurrenceId())
                .filter(found -> actorId.equals(found.getCaregiverUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        CareSessionResponse careSessionResponse;
        try {
            careSessionResponse = transactionTemplate.execute(status ->
                    startInTransaction(actorId, request, occurrence.getBooking().getId()));
        } catch (DataIntegrityViolationException e) {
            // Hai lần check-in song song: UNIQUE(occurrence_id) chặn lần thứ hai.
            throw new AppException(ErrorCode.SESSION_ALREADY_EXISTS);
        }

        log.info("Care session {} started for occurrence {} by caregiver {}", careSessionResponse.getSessionId(),
                request.getOccurrenceId(), actorId);
        return APIResponse.success("Bắt đầu ca chăm sóc thành công", careSessionResponse);
    }

    @Override
    public APIResponse<CareSessionResponse> end(UUID actorId, UUID id, CareSessionEndRequest request) {
        CareSession session = careSessionRepository.findWithBooking(id)
                .filter(found -> actorId.equals(found.getCaregiverUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
        CareSessionResponse careSessionResponse = transactionTemplate.execute(status -> {
            CareSession lockedSession = lockSession(session);
            if (careTaskRepository.existsBySessionIdAndStatusAndDeletedAtIsNull(id, CareTaskStatus.PENDING)) {
                throw new AppException(ErrorCode.TASKS_PENDING);
            }
            // Body tùy chọn: check-out không gửi tọa độ vẫn hợp lệ.
            if (request != null) {
                lockedSession.setCheckOutLatitude(request.getLatitude());
                lockedSession.setCheckOutLongitude(request.getLongitude());
            }
            closeSession(lockedSession, actorId, null);
            return careSessionMapper.toResponse(lockedSession, careTaskRepository.findAllBySession(id));
        });
        log.info("Care session {} ended by caregiver {}", id, actorId);
        return APIResponse.success("Kết thúc ca chăm sóc thành công", careSessionResponse);
    }

    @Override
    public APIResponse<CareSessionResponse> forceEnd(UUID actorId, UUID centerId, UUID id,
                                                     CareSessionForceEndRequest request) {
        CareSession session = careSessionRepository.findWithBooking(id)
                .filter(found -> centerId.equals(found.getBooking().getCenterId()))
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
        String reason = request.getReason().strip();
        CareSessionResponse careSessionResponse = transactionTemplate.execute(status -> {
            CareSession lockedSession = lockSession(session);
            Instant now = clock.instant();
            List<CareTask> tasks = careTaskRepository.findAllBySession(id);
            for (CareTask task : tasks) {
                if (task.getStatus() == CareTaskStatus.PENDING) {
                    task.setStatus(CareTaskStatus.SKIPPED);
                    task.setSkipReason(FORCE_END_SKIP_REASON);
                    task.setUpdatedBy(actorId);
                }
            }
            closeSession(lockedSession, actorId, reason);
            log.info("Care session {} force-ended by manager {} at {}", id, actorId, now);
            return careSessionMapper.toResponse(lockedSession, tasks);
        });
        return APIResponse.success("Đã buộc kết thúc ca chăm sóc", careSessionResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findOverdueIds() {
        Instant endedBefore = clock.instant().minus(bookingProperties.getSession().getOverdueReminderAfter());
        return careSessionRepository.findOverdueIds(CareSessionStatus.IN_PROGRESS, endedBefore,
                PageRequest.of(0, OVERDUE_BATCH_SIZE));
    }

    @Override
    @Transactional
    public void remindOverdue(UUID id) {
        careSessionRepository.findForUpdate(id)
                .filter(session -> session.getStatus() == CareSessionStatus.IN_PROGRESS)
                .filter(session -> session.getOverdueRemindedAt() == null)
                .ifPresent(session -> {
                    session.setOverdueRemindedAt(clock.instant());
                    notificationService.notify(List.of(session.getCaregiverUserId()), NotificationType.SHIFT_REMINDER,
                            Map.of("body", "Ca chăm sóc đã quá giờ, vui lòng kết thúc ca"), "CARE_SESSION", id);
                    bookingNotificationService.notifyCenter(session.getBooking().getCenterId(), NotificationType.SHIFT_REMINDER,
                            "CARE_SESSION", id);
                    log.warn("Care session {} is still open after the occurrence ended — reminder recorded", id);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findAutoCloseIds() {
        Instant endedBefore = clock.instant().minus(bookingProperties.getSession().getAutoCloseAfter());
        return careSessionRepository.findOverdueIds(CareSessionStatus.IN_PROGRESS, endedBefore,
                PageRequest.of(0, OVERDUE_BATCH_SIZE));
    }

    @Override
    public void autoClose(UUID id) {
        CareSession snapshot = careSessionRepository.findWithBooking(id).orElse(null);
        // Ca đã được caregiver/manager hoặc lượt quét trước kết thúc → không làm gì (gọi lại an toàn).
        if (snapshot == null || snapshot.getStatus() != CareSessionStatus.IN_PROGRESS) {
            return;
        }
        Boolean closed = transactionTemplate.execute(status -> {
            CareSession session = lockSession(snapshot);
            Instant endedBefore = clock.instant().minus(bookingProperties.getSession().getAutoCloseAfter());
            if (session.getStatus() != CareSessionStatus.IN_PROGRESS
                    || session.getOccurrence().getEndAt().isAfter(endedBefore)) {
                return false;
            }
            for (CareTask task : careTaskRepository.findAllBySession(id)) {
                if (task.getStatus() == CareTaskStatus.PENDING) {
                    task.setStatus(CareTaskStatus.SKIPPED);
                    task.setSkipReason(AUTO_CLOSE_REASON);
                }
            }
            session.setAutoClosed(true);
            closeSession(session, null, AUTO_CLOSE_REASON);
            bookingNotificationService.notifyCenter(session.getBooking().getCenterId(), NotificationType.SESSION_AUTO_CLOSED,
                    "CARE_SESSION", id);
            return true;
        });
        if (Boolean.TRUE.equals(closed)) {
            log.warn("Care session {} auto-closed: caregiver never checked out", id);
        }
    }

    private CareSessionResponse startInTransaction(UUID actorId, CareSessionStartRequest request, UUID bookingId) {
        // Khóa booking rồi tới buổi — cùng thứ tự với đổi trạng thái booking / đổi caregiver.
        Booking booking = bookingRepository.findForUpdate(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        BookingOccurrence occurrence = bookingOccurrenceRepository.findForUpdate(request.getOccurrenceId())
                .filter(found -> actorId.equals(found.getCaregiverUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        if (!BookingStatus.CAREGIVER_RECIPIENT_ACCESS.contains(booking.getStatus())) {
            throw new AppException(ErrorCode.BOOKING_NOT_CONFIRMED);
        }
        if (careSessionRepository.existsByOccurrenceIdAndDeletedAtIsNull(occurrence.getId())) {
            throw new AppException(ErrorCode.SESSION_ALREADY_EXISTS);
        }
        if (!OccurrenceStatus.NOT_STARTED.contains(occurrence.getStatus())) {
            throw new AppException(ErrorCode.INVALID_OCCURRENCE_TRANSITION);
        }
        // H-11: caregiver bị khóa / lưu trữ → buổi chờ đổi người, không cho check-in bằng tài khoản cũ.
        if (Boolean.TRUE.equals(occurrence.getNeedsReassignment())) {
            throw new AppException(ErrorCode.OCCURRENCE_NEEDS_REASSIGNMENT);
        }
        Instant now = clock.instant();
        Instant earliestCheckIn = occurrence.getStartAt().minus(bookingProperties.getSession().getStartWindow());
        // D22 (M-08): check-in muộn nhất start + 30' — sau mốc đó buổi bị đánh MISSED (hoàn 100 % cọc buổi).
        Instant latestCheckIn = occurrence.getStartAt().plus(bookingProperties.getSession().getMissedGrace());
        if (now.isBefore(earliestCheckIn) || !now.isBefore(latestCheckIn) || !now.isBefore(occurrence.getEndAt())) {
            throw new AppException(ErrorCode.SESSION_WINDOW_NOT_OPEN);
        }
        CareSession session = careSessionRepository.saveAndFlush(careSessionMapper.toCareSession(request, occurrence,
                CareSessionStatus.IN_PROGRESS, now));
        List<CareTask> tasks = careTaskRepository.saveAll(buildTasks(session, booking, occurrence));
        OccurrenceStatus fromStatus = occurrence.getStatus();
        occurrence.setStatus(OccurrenceStatus.IN_PROGRESS);
        occurrence.setActualStartAt(now);
        bookingEventRepository.save(bookingEventMapper.toStatusEvent(occurrence, BookingEventType.OCCURRENCE_STATUS_CHANGED,
                fromStatus, OccurrenceStatus.IN_PROGRESS, actorId, null, now));
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            bookingLifecycleService.apply(booking, BookingAction.START, actorId, null);
        }
        notificationService.notify(List.of(booking.getCustomerId()), NotificationType.SESSION_STARTED,
                Map.of(), "CARE_SESSION", session.getId());
        return careSessionMapper.toResponse(session, tasks);
    }

    // Checklist: mỗi dịch vụ của booking một việc, rồi các lần uống thuốc (snapshot lúc đặt) rơi vào khung giờ buổi,
    // theo giờ tăng dần.
    private List<CareTask> buildTasks(CareSession session, Booking booking, BookingOccurrence occurrence) {
        List<CareTask> tasks = new ArrayList<>();
        int sortOrder = 0;
        for (BookingDetail detail : bookingDetailRepository.findAllByBooking(booking.getId())) {
            tasks.add(careSessionMapper.toServiceTask(detail, session, CareTaskType.SERVICE, CareTaskStatus.PENDING,
                    sortOrder++));
        }
        for (Map.Entry<LocalTime, List<String>> doses : findDosesInWindow(booking, occurrence).entrySet()) {
            for (String title : doses.getValue()) {
                tasks.add(careSessionMapper.toMedicationTask(title, doses.getKey(), session, CareTaskType.MEDICATION,
                        CareTaskStatus.PENDING, sortOrder++));
            }
        }
        return tasks;
    }

    private Map<LocalTime, List<String>> findDosesInWindow(Booking booking, BookingOccurrence occurrence) {
        Map<LocalTime, List<String>> doses = new TreeMap<>(Comparator.naturalOrder());
        RecipientSnapshot snapshot = booking.getRecipientSnapshot();
        if (snapshot == null || snapshot.getActiveMedications() == null) {
            return doses;
        }
        ZoneId zone = bookingProperties.getSchedule().getTimezone();
        LocalTime windowStart = occurrence.getStartAt().atZone(zone).toLocalTime();
        LocalTime windowEnd = occurrence.getEndAt().atZone(zone).toLocalTime();
        for (RecipientSnapshot.MedicationItem medication : snapshot.getActiveMedications()) {
            if (medication.getTimeOfDay() == null) {
                continue;
            }
            for (String timeOfDay : medication.getTimeOfDay()) {
                LocalTime doseTime = parseTime(timeOfDay);
                if (doseTime != null && !doseTime.isBefore(windowStart) && doseTime.isBefore(windowEnd)) {
                    doses.computeIfAbsent(doseTime, time -> new ArrayList<>()).add(toMedicationTitle(medication, doseTime));
                }
            }
        }
        return doses;
    }

    private String toMedicationTitle(RecipientSnapshot.MedicationItem medication, LocalTime doseTime) {
        String dosage = medication.getDosage() == null || medication.getDosage().isBlank()
                ? ""
                : " " + medication.getDosage().strip();
        return "Cho uống " + medication.getMedicationName() + dosage + " lúc " + doseTime.format(TIME_FORMAT);
    }

    // Snapshot đã được CareRecipient kiểm định dạng HH:mm; dữ liệu cũ sai định dạng thì bỏ qua thay vì chặn check-in.
    private LocalTime parseTime(String timeOfDay) {
        try {
            return LocalTime.parse(timeOfDay, TIME_FORMAT);
        } catch (DateTimeParseException | NullPointerException e) {
            log.warn("Skipping medication time '{}' with unexpected format", timeOfDay);
            return null;
        }
    }

    @Override
    @Transactional
    public APIResponse<CareTaskResponse> updateTask(UUID actorId, UUID sessionId, UUID taskId, CareTaskUpdateRequest request) {
        CareSession session = lockWritableSession(actorId, sessionId);
        if (request.getStatus() != CareTaskStatus.DONE && request.getStatus() != CareTaskStatus.SKIPPED) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (request.getStatus() == CareTaskStatus.SKIPPED
                && (request.getSkipReason() == null || request.getSkipReason().isBlank())) {
            throw new AppException(ErrorCode.TASK_SKIP_REASON_REQUIRED);
        }
        CareTask task = careTaskRepository.findByIdAndSessionIdAndDeletedAtIsNull(taskId, sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.TASK_NOT_FOUND));
        task.setStatus(request.getStatus());
        task.setNote(request.getNote());
        task.setSkipReason(request.getStatus() == CareTaskStatus.SKIPPED ? request.getSkipReason().trim() : null);
        task.setCompletedAt(request.getStatus() == CareTaskStatus.DONE ? clock.instant() : null);
        task.setUpdatedBy(actorId);
        CareTaskResponse response = careSessionMapper.toTaskResponse(careTaskRepository.saveAndFlush(task));
        SessionUpdateResponse update = careSessionMapper.toSessionUpdateResponse(sessionId, taskId, task.getStatus(), null);
        notificationService.publishSessionUpdate(session.getBooking().getCustomerId(), update);
        return APIResponse.success("Cập nhật công việc thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<CareTaskResponse> createTask(UUID actorId, UUID sessionId, CareTaskCreateRequest request) {
        CareSession session = lockWritableSession(actorId, sessionId);
        if (careTaskRepository.countBySessionIdAndTaskTypeAndDeletedAtIsNull(sessionId, CareTaskType.CUSTOM) >= 20) {
            throw new AppException(ErrorCode.TOO_MANY_CUSTOM_TASKS);
        }
        int order = careTaskRepository.findAllBySession(sessionId).stream().mapToInt(CareTask::getSortOrder).max().orElse(0) + 1;
        CareTask task = careSessionMapper.toMedicationTask(request.getTitle().trim(), null, session,
                CareTaskType.CUSTOM, CareTaskStatus.PENDING, order);
        task.setUpdatedBy(actorId);
        CareTask saved = careTaskRepository.saveAndFlush(task);
        CareTaskResponse response = careSessionMapper.toTaskResponse(saved);
        notificationService.publishSessionUpdate(session.getBooking().getCustomerId(),
                careSessionMapper.toSessionUpdateResponse(sessionId, saved.getId(), saved.getStatus(), null));
        return APIResponse.success("Thêm công việc thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<SessionNoteResponse> createNote(UUID actorId, UUID sessionId, SessionNoteRequest request) {
        CareSession session = lockWritableSession(actorId, sessionId);
        CareSessionNote note = careSessionMapper.toCareSessionNote(request, session, actorId);
        CareSessionNote saved = careSessionNoteRepository.saveAndFlush(note);
        SessionNoteResponse response = careSessionMapper.toNoteResponse(saved);
        notificationService.publishSessionUpdate(session.getBooking().getCustomerId(),
                careSessionMapper.toSessionUpdateResponse(sessionId, null, null, saved.getId()));
        return APIResponse.success("Ghi nhận tình trạng thành công", response);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<SessionNoteResponse>> listNotes(UUID actorId, Set<RoleCode> roles, UUID centerId,
                                                                    UUID id, int page, int size) {
        careSessionRepository.findWithBooking(id).filter(session -> canRead(actorId, roles, centerId, session))
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
        if (page < 0 || size < 1 || size > 100) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        Page<CareSessionNote> notes = careSessionNoteRepository.findBySessionIdAndDeletedAtIsNull(id,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        PageResponse<SessionNoteResponse> response = PageResponse.of(notes,
                careSessionMapper.toNoteResponseList(notes.getContent()));
        return APIResponse.success("Lấy ghi chú tình trạng thành công", response);
    }

    private CareSession lockWritableSession(UUID actorId, UUID id) {
        CareSession session = careSessionRepository.findWithBooking(id)
                .filter(found -> actorId.equals(found.getCaregiverUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
        return lockSession(session);
    }

    // Khóa booking → buổi → session (cùng thứ tự với check-in) và kiểm session còn mở.
    private CareSession lockSession(CareSession session) {
        bookingRepository.findForUpdate(session.getBooking().getId())
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
        bookingOccurrenceRepository.findForUpdate(session.getOccurrence().getId())
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
        CareSession lockedSession = careSessionRepository.findForUpdate(session.getId())
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
        if (lockedSession.getStatus() != CareSessionStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.SESSION_NOT_IN_PROGRESS);
        }
        return lockedSession;
    }

    // Kết thúc ca: session ENDED, buổi COMPLETED, chốt booking nếu không còn buổi nào chưa kết thúc (OPS-04).
    private void closeSession(CareSession session, UUID actorId, String reason) {
        Instant now = clock.instant();
        session.setStatus(CareSessionStatus.ENDED);
        notificationService.notify(List.of(session.getBooking().getCustomerId()), NotificationType.SESSION_ENDED,
                Map.of(), "CARE_SESSION", session.getId());
        session.setCheckOutAt(now);
        session.setEndedBy(actorId);
        session.setEndReason(reason);
        BookingOccurrence occurrence = session.getOccurrence();
        occurrence.setStatus(OccurrenceStatus.COMPLETED);
        occurrence.setActualEndAt(now);
        bookingEventRepository.save(bookingEventMapper.toStatusEvent(occurrence, BookingEventType.OCCURRENCE_STATUS_CHANGED,
                OccurrenceStatus.IN_PROGRESS, OccurrenceStatus.COMPLETED, actorId, reason, now));
        if (bookingLifecycleService.finalizeIfDone(session.getBooking(), actorId)) {

            log.info("Booking {} finalized after session {}", session.getBooking().getId(), session.getId());
        }
    }

    private boolean canRead(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId, CareSession session) {
        if (roles.contains(RoleCode.ADMIN) || roles.contains(RoleCode.SERVICE)) {
            return true;
        }
        Booking booking = session.getBooking();
        if (roles.contains(RoleCode.CENTER_MANAGER) && booking.getCenterId().equals(scopeCenterId)) {
            return true;
        }
        if (roles.contains(RoleCode.CUSTOMER) && booking.getCustomerId().equals(actorId)) {
            return true;
        }
        return roles.contains(RoleCode.CAREGIVER) && actorId.equals(session.getCaregiverUserId());
    }
}
