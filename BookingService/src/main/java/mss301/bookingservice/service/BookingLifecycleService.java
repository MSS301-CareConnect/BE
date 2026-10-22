package mss301.bookingservice.service;

import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingAction;
import mss301.bookingservice.enums.CancelledBy;
import mss301.bookingservice.enums.RefundReason;

import java.util.List;
import java.util.UUID;

/**
 * Nơi DUY NHẤT đổi trạng thái booking (state machine ở BookingAction): mốc thời gian, hủy buổi chưa diễn ra khi kết
 * thúc sớm, booking_status_history, việc gửi sang Payment qua outbox. Chỉ gọi trong transaction ghi, booking ĐÃ khóa dòng.
 */
public interface BookingLifecycleService {
    void recordCreated(Booking booking, UUID customerId);

    void apply(Booking booking, BookingAction action, UUID changedBy, String reason);

    // OPS-04 (dùng chung BK-03 / BK-04 / OPS-06): khi không còn buổi nào chưa kết thúc → COMPLETED nếu có buổi đã được
    // phục vụ (COMPLETED / CUSTOMER_NO_SHOW), ngược lại CANCELLED. Trả true khi booking vừa được chốt.
    boolean finalizeIfDone(Booking booking, UUID changedBy);

    // Hủy các buổi (đã được service kiểm là hủy được): CANCELLED + ai hủy / lúc nào + booking_events. Không đổi booking.
    void cancelOccurrences(Booking booking, List<BookingOccurrence> occurrences, CancelledBy cancelledBy,
                           UUID changedBy, String reason);

    // Xếp yêu cầu hoàn tiền các buổi sang PaymentService qua outbox (C2.3) — Payment tính số tiền theo chính sách đã
    // snapshot. keySuffix phân biệt từng lần (vd id buổi) để cùng một việc không bao giờ được hoàn hai lần.
    void requestRefund(Booking booking, List<UUID> occurrenceIds, RefundReason reason, CancelledBy cancelledBy,
                       UUID requestedBy, String keySuffix);
}
