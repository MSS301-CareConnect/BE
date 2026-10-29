package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingAction;
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
import mss301.bookingservice.service.BookingNotificationService;
import mss301.bookingservice.enums.NotificationType;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BookingLifecycleServiceImpl implements BookingLifecycleService {

    static String EXPIRED_REASON = "Quá hạn duyệt hoặc quá hạn đặt cọc";
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
    BookingProperties bookingProperties;
    Clock clock;
    BookingNotificationService bookingNotificationService;

    @Override
    public void recordCreated(Booking booking, UUID customerId) {
        bookingStatusHistoryRepository.save(bookingStatusHistoryMapper.toHistory(booking, null, booking.getStatus(),
                customerId, null, clock.instant()));
        bookingNotificationService.notifyCenter(booking.getCenterId(), NotificationType.BOOKING_CREATED, "BOOKING", booking.getId());
    }

    @Override
    public void apply(Booking booking, BookingAction action, UUID changedBy, String reason) {
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
                cancelScheduledOccurrences(booking, occurrences, CancelledBy.CENTER, changedBy, REJECTED_OCCURRENCE_REASON);
            }
            case PAY_DEPOSIT -> booking.setConfirmedAt(now);
            case CANCEL_BY_CUSTOMER, CANCEL_BY_CENTER, CANCEL_BY_SYSTEM -> {
                booking.setCancelledAt(now);
                booking.setCancelledBy(changedBy);
                booking.setCancelReason(reason);
                // Hoàn tiền (nếu đã cọc) do caller xếp qua requestRefund — hành động này chỉ đóng booking.
                cancelScheduledOccurrences(booking, occurrences, resolveCancelledBy(action), changedBy, reason);
            }
            case EXPIRE -> {
                // Chỉ PENDING / AWAITING_DEPOSIT hết hạn — chưa có tiền nên không hoàn.
                historyReason = reason == null ? EXPIRED_REASON : reason;
                cancelScheduledOccurrences(booking, occurrences, CancelledBy.SYSTEM, changedBy, historyReason);
            }
            case START -> booking.setStartedAt(now);
            case FINALIZE -> {
                booking.setCompletedAt(now);
                // Hóa đơn phần còn lại do PaymentService tính (PAY-11); ghi outbox cùng transaction để không bao giờ mất.
                bookingOutboxService.enqueue(OutboxEventType.REMAINING_INVOICE, booking.getId(),
                        OutboxEventType.REMAINING_INVOICE.name() + KEY_SEPARATOR + booking.getId(),
                        bookingMapper.toRemainingInvoiceRequest(booking.getId(), occurrences));
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

    @Override
    public boolean finalizeIfDone(Booking booking, UUID changedBy) {
        if (!FINALIZABLE_STATUSES.contains(booking.getStatus())) {
            return false;
        }
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findAllByBooking(booking.getId());
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

    @Override
    public void requestRefund(Booking booking, List<UUID> occurrenceIds, RefundReason reason, CancelledBy cancelledBy,
                              UUID requestedBy, String keySuffix) {
        Instant now = clock.instant();
        // Mốc tính % hoàn: buổi chưa diễn ra gần nhất còn lại (PaymentService dùng cho bảng mốc 24h / 72h).
        Instant firstUpcomingStartAt = bookingOccurrenceRepository.findAllByBooking(booking.getId()).stream()
                .filter(occurrence -> OccurrenceStatus.NOT_STARTED.contains(occurrence.getStatus())
                        || occurrenceIds.contains(occurrence.getId()))
                .map(BookingOccurrence::getStartAt)
                .filter(startAt -> startAt.isAfter(now))
                .min(Comparator.naturalOrder())
                .orElse(null);
        String idempotencyKey = OutboxEventType.REFUND.name() + KEY_SEPARATOR + reason.name() + KEY_SEPARATOR + keySuffix;
        bookingOutboxService.enqueue(OutboxEventType.REFUND, booking.getId(), idempotencyKey,
                bookingMapper.toRefundCreateRequest(booking.getId(), occurrenceIds, reason, cancelledBy, now,
                        firstUpcomingStartAt, requestedBy, idempotencyKey));
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
