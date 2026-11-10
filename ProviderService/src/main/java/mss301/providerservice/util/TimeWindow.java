package mss301.providerservice.util;

import java.time.Instant;

// Khoảng thời gian nửa mở [startAt, endAt) dùng khi tính lịch rảnh (PRV-02) — giá trị nội bộ, không phải DTO API.
public record TimeWindow(Instant startAt, Instant endAt) {

    public boolean overlaps(TimeWindow other) {
        return startAt.isBefore(other.endAt) && endAt.isAfter(other.startAt);
    }

    public boolean contains(TimeWindow other) {
        return !startAt.isAfter(other.startAt) && !endAt.isBefore(other.endAt);
    }
}
