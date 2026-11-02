package mss301.bookingservice.enums;

import java.util.EnumSet;
import java.util.Set;

// Trạng thái booking theo spec §11.1 — đồng bộ CHECK bookings.status. Chuyển trạng thái chỉ qua BookingAction.
public enum BookingStatus {
    PENDING_CENTER_CONFIRMATION,
    PENDING_CAREGIVER_ASSIGNMENT,
    AWAITING_INITIAL_PAYMENT,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    CANCELLED,
    EXPIRED;

    // Booking còn giữ chỗ caregiver / người bệnh (tính trùng lịch). EXPIRED/REJECTED/CANCELLED/COMPLETED giải phóng.
    public static final Set<BookingStatus> SCHEDULE_HOLDING = EnumSet.of(PENDING_CENTER_CONFIRMATION,
            PENDING_CAREGIVER_ASSIGNMENT, AWAITING_INITIAL_PAYMENT, CONFIRMED, IN_PROGRESS);

    // Chưa trả khoản thanh toán đầu — tính vào giới hạn booking chờ của một khách (SEC-12).
    public static final Set<BookingStatus> AWAITING_CONFIRMATION = EnumSet.of(PENDING_CENTER_CONFIRMATION,
            PENDING_CAREGIVER_ASSIGNMENT, AWAITING_INITIAL_PAYMENT);

    // Caregiver thấy buổi được giao khi booking đã xác nhận (trước đó chưa lộ dữ liệu người bệnh).
    public static final Set<BookingStatus> CAREGIVER_VISIBLE = EnumSet.of(CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED);

    // Caregiver được xem hồ sơ người bệnh (CareRecipientService hỏi qua GET /api/v1/bookings/caregiver-access bằng
    // token SERVICE) — tính THEO TỪNG BUỔI được giao; booking CANCELLED / COMPLETED không còn quyền (SEC-09).
    public static final Set<BookingStatus> CAREGIVER_RECIPIENT_ACCESS = EnumSet.of(CONFIRMED, IN_PROGRESS);

    // Đã trả khoản thanh toán đầu — hủy buổi phải hoàn tiền qua PaymentService (BK-03 / BK-04).
    public static final Set<BookingStatus> DEPOSIT_PAID = EnumSet.of(CONFIRMED, IN_PROGRESS);

    // Còn đổi / phân caregiver được (chỉ áp cho buổi chưa bắt đầu).
    public static final Set<BookingStatus> CAREGIVER_CHANGEABLE = EnumSet.of(PENDING_CENTER_CONFIRMATION,
            PENDING_CAREGIVER_ASSIGNMENT, AWAITING_INITIAL_PAYMENT, CONFIRMED, IN_PROGRESS);

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED || this == EXPIRED;
    }
}
