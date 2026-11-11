package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.RegistrationStatus;
import mss301.providerservice.enums.ScheduleStatus;
import mss301.providerservice.enums.ShiftStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ShiftRegistrationResponse {
    UUID id;

    UUID workShiftId;

    UUID caregiverId;

    String caregiverName;

    RegistrationStatus registrationStatus;

    String caregiverNote;

    UUID reviewedBy;

    Instant reviewedAt;

    String rejectionReason;

    Long version;

    // Tóm tắt ca (đọc kèm, khỏi gọi GET /work-shifts/{id} cho từng đăng ký); shiftStatus là trạng thái hiệu lực.
    LocalDate workDate;

    LocalTime startTime;

    LocalTime endTime;

    String shiftType;

    ShiftStatus shiftStatus;

    String shiftCancellationReason;

    // Lịch (khung rảnh) sinh ra khi duyệt — rút đăng ký đã duyệt qua PATCH /caregivers/schedules/{scheduleId}/status.
    UUID scheduleId;

    ScheduleStatus scheduleStatus;

    Long scheduleVersion;

    Instant createdAt;

    Instant updatedAt;
}
