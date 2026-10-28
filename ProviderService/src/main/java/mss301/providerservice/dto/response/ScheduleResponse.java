package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.ScheduleStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ScheduleResponse {
    UUID id;
    UUID registrationId;
    UUID caregiverId;
    String caregiverName;
    UUID workShiftId;
    UUID centerId;
    LocalDate workDate;
    LocalTime startTime;
    LocalTime endTime;
    ScheduleStatus scheduleStatus;
    UUID assignedBy;
    Instant assignedAt;
    String note;
    String cancellationReason;
    Instant cancelledAt;
    Long version;
    Instant createdAt;
    Instant updatedAt;
}
