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
    // Trạng thái hiệu lực (util/WorkShiftStates): ca đã qua không bao giờ là OPEN.
    ShiftStatus shiftStatus;
    Integer capacity;
    Integer maxBookings;
    long approvedCount;
    // Đăng ký đang chờ duyệt (badge "N chờ duyệt" phía trung tâm).
    long pendingCount;
    Instant registrationDeadline;
    String note;
    String cancellationReason;
    Instant cancelledAt;
    Long version;
    Instant createdAt;
    Instant updatedAt;
}
