package mss301.providerservice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.ShiftStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WorkShiftBatchRequest {

    @NotEmpty(message = "WORK_SHIFT_BATCH_INVALID")
    @Size(max = 31, message = "WORK_SHIFT_BATCH_INVALID")
    List<@NotNull(message = "WORK_SHIFT_BATCH_INVALID") LocalDate> workDates;

    @NotNull(message = "WORK_SHIFT_TIME_INVALID")
    LocalTime startTime;

    @NotNull(message = "WORK_SHIFT_TIME_INVALID")
    LocalTime endTime;

    @NotBlank(message = "WORK_SHIFT_TYPE_INVALID")
    @Size(max = 50, message = "WORK_SHIFT_TYPE_INVALID")
    String shiftType;

    @NotNull(message = "WORK_SHIFT_STATUS_INVALID")
    ShiftStatus shiftStatus;

    @NotNull(message = "WORK_SHIFT_CAPACITY_INVALID")
    @Min(value = 1, message = "WORK_SHIFT_CAPACITY_INVALID")
    @Max(value = 50, message = "WORK_SHIFT_CAPACITY_INVALID")
    Integer capacity;

    @Min(value = 1, message = "WORK_SHIFT_CAPACITY_INVALID")
    @Max(value = 20, message = "WORK_SHIFT_CAPACITY_INVALID")
    Integer maxBookings;

    @Min(value = 60, message = "WORK_SHIFT_DEADLINE_INVALID")
    @Max(value = 10080, message = "WORK_SHIFT_DEADLINE_INVALID")
    Integer registrationLeadMinutes;

    @Size(max = 5000, message = "WORK_SHIFT_NOTE_TOO_LONG")
    String note;
}
