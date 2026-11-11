package mss301.providerservice.util;

import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.ShiftStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class WorkShiftStates {

    // Lớp tiện ích tĩnh, không cho khởi tạo.
    private WorkShiftStates() {
    }

    // Trạng thái hiệu lực của ca theo thời điểm hiện tại (OPEN / CLOSED / COMPLETED).
    public static ShiftStatus effective(WorkShift shift, Instant now) {
        ShiftStatus stored = shift.getShiftStatus();
        if (stored != ShiftStatus.OPEN && stored != ShiftStatus.CLOSED) {
            return stored;
        }
        if (!now.isBefore(end(shift))) {
            return ShiftStatus.COMPLETED;
        }
        if (stored == ShiftStatus.OPEN && (!now.isBefore(start(shift)) || deadlinePassed(shift, now))) {
            return ShiftStatus.CLOSED;
        }
        return stored;
    }

    // Thời điểm bắt đầu ca.
    public static Instant start(WorkShift shift) {
        return ProviderTime.toInstant(shift.getWorkDate(), shift.getStartTime());
    }

    // Thời điểm kết thúc ca.
    public static Instant end(WorkShift shift) {
        return ProviderTime.toInstant(shift.getWorkDate(), shift.getEndTime());
    }

    /**
     * Hạn đăng ký mặc định: giờ bắt đầu − 24h; nếu đã qua thì − 1h; nếu cũng đã qua thì không đặt hạn (đăng ký đóng
     * lúc ca bắt đầu).
     */
    public static Instant defaultDeadline(Instant shiftStart, Instant now) {
        Instant dayBefore = shiftStart.minus(24, ChronoUnit.HOURS);
        if (dayBefore.isAfter(now)) {
            return dayBefore;
        }
        Instant hourBefore = shiftStart.minus(1, ChronoUnit.HOURS);
        return hourBefore.isAfter(now) ? hourBefore : null;
    }

    // Đã qua hạn đăng ký ca.
    private static boolean deadlinePassed(WorkShift shift, Instant now) {
        return shift.getRegistrationDeadline() != null && !now.isBefore(shift.getRegistrationDeadline());
    }
}
