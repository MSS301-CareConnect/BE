package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Spec §3: lịch caregiver đã bị booking giữ (giao diện khóa lựa chọn). heldScheduleIds = lịch Provider đã có buổi SHIFT
// giữ (từ 2026-11-05 một ca nhận nhiều booking tới maxBookings — giao diện so scheduleBookingCounts với maxBookings); busySlots = mọi khoảng caregiver đã bận (kể cả RECURRING / CUSTOM) — không kèm thông tin booking / khách.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverHeldScheduleResponse {
    UUID caregiverId;

    List<UUID> heldScheduleIds;

    List<BusySlot> busySlots;

    // Trừ hao di chuyển / nghỉ giữa hai booking khác nhau (phút) — giao diện khóa thêm khoảng này quanh busySlots.
    Integer travelBufferMinutes;

    // Số booking khác nhau đang giữ buổi trên mỗi ca (caregiverScheduleId) — so với maxBookings của ca.
    Map<UUID, Integer> scheduleBookingCounts;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class BusySlot {
        Instant startAt;

        Instant endAt;

        UUID caregiverScheduleId;
    }
}
