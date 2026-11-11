package mss301.bookingservice.enums;

import java.util.EnumSet;
import java.util.Set;

// Trạng thái một buổi chăm sóc theo spec §11.2 — đồng bộ CHECK booking_occurrences.status.
// PENDING_ASSIGNMENT (chưa có caregiver) → RESERVED (đã gắn caregiver, giữ lịch khi chờ xác nhận / thanh toán đầu)
// → SCHEDULED (đã trả khoản đầu) → ACKNOWLEDGED (caregiver nhận ca, OPS-01) → IN_PROGRESS (check-in) → COMPLETED.
// Chưa bắt đầu → CANCELLED | EXPIRED (booking hết hạn) | CAREGIVER_MISSED | CUSTOMER_NO_SHOW (OPS-06).
public enum OccurrenceStatus {
    PENDING_ASSIGNMENT,
    RESERVED,
    SCHEDULED,
    ACKNOWLEDGED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    CUSTOMER_NO_SHOW,
    CAREGIVER_MISSED;

    // Buổi chiếm lịch caregiver (spec §6) — đồng bộ exclusion constraint ex_booking_occurrences_caregiver_slot.
    public static final Set<OccurrenceStatus> CAREGIVER_HOLDING = EnumSet.of(RESERVED, SCHEDULED, ACKNOWLEDGED,
            IN_PROGRESS);

    // Buổi chưa kết thúc: chiếm giờ người bệnh (kiểm trùng lịch), quyền xem hồ sơ người bệnh.
    public static final Set<OccurrenceStatus> ACTIVE = EnumSet.of(PENDING_ASSIGNMENT, RESERVED, SCHEDULED, ACKNOWLEDGED,
            IN_PROGRESS);

    // Buổi chưa bắt đầu: còn đổi caregiver, hủy được.
    public static final Set<OccurrenceStatus> NOT_STARTED = EnumSet.of(PENDING_ASSIGNMENT, RESERVED, SCHEDULED,
            ACKNOWLEDGED);

    // Buổi đã lên lịch chính thức, caregiver làm việc được (nhận ca, check-in, báo vắng, MISSED).
    public static final Set<OccurrenceStatus> WORKABLE = EnumSet.of(SCHEDULED, ACKNOWLEDGED);

    // Kiểm tra buổi được chuyển sang trạng thái đích không.
    public boolean canTransitionTo(OccurrenceStatus targetStatus) {
        return switch (this) {
            // SCHEDULED: booking đã cọc khi chưa có người (2026-11-09) — gán người là lên lịch chính thức luôn.
            case PENDING_ASSIGNMENT -> targetStatus == RESERVED || targetStatus == SCHEDULED || targetStatus == CANCELLED
                    || targetStatus == EXPIRED;
            // PENDING_ASSIGNMENT: caregiver khách chọn không nhận đơn → trung tâm phân người khác (CR-3).
            case RESERVED -> targetStatus == SCHEDULED || targetStatus == CANCELLED || targetStatus == EXPIRED
                    || targetStatus == PENDING_ASSIGNMENT;
            // EXPIRED: booking quá hạn trả phần còn lại (đã trả khoản đầu nên buổi đã SCHEDULED).
            case SCHEDULED -> targetStatus == ACKNOWLEDGED || targetStatus == IN_PROGRESS || targetStatus == CANCELLED
                    || targetStatus == CAREGIVER_MISSED || targetStatus == CUSTOMER_NO_SHOW || targetStatus == EXPIRED;
            case ACKNOWLEDGED -> targetStatus == SCHEDULED || targetStatus == IN_PROGRESS || targetStatus == CANCELLED
                    || targetStatus == CAREGIVER_MISSED || targetStatus == CUSTOMER_NO_SHOW || targetStatus == EXPIRED;
            case IN_PROGRESS -> targetStatus == COMPLETED;
            // 2026-11-11: ca tự đóng mà trung tâm kết luận không thực hiện → coi như caregiver vắng (hoàn tiền khách).
            case COMPLETED -> targetStatus == CAREGIVER_MISSED;
            case CANCELLED, EXPIRED, CUSTOMER_NO_SHOW, CAREGIVER_MISSED -> false;
        };
    }
}
