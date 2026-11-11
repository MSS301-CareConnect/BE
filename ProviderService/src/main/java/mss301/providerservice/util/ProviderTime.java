package mss301.providerservice.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public final class ProviderTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    // Lớp tiện ích tĩnh, không cho khởi tạo.
    private ProviderTime() {
    }

    // Đổi ngày + giờ Việt Nam sang Instant.
    public static Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(ZONE).toInstant();
    }
}
