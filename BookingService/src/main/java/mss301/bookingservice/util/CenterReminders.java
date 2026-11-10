package mss301.bookingservice.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Mốc nhắc trung tâm trước hạn xác nhận / phân công (booking.schedule.center-reminder-minutes, phút trước hạn). Lưu mốc
 * nhỏ nhất đã gửi trên booking: mỗi mốc nhắc một lần, mốc đã qua lúc bắt đầu chờ thì bỏ (không nhắc dồn).
 */
public final class CenterReminders {

    private CenterReminders() {
    }

    // Mốc nhỏ nhất đã tới (now ≥ due − mốc); null khi chưa tới mốc nào.
    public static Integer passed(List<Integer> reminderMinutes, Instant dueAt, Instant now) {
        if (dueAt == null || reminderMinutes == null) {
            return null;
        }
        return reminderMinutes.stream()
                .filter(Objects::nonNull)
                .filter(minutes -> !now.isBefore(dueAt.minus(Duration.ofMinutes(minutes))))
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    public static int largest(List<Integer> reminderMinutes) {
        return reminderMinutes.stream().filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(0);
    }
}
