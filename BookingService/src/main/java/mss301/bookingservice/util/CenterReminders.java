package mss301.bookingservice.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class CenterReminders {

    // Lớp tiện ích tĩnh, không cho khởi tạo.
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

    // Mốc nhắc lớn nhất (phút) trong cấu hình.
    public static int largest(List<Integer> reminderMinutes) {
        return reminderMinutes.stream().filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(0);
    }
}
