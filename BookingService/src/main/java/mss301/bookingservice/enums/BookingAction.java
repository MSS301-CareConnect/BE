package mss301.bookingservice.enums;

import lombok.Getter;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * State machine của booking — NGUỒN DUY NHẤT về chuyển trạng thái hợp lệ (docs/2026-10-09-PLAN_BOOKING.md §4).
 * Mỗi hành động có tập trạng thái nguồn và đúng một trạng thái đích. "Ai được làm" kiểm ở service (role + phạm vi).
 */
@Getter
public enum BookingAction {
    // Trung tâm chấp nhận (cần đã có caregiver).
    ACCEPT(BookingStatus.AWAITING_DEPOSIT, BookingStatus.PENDING),
    // Trung tâm từ chối.
    REJECT(BookingStatus.REJECTED, BookingStatus.PENDING),
    // Payment báo đã đặt cọc (TODO(Payment): hiện qua /internal/bookings/{id}/deposit-paid).
    PAY_DEPOSIT(BookingStatus.CONFIRMED, BookingStatus.AWAITING_DEPOSIT),
    // Khách hủy trước giờ buổi đầu.
    CANCEL_BY_CUSTOMER(BookingStatus.CANCELLED,
            BookingStatus.PENDING, BookingStatus.AWAITING_DEPOSIT, BookingStatus.CONFIRMED),
    // Trung tâm hủy trường hợp đặc biệt (PENDING thì dùng REJECT).
    CANCEL_BY_CENTER(BookingStatus.CANCELLED,
            BookingStatus.AWAITING_DEPOSIT, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS),
    // Hệ thống hủy (BK-04: trung tâm bị khóa) — mọi trạng thái còn giữ chỗ; IN_PROGRESS chỉ khi không còn buổi nào.
    CANCEL_BY_SYSTEM(BookingStatus.CANCELLED,
            BookingStatus.PENDING, BookingStatus.AWAITING_DEPOSIT, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS),
    // Scheduler: quá hạn duyệt (PENDING) hoặc quá hạn đặt cọc (AWAITING_DEPOSIT).
    EXPIRE(BookingStatus.EXPIRED, BookingStatus.PENDING, BookingStatus.AWAITING_DEPOSIT),
    // Caregiver check-in buổi đầu tiên (OPS-02).
    START(BookingStatus.IN_PROGRESS, BookingStatus.CONFIRMED),
    // Không còn buổi nào chưa kết thúc và ít nhất một buổi đã được phục vụ (COMPLETED / CUSTOMER_NO_SHOW) — OPS-04.
    FINALIZE(BookingStatus.COMPLETED, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS),
    // Không còn buổi nào chưa kết thúc và không buổi nào được phục vụ (mọi buổi bị hủy / MISSED) — OPS-04.
    FINALIZE_CANCELLED(BookingStatus.CANCELLED, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

    private final BookingStatus targetStatus;

    private final Set<BookingStatus> sourceStatuses;

    BookingAction(BookingStatus targetStatus, BookingStatus... sourceStatuses) {
        this.targetStatus = targetStatus;
        this.sourceStatuses = Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(sourceStatuses)));
    }

    public boolean isAllowedFrom(BookingStatus currentStatus) {
        return sourceStatuses.contains(currentStatus);
    }

    // Trạng thái đích khi áp hành động; không hợp lệ → INVALID_BOOKING_TRANSITION (409).
    public BookingStatus apply(BookingStatus currentStatus) {
        if (!isAllowedFrom(currentStatus)) {
            throw new AppException(ErrorCode.INVALID_BOOKING_TRANSITION);
        }
        return targetStatus;
    }
}
