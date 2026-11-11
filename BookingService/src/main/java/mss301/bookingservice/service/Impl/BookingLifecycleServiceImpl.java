package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.entity.CenterBookingPolicy;
import mss301.bookingservice.enums.BookingExpiredReason;
import mss301.bookingservice.enums.BookingAction;
import mss301.bookingservice.enums.BookingType;
import mss301.bookingservice.enums.BookingEventType;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.CancelledBy;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.OutboxEventType;
import mss301.bookingservice.enums.RefundReason;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.BookingEventMapper;
import mss301.bookingservice.mapper.BookingMapper;
import mss301.bookingservice.mapper.BookingStatusHistoryMapper;
import mss301.bookingservice.repository.BookingEventRepository;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.repository.BookingStatusHistoryRepository;
import mss301.bookingservice.service.BookingLifecycleService;
import mss301.bookingservice.service.BookingOutboxService;
import mss301.bookingservice.service.CenterBookingPolicyService;
import mss301.bookingservice.service.BookingNotificationService;
import mss301.bookingservice.util.BookingDeadlines;
import mss301.bookingservice.util.CenterReminders;
import mss301.bookingservice.enums.NotificationType;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BookingLifecycleServiceImpl implements BookingLifecycleService {

    static Map<BookingExpiredReason, String> EXPIRED_REASONS = Map.of(
            BookingExpiredReason.CENTER_CONFIRMATION_TIMEOUT, "Trung tâm không xác nhận kịp hạn",
            BookingExpiredReason.CAREGIVER_CONFIRMATION_TIMEOUT, "Người chăm sóc không trả lời kịp hạn",
            BookingExpiredReason.ASSIGNMENT_TIMEOUT, "Trung tâm không phân người chăm sóc kịp hạn",
            BookingExpiredReason.INITIAL_PAYMENT_TIMEOUT, "Khách chưa thanh toán khoản đầu đúng hạn",
            BookingExpiredReason.PAYMENT_OVERDUE, "Khách chưa thanh toán phần còn lại đúng hạn");
    static Set<BookingStatus> CENTER_ACTION_STATUSES = EnumSet.of(BookingStatus.PENDING_CENTER_CONFIRMATION,
            BookingStatus.PENDING_CAREGIVER_ASSIGNMENT);
    static String KEY_SEPARATOR = ":";
    static String REJECTED_OCCURRENCE_REASON = "Trung tâm từ chối booking";
    static String NOTHING_SERVED_REASON = "Không còn buổi nào được thực hiện";
    static Set<OccurrenceStatus> SERVED_STATUSES = EnumSet.of(OccurrenceStatus.COMPLETED, OccurrenceStatus.CUSTOMER_NO_SHOW);
    static Set<BookingStatus> FINALIZABLE_STATUSES = EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

    BookingOccurrenceRepository bookingOccurrenceRepository;
    BookingStatusHistoryRepository bookingStatusHistoryRepository;
    BookingStatusHistoryMapper bookingStatusHistoryMapper;
    BookingEventRepository bookingEventRepository;
    BookingEventMapper bookingEventMapper;
    BookingMapper bookingMapper;
    BookingOutboxService bookingOutboxService;
    CenterBookingPolicyService centerBookingPolicyService;
    Clock clock;
    BookingNotificationService bookingNotificationService;
    BookingProperties bookingProperties;

    // Ghi dòng lịch sử đầu tiên khi tạo booking.
    @Override
    public void recordCreated(Booking booking, UUID customerId) {
        bookingStatusHistoryRepository.save(bookingStatusHistoryMapper.toHistory(booking, null, booking.getStatus(),
                customerId, null, clock.instant()));
        // CR-3: đơn đã chọn caregiver tới thẳng caregiver; trung tâm vẫn được báo để theo dõi. SHIFT tự nhận ngay sau đó
        // (ca caregiver đã đăng ký) nên không gửi yêu cầu trả lời.
        if (booking.getStatus() == BookingStatus.PENDING_CAREGIVER_CONFIRMATION
                && booking.getBookingType() != BookingType.SHIFT) {
            bookingNotificationService.notifyCaregiverRequest(booking);
        }
        bookingNotificationService.notifyCenter(booking.getCenterId(), NotificationType.BOOKING_CREATED, "BOOKING", booking.getId());
    }

    // Đổi trạng thái booking theo hành động, cập nhật buổi, ghi lịch sử, hoàn tiền và thông báo.
    @Override
    public void apply(Booking booking, BookingAction action, UUID changedBy, String reason) {
        BookingStatus fromStatus = booking.getStatus();
        BookingStatus toStatus = action.apply(fromStatus);
        Instant now = clock.instant();
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findByBookingIdAndDeletedAtIsNullOrderByOccurrenceNoAsc(booking.getId());
        booking.setStatus(toStatus);
        String historyReason = reason;
        switch (action) {
            case ACCEPT -> {
                booking.setAcceptedBy(changedBy);
                booking.setAcceptedAt(now);
                booking.setDepositDueAt(calculateDueAt(booking, occurrences, now,
                        findPolicy(booking).getInitialPaymentTimeoutMinutes()));
            }
            case ACCEPT_WITHOUT_CAREGIVER -> {
                booking.setAcceptedBy(changedBy);
                booking.setAcceptedAt(now);
                booking.setAssignmentDueAt(calculateDueAt(booking, occurrences, now,
                        findPolicy(booking).getAssignmentTimeoutMinutes()));
                booking.setCenterReminderMinutes(findPassedCenterReminder(booking.getAssignmentDueAt(), now));
            }
            case ASSIGN_CAREGIVER -> booking.setDepositDueAt(calculateDueAt(booking, occurrences, now,
                    findPolicy(booking).getInitialPaymentTimeoutMinutes()));
            case CAREGIVER_ACCEPT -> {
                booking.setAcceptedBy(changedBy);
                booking.setAcceptedAt(now);
                booking.setDepositDueAt(calculateDueAt(booking, occurrences, now,
                        findPolicy(booking).getInitialPaymentTimeoutMinutes()));
            }
            case CAREGIVER_DECLINE -> {
                booking.setRejectedBy(changedBy);
                booking.setRejectedAt(now);
                booking.setRejectedReason(reason);
                cancelScheduledOccurrences(booking, occurrences, CancelledBy.CENTER, changedBy, REJECTED_OCCURRENCE_REASON);
            }
            case HAND_OVER_TO_CENTER -> {
                // CR-3: trả lịch caregiver cũ, trung tâm phân người khác trước hạn phân công của policy.
                releaseCaregiver(booking, occurrences, changedBy, reason, now);
                booking.setAssignmentDueAt(calculateDueAt(booking, occurrences, now,
                        findPolicy(booking).getAssignmentTimeoutMinutes()));
                booking.setCenterReminderMinutes(findPassedCenterReminder(booking.getAssignmentDueAt(), now));
            }
            case REJECT -> {
                booking.setRejectedBy(changedBy);
                booking.setRejectedAt(now);
                booking.setRejectedReason(reason);
                cancelScheduledOccurrences(booking, occurrences, CancelledBy.CENTER, changedBy, REJECTED_OCCURRENCE_REASON);
            }
            case PAY_INITIAL_PAYMENT -> {
                booking.setConfirmedAt(now);
                // Spec §11.2 (M2): trả khoản đầu → buổi đang giữ được lên lịch chính thức.
                scheduleReservedOccurrences(occurrences, changedBy, now);
                // M6: mốc nhắc đã qua lúc trả khoản đầu (booking đặt sát giờ) thì bỏ qua, không nhắc dồn.
                if (booking.getRemainingPaymentDueAt() != null) {
                    booking.setRemainingReminderMinutes(findPassedReminder(booking, now));
                }
            }
            case CANCEL_BY_CUSTOMER, CANCEL_BY_CENTER, CANCEL_BY_SYSTEM -> {
                booking.setCancelledAt(now);
                booking.setCancelledBy(changedBy);
                booking.setCancelReason(reason);
                // Hoàn tiền (nếu đã cọc) do caller xếp qua requestRefund — hành động này chỉ đóng booking.
                cancelScheduledOccurrences(booking, occurrences, resolveCancelledBy(action), changedBy, reason);
            }
            case EXPIRE -> {
                // Không xếp hoàn tiền ở đây: chưa trả khoản đầu thì chưa có tiền; quá hạn phần còn lại (CONFIRMED) thì
                // PaymentService đã giữ cọc theo policy và hoàn phần dư trước khi báo. Buổi chưa bắt đầu → EXPIRED.
                booking.setExpiredReason(BookingExpiredReason.from(fromStatus));
                historyReason = reason == null ? EXPIRED_REASONS.get(booking.getExpiredReason()) : reason;
                expireOccurrences(occurrences, changedBy, historyReason, now);
            }
            case START -> booking.setStartedAt(now);
            case FINALIZE -> {
                booking.setCompletedAt(now);
                // Spec §9.1: booking mới trả đủ trước buổi đầu, không thu sau. Chỉ booking cũ (trước spec v1, chưa có hạn
                // phần còn lại) còn hóa đơn sau hoàn tất do PaymentService tính (PAY-11) — outbox cùng transaction.
                if (booking.getRemainingPaymentDueAt() == null && booking.getFullyPaidAt() == null) {
                    bookingOutboxService.enqueue(OutboxEventType.REMAINING_INVOICE, booking.getId(),
                            OutboxEventType.REMAINING_INVOICE.name() + KEY_SEPARATOR + booking.getId(),
                            bookingMapper.toRemainingInvoiceRequest(booking.getId(), occurrences));
                }
            }
            case FINALIZE_CANCELLED -> {
                booking.setCancelledAt(now);
                booking.setCancelledBy(changedBy);
                booking.setCancelReason(historyReason);
            }
        }
        bookingStatusHistoryRepository.save(bookingStatusHistoryMapper.toHistory(booking, fromStatus, toStatus,
                changedBy, historyReason, now));
        bookingNotificationService.notifyLifecycle(booking, occurrences, action);
        log.info("Booking {} {} -> {} ({})", booking.getId(), fromStatus, toStatus, action);
    }

    // Nhắc khách trả phần còn lại theo mốc nhắc của chính sách.
    @Override
    public boolean remindRemainingPayment(Booking booking) {
        Instant now = clock.instant();
        boolean awaitingRemaining = booking.getStatus() == BookingStatus.CONFIRMED && booking.getFullyPaidAt() == null
                && booking.getRemainingPaymentDueAt() != null && now.isBefore(booking.getRemainingPaymentDueAt());
        if (!awaitingRemaining) {
            return false;
        }
        Integer passed = findPassedReminder(booking, now);
        Integer sent = booking.getRemainingReminderMinutes();
        if (passed == null || (sent != null && sent <= passed)) {
            return false;
        }
        booking.setRemainingReminderMinutes(passed);
        bookingNotificationService.notifyRemainingDue(booking);
        log.info("Booking {} reminded to pay the remaining amount ({} minutes before due)", booking.getId(), passed);
        return true;
    }

    // Nhắc trung tâm khi booking sắp tới hạn xử lý.
    @Override
    public boolean remindCenterAction(Booking booking) {
        Instant now = clock.instant();
        Instant dueAt = BookingDeadlines.dueAt(booking);
        if (!CENTER_ACTION_STATUSES.contains(booking.getStatus()) || dueAt == null || !now.isBefore(dueAt)) {
            return false;
        }
        Integer passed = findPassedCenterReminder(dueAt, now);
        Integer sent = booking.getCenterReminderMinutes();
        if (passed == null || (sent != null && sent <= passed)) {
            return false;
        }
        booking.setCenterReminderMinutes(passed);
        bookingNotificationService.notifyCenterActionDue(booking);
        log.info("Booking {} center reminded ({} minutes before {})", booking.getId(), passed, booking.getStatus());
        return true;
    }

    // Hoàn tất booking khi mọi buổi đã kết thúc.
    @Override
    public boolean finalizeIfDone(Booking booking, UUID changedBy) {
        if (!FINALIZABLE_STATUSES.contains(booking.getStatus())) {
            return false;
        }
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findByBookingIdAndDeletedAtIsNullOrderByOccurrenceNoAsc(booking.getId());
        boolean unfinished = occurrences.stream()
                .anyMatch(occurrence -> OccurrenceStatus.ACTIVE.contains(occurrence.getStatus()));
        if (unfinished) {
            return false;
        }
        boolean served = occurrences.stream()
                .anyMatch(occurrence -> SERVED_STATUSES.contains(occurrence.getStatus()));
        if (served) {
            apply(booking, BookingAction.FINALIZE, changedBy, null);
        } else {
            apply(booking, BookingAction.FINALIZE_CANCELLED, changedBy, NOTHING_SERVED_REASON);
        }
        return true;
    }

    // Hủy các buổi chưa kết thúc của booking.
    @Override
    public void cancelOccurrences(Booking booking, List<BookingOccurrence> occurrences, CancelledBy cancelledBy,
                                  UUID changedBy, String reason) {
        Instant now = clock.instant();
        for (BookingOccurrence occurrence : occurrences) {
            OccurrenceStatus fromStatus = occurrence.getStatus();
            if (!fromStatus.canTransitionTo(OccurrenceStatus.CANCELLED)) {
                throw new AppException(ErrorCode.OCCURRENCE_NOT_CANCELLABLE);
            }
            occurrence.setStatus(OccurrenceStatus.CANCELLED);
            occurrence.setCancelReason(reason);
            occurrence.setCancelledAt(now);
            occurrence.setCancelledByParty(cancelledBy);
            bookingEventRepository.save(bookingEventMapper.toStatusEvent(occurrence,
                    BookingEventType.OCCURRENCE_STATUS_CHANGED, fromStatus, OccurrenceStatus.CANCELLED, changedBy,
                    reason, now));
        }
        // M-10: hủy nhiều buổi một lần → một thông báo gộp cho mỗi người, không bắn N thông báo.
        if (occurrences.size() == 1) {
            bookingNotificationService.notifyOccurrence(occurrences.getFirst(), NotificationType.OCCURRENCE_CANCELLED);
        } else if (!occurrences.isEmpty()) {
            bookingNotificationService.notifyOccurrencesCancelled(booking, occurrences);
        }
    }

    // Xếp lệnh hoàn tiền cho các buổi bị hủy qua outbox.
    @Override
    public void requestRefund(Booking booking, List<UUID> occurrenceIds, RefundReason reason, CancelledBy cancelledBy,
                              UUID requestedBy, String keySuffix) {
        Instant now = clock.instant();
        // Mốc tính % hoàn: buổi chưa diễn ra gần nhất còn lại (PaymentService dùng cho bảng mốc 24h / 72h).
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findByBookingIdAndDeletedAtIsNullOrderByOccurrenceNoAsc(booking.getId());
        Instant firstUpcomingStartAt = occurrences.stream()
                .filter(occurrence -> OccurrenceStatus.NOT_STARTED.contains(occurrence.getStatus())
                        || occurrenceIds.contains(occurrence.getId()))
                .map(BookingOccurrence::getStartAt)
                .filter(startAt -> startAt.isAfter(now))
                .min(Comparator.naturalOrder())
                .orElse(null);
        String idempotencyKey = OutboxEventType.REFUND.name() + KEY_SEPARATOR + reason.name() + KEY_SEPARATOR + keySuffix;
        bookingOutboxService.enqueue(OutboxEventType.REFUND, booking.getId(), idempotencyKey,
                bookingMapper.toRefundCreateRequest(booking.getId(), occurrenceIds, reason, cancelledBy, now,
                        firstUpcomingStartAt, requestedBy, idempotencyKey,
                        occurrences.stream().filter(occurrence -> occurrenceIds.contains(occurrence.getId())).toList()));
    }

    // Hạn = bây giờ + timeout của policy, nhưng không quá mốc chốt thanh toán: hạn trả phần còn lại (RECURRING / CUSTOM)
    // hoặc giờ buổi đầu (SHIFT) — spec §8.
    private Instant calculateDueAt(Booking booking, List<BookingOccurrence> occurrences, Instant now, int timeoutMinutes) {
        Instant dueAt = now.plus(Duration.ofMinutes(timeoutMinutes));
        Instant cutoff = booking.getRemainingPaymentDueAt();
        if (cutoff == null) {
            cutoff = occurrences.stream()
                    .map(BookingOccurrence::getStartAt)
                    .min(Comparator.naturalOrder())
                    .orElse(dueAt);
        }
        return cutoff.isBefore(dueAt) ? cutoff : dueAt;
    }

    // Chính sách booking hiệu lực của trung tâm.
    private CenterBookingPolicy findPolicy(Booking booking) {
        return centerBookingPolicyService.findEffective(booking.getCenterId());
    }

    // Mốc nhắc (phút trước hạn phần còn lại) gần hạn nhất đã tới; chưa tới mốc nào → null.
    private Integer findPassedCenterReminder(Instant dueAt, Instant now) {
        return CenterReminders.passed(bookingProperties.getSchedule().getCenterReminderMinutes(), dueAt, now);
    }

    // Mốc nhắc trả phần còn lại vừa qua (nếu có).
    private Integer findPassedReminder(Booking booking, Instant now) {
        Integer[] reminders = findPolicy(booking).getRemainingPaymentReminderMinutes();
        if (reminders == null) {
            return null;
        }
        Instant dueAt = booking.getRemainingPaymentDueAt();
        return Arrays.stream(reminders)
                .filter(Objects::nonNull)
                .filter(minutes -> !now.isBefore(dueAt.minus(Duration.ofMinutes(minutes))))
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    // Chuyển các buổi đang giữ chỗ sang đã lên lịch.
    private void scheduleReservedOccurrences(List<BookingOccurrence> occurrences, UUID changedBy, Instant now) {
        for (BookingOccurrence occurrence : occurrences) {
            if (occurrence.getStatus() == OccurrenceStatus.RESERVED) {
                changeOccurrenceStatus(occurrence, OccurrenceStatus.SCHEDULED, changedBy, null, now);
            }
        }
    }

    // Booking hết hạn: buổi chưa bắt đầu không còn hiệu lực (spec §11.2 EXPIRED), giải phóng lịch caregiver.
    private void expireOccurrences(List<BookingOccurrence> occurrences, UUID changedBy, String reason, Instant now) {
        for (BookingOccurrence occurrence : occurrences) {
            if (OccurrenceStatus.NOT_STARTED.contains(occurrence.getStatus())) {
                changeOccurrenceStatus(occurrence, OccurrenceStatus.EXPIRED, changedBy, reason, now);
            }
        }
    }

    // CR-3: mỗi buổi đang giữ lịch của caregiver cũ về PENDING_ASSIGNMENT (ghi CAREGIVER_CHANGED cho lịch sử), booking bỏ
    // caregiver mặc định. Thông báo do notifyLifecycle (trung tâm + khách) — caregiver cũ là người vừa từ chối / quá hạn.
    private void releaseCaregiver(Booking booking, List<BookingOccurrence> occurrences, UUID changedBy, String reason,
                                  Instant now) {
        for (BookingOccurrence occurrence : occurrences) {
            if (occurrence.getStatus() != OccurrenceStatus.RESERVED) {
                continue;
            }
            bookingEventRepository.save(bookingEventMapper.toCaregiverChangedEvent(booking, occurrence.getId(),
                    occurrence.getCaregiverId(), null, changedBy, reason, now));
            occurrence.releaseCaregiver();
        }
        booking.setCaregiverId(null);
        booking.setCaregiverUserId(null);
    }

    // Đổi trạng thái một buổi có kiểm chuyển trạng thái hợp lệ và ghi sự kiện.
    private void changeOccurrenceStatus(BookingOccurrence occurrence, OccurrenceStatus toStatus, UUID changedBy,
                                        String reason, Instant now) {
        OccurrenceStatus fromStatus = occurrence.getStatus();
        if (!fromStatus.canTransitionTo(toStatus)) {
            throw new AppException(ErrorCode.INVALID_OCCURRENCE_TRANSITION);
        }
        occurrence.setStatus(toStatus);
        bookingEventRepository.save(bookingEventMapper.toStatusEvent(occurrence,
                BookingEventType.OCCURRENCE_STATUS_CHANGED, fromStatus, toStatus, changedBy, reason, now));
    }

    // Bên hủy tương ứng với hành động (khách, trung tâm, hệ thống).
    private CancelledBy resolveCancelledBy(BookingAction action) {
        return switch (action) {
            case CANCEL_BY_CENTER -> CancelledBy.CENTER;
            case CANCEL_BY_SYSTEM -> CancelledBy.SYSTEM;
            default -> CancelledBy.CUSTOMER;
        };
    }

    // Booking kết thúc sớm → các buổi chưa diễn ra bị hủy (giải phóng caregiver); buổi đang / đã diễn ra giữ nguyên.
    private void cancelScheduledOccurrences(Booking booking, List<BookingOccurrence> occurrences,
                                            CancelledBy cancelledBy, UUID changedBy, String reason) {
        List<BookingOccurrence> scheduledOccurrences = occurrences.stream()
                .filter(occurrence -> OccurrenceStatus.NOT_STARTED.contains(occurrence.getStatus()))
                .toList();
        cancelOccurrences(booking, scheduledOccurrences, cancelledBy, changedBy, reason);
    }
}
