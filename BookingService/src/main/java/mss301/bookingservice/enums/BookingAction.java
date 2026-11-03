package mss301.bookingservice.enums;

import lombok.Getter;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * State machine của booking — NGUỒN DUY NHẤT về chuyển trạng thái hợp lệ (spec §11.1).
 * Mỗi hành động có tập trạng thái nguồn và đúng một trạng thái đích. "Ai được làm" kiểm ở service (role + phạm vi).
 */
@Getter
public enum BookingAction {
    // Trung tâm xác nhận booking đã có caregiver → chờ thanh toán khoản đầu.
    ACCEPT(BookingStatus.AWAITING_INITIAL_PAYMENT, BookingStatus.PENDING_CENTER_CONFIRMATION),
    // Trung tâm xác nhận RECURRING / CUSTOM chưa có caregiver → phải phân caregiver trong hạn policy (spec §5.2).
    ACCEPT_WITHOUT_CAREGIVER(BookingStatus.PENDING_CAREGIVER_ASSIGNMENT, BookingStatus.PENDING_CENTER_CONFIRMATION),
    // Trung tâm phân caregiver cho booking đã xác nhận → chờ thanh toán khoản đầu (spec §5.3).
    ASSIGN_CAREGIVER(BookingStatus.AWAITING_INITIAL_PAYMENT, BookingStatus.PENDING_CAREGIVER_ASSIGNMENT),
    // CR-3: caregiver khách đã chọn nhận đơn → chờ thanh toán khoản đầu, không qua trung tâm.
    CAREGIVER_ACCEPT(BookingStatus.AWAITING_INITIAL_PAYMENT, BookingStatus.PENDING_CAREGIVER_CONFIRMATION),
    // CR-3: caregiver từ chối đơn SHIFT (ca gắn với chính caregiver đó nên không chuyển được cho người khác).
    CAREGIVER_DECLINE(BookingStatus.REJECTED, BookingStatus.PENDING_CAREGIVER_CONFIRMATION),
    // CR-3: caregiver từ chối hoặc quá hạn trả lời đơn RECURRING / CUSTOM → trung tâm phân người khác trong hạn policy.
    HAND_OVER_TO_CENTER(BookingStatus.PENDING_CAREGIVER_ASSIGNMENT, BookingStatus.PENDING_CAREGIVER_CONFIRMATION),
    // Trung tâm từ chối trước khi cung cấp dịch vụ (kể cả khi không tìm được caregiver, spec §5.3).
    REJECT(BookingStatus.REJECTED, BookingStatus.PENDING_CENTER_CONFIRMATION,
            BookingStatus.PENDING_CAREGIVER_CONFIRMATION, BookingStatus.PENDING_CAREGIVER_ASSIGNMENT),
    // PaymentService báo khoản thanh toán đầu thành công (SHIFT 100 %, RECURRING / CUSTOM tiền cọc).
    PAY_INITIAL_PAYMENT(BookingStatus.CONFIRMED, BookingStatus.AWAITING_INITIAL_PAYMENT),
    // Khách hủy trước giờ buổi đầu.
    CANCEL_BY_CUSTOMER(BookingStatus.CANCELLED, BookingStatus.PENDING_CENTER_CONFIRMATION,
            BookingStatus.PENDING_CAREGIVER_CONFIRMATION, BookingStatus.PENDING_CAREGIVER_ASSIGNMENT,
            BookingStatus.AWAITING_INITIAL_PAYMENT, BookingStatus.CONFIRMED),
    // Trung tâm hủy trường hợp đặc biệt (chưa thanh toán thì dùng REJECT).
    CANCEL_BY_CENTER(BookingStatus.CANCELLED,
            BookingStatus.AWAITING_INITIAL_PAYMENT, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS),
    // Hệ thống / admin hủy (BK-04: trung tâm bị khóa) — mọi trạng thái còn giữ chỗ; IN_PROGRESS chỉ khi không còn buổi nào.
    CANCEL_BY_SYSTEM(BookingStatus.CANCELLED, BookingStatus.PENDING_CENTER_CONFIRMATION,
            BookingStatus.PENDING_CAREGIVER_CONFIRMATION, BookingStatus.PENDING_CAREGIVER_ASSIGNMENT, BookingStatus.AWAITING_INITIAL_PAYMENT, BookingStatus.CONFIRMED,
            BookingStatus.IN_PROGRESS),
    // Scheduler: quá hạn xác nhận, quá hạn phân caregiver hoặc quá hạn thanh toán khoản đầu. CONFIRMED: PaymentService
    // báo quá hạn trả phần còn lại trước buổi đầu (spec §11.1, §15; M1).
    // PENDING_CAREGIVER_CONFIRMATION: SHIFT quá hạn caregiver trả lời, hoặc không còn kịp chuyển trung tâm.
    EXPIRE(BookingStatus.EXPIRED, BookingStatus.PENDING_CENTER_CONFIRMATION,
            BookingStatus.PENDING_CAREGIVER_CONFIRMATION, BookingStatus.PENDING_CAREGIVER_ASSIGNMENT,
            BookingStatus.AWAITING_INITIAL_PAYMENT, BookingStatus.CONFIRMED),
    // Caregiver check-in buổi đầu tiên (OPS-02).
    START(BookingStatus.IN_PROGRESS, BookingStatus.CONFIRMED),
    // Không còn buổi nào chưa kết thúc và ít nhất một buổi đã được phục vụ (COMPLETED / CUSTOMER_NO_SHOW) — OPS-04.
    FINALIZE(BookingStatus.COMPLETED, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS),
    // Không còn buổi nào chưa kết thúc và không buổi nào được phục vụ (mọi buổi bị hủy / CAREGIVER_MISSED) — OPS-04.
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
