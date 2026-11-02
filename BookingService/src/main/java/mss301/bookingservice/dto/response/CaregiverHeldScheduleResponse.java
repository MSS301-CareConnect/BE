package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Spec §3: lịch caregiver đã bị booking giữ (giao diện khóa lựa chọn). heldScheduleIds = lịch Provider đã có buổi SHIFT
// giữ; busySlots = mọi khoảng caregiver đã bận (kể cả RECURRING / CUSTOM) — không kèm thông tin booking / khách.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverHeldScheduleResponse {
    UUID caregiverId;
    List<UUID> heldScheduleIds;
    List<BusySlot> busySlots;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class BusySlot {
        Instant startAt;
        Instant endAt;
    }
}
