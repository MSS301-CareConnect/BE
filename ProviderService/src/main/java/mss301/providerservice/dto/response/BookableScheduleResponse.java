package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

// Lịch caregiver khách xem để đặt booking SHIFT (spec booking §3, §5.1) — chỉ ngày giờ ca, không lộ ghi chú / người duyệt.
// Ngày giờ là giờ Việt Nam như work_shifts. Booking cho biết lịch nào đã bị giữ.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookableScheduleResponse {
    UUID id;

    UUID caregiverId;

    UUID centerId;

    UUID workShiftId;

    LocalDate workDate;

    LocalTime startTime;

    LocalTime endTime;

    Integer maxBookings;
}
