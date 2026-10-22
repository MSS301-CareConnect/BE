package mss301.bookingservice.enums;

import java.util.EnumSet;
import java.util.Set;

// Trạng thái booking — đồng bộ CHECK bookings.status. Chuyển trạng thái chỉ qua BookingAction.
public enum BookingStatus {
    PENDING,
    AWAITING_DEPOSIT,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    CANCELLED,
    EXPIRED;

    // Booking còn giữ chỗ caregiver / người bệnh (tính trùng lịch). EXPIRED/REJECTED/CANCELLED/COMPLETED giải phóng.
    public static final Set<BookingStatus> SCHEDULE_HOLDING = EnumSet.of(PENDING, AWAITING_DEPOSIT, CONFIRMED, IN_PROGRESS);

    // Caregiver thấy buổi được giao khi booking đã xác nhận (trước đó chưa lộ dữ liệu người bệnh).
    public static final Set<BookingStatus> CAREGIVER_VISIBLE = EnumSet.of(CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED);

    // Caregiver được xem hồ sơ người bệnh (CareRecipientService hỏi qua GET /api/v1/bookings/caregiver-access bằng
    // token SERVICE) — tính THEO TỪNG BUỔI được giao; booking CANCELLED / COMPLETED không còn quyền (SEC-09).
    public static final Set<BookingStatus> CAREGIVER_RECIPIENT_ACCESS = EnumSet.of(CONFIRMED, IN_PROGRESS);

    // Đã đặt cọc — hủy buổi phải hoàn tiền qua PaymentService (BK-03 / BK-04).
    public static final Set<BookingStatus> DEPOSIT_PAID = EnumSet.of(CONFIRMED, IN_PROGRESS);

    // Còn đổi caregiver được (chỉ áp cho buổi chưa bắt đầu).
    public static final Set<BookingStatus> CAREGIVER_CHANGEABLE = EnumSet.of(PENDING, AWAITING_DEPOSIT, CONFIRMED, IN_PROGRESS);

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED || this == EXPIRED;
    }
}
