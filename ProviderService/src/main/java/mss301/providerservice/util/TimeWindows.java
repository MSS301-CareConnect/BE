package mss301.providerservice.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Phép toán thuần trên danh sách {@link TimeWindow} cho lịch rảnh caregiver (PRV-02): gộp ca liền kề/chồng nhau,
 * trừ ngày nghỉ, cắt theo khoảng tra cứu, kiểm một khung có nằm trọn trong lịch rảnh hay không.
 */
public final class TimeWindows {

    private TimeWindows() {
    }

    // Ca 08–12 và 12–16 gộp thành 08–16 để slot 10–14 được coi là nằm trọn trong lịch rảnh.
    public static List<TimeWindow> merge(List<TimeWindow> windows) {
        List<TimeWindow> sorted = new ArrayList<>(windows);
        sorted.sort(Comparator.comparing(TimeWindow::startAt));
        List<TimeWindow> merged = new ArrayList<>();
        for (TimeWindow window : sorted) {
            if (merged.isEmpty()) {
                merged.add(window);
                continue;
            }
            TimeWindow last = merged.getLast();
            if (!window.startAt().isAfter(last.endAt())) {
                Instant end = window.endAt().isAfter(last.endAt()) ? window.endAt() : last.endAt();
                merged.set(merged.size() - 1, new TimeWindow(last.startAt(), end));
            } else {
                merged.add(window);
            }
        }
        return merged;
    }

    public static List<TimeWindow> subtract(List<TimeWindow> windows, List<TimeWindow> removals) {
        List<TimeWindow> remaining = new ArrayList<>(windows);
        for (TimeWindow removal : removals) {
            List<TimeWindow> next = new ArrayList<>();
            for (TimeWindow window : remaining) {
                if (!window.overlaps(removal)) {
                    next.add(window);
                    continue;
                }
                if (window.startAt().isBefore(removal.startAt())) {
                    next.add(new TimeWindow(window.startAt(), removal.startAt()));
                }
                if (window.endAt().isAfter(removal.endAt())) {
                    next.add(new TimeWindow(removal.endAt(), window.endAt()));
                }
            }
            remaining = next;
        }
        return remaining;
    }

    public static List<TimeWindow> clip(List<TimeWindow> windows, Instant from, Instant to) {
        List<TimeWindow> clipped = new ArrayList<>();
        for (TimeWindow window : windows) {
            Instant start = window.startAt().isBefore(from) ? from : window.startAt();
            Instant end = window.endAt().isAfter(to) ? to : window.endAt();
            if (start.isBefore(end)) {
                clipped.add(new TimeWindow(start, end));
            }
        }
        return clipped;
    }

    // windows phải đã merge: khi đó slot hợp lệ khi và chỉ khi nằm trọn trong MỘT khung.
    public static boolean covers(List<TimeWindow> mergedWindows, TimeWindow slot) {
        for (TimeWindow window : mergedWindows) {
            if (window.contains(slot)) {
                return true;
            }
        }
        return false;
    }

    public static boolean overlapsAny(List<TimeWindow> windows, TimeWindow slot) {
        for (TimeWindow window : windows) {
            if (window.overlaps(slot)) {
                return true;
            }
        }
        return false;
    }
}
