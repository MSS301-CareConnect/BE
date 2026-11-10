package mss301.providerservice.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Giờ ca (work_date + start/end_time) là giờ địa phương Việt Nam, không phải UTC (WORK_PLAN §3.4) — mọi quy đổi sang
 * Instant đi qua đây để không còn chỗ nào dùng {@code ZoneOffset.UTC} (lệch 7 giờ).
 */
public final class ProviderTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private ProviderTime() {
    }

    public static Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(ZONE).toInstant();
    }
}
