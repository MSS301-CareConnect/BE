package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.response.RefundQuote;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingAction;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.CancelledBy;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.mapper.BookingStatusHistoryMapper;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.repository.BookingStatusHistoryRepository;
import mss301.bookingservice.service.BookingLifecycleService;
import mss301.bookingservice.service.RefundPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BookingLifecycleServiceImpl implements BookingLifecycleService {

    static String EXPIRED_REASON = "Quá hạn duyệt hoặc quá hạn đặt cọc";
    static String REJECTED_OCCURRENCE_REASON = "Trung tâm từ chối booking";

    BookingOccurrenceRepository bookingOccurrenceRepository;
    BookingStatusHistoryRepository bookingStatusHistoryRepository;
    BookingStatusHistoryMapper bookingStatusHistoryMapper;
    RefundPolicy refundPolicy;
    BookingProperties bookingProperties;
    Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCreated(Booking booking, UUID customerId) {
        bookingStatusHistoryRepository.save(bookingStatusHistoryMapper.toHistory(booking, null, booking.getStatus(),
                customerId, null, clock.instant()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
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
}
