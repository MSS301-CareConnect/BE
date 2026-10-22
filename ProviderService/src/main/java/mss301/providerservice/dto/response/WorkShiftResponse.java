package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
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
public class WorkShiftResponse {
    UUID id;
    UUID centerId;
    String centerName;
    LocalDate workDate;
    LocalTime startTime;
    LocalTime endTime;
    String shiftType;
    ShiftStatus shiftStatus;
    Integer capacity;
    long approvedCount;
    Instant registrationDeadline;
    String note;
    Long version;
    Instant createdAt;
    Instant updatedAt;
}
