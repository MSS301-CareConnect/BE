package mss301.providerservice.util;

import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.ShiftStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Trạng thái hiệu lực của ca — đúng ngay khi đọc, kể cả trong khoảng giữa hai lần chạy WorkShiftLifecycleScheduler:
 * OPEN đã tới giờ bắt đầu hoặc quá hạn đăng ký → CLOSED; OPEN / CLOSED đã qua giờ kết thúc → COMPLETED.
 * DRAFT, CANCELLED, COMPLETED giữ nguyên. Không bao giờ trả "đang mở" cho ca đã qua.
 */
public final class WorkShiftStates {

    private WorkShiftStates() {
    }

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

    public static Instant start(WorkShift shift) {
        return ProviderTime.toInstant(shift.getWorkDate(), shift.getStartTime());
    }

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

    private static boolean deadlinePassed(WorkShift shift, Instant now) {
        return shift.getRegistrationDeadline() != null && !now.isBefore(shift.getRegistrationDeadline());
    }
}
