package mss301.providerservice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.ShiftStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WorkShiftRequest {

    @NotNull(message = "WORK_SHIFT_DATE_INVALID")
    LocalDate workDate;

    @NotNull(message = "WORK_SHIFT_TIME_INVALID")
    LocalTime startTime;

    @NotNull(message = "WORK_SHIFT_TIME_INVALID")
    LocalTime endTime;

    // Mã loại ca (util/ShiftTypes): MORNING, AFTERNOON, EVENING, NIGHT, FULL_DAY, CUSTOM; DAILY/WEEKLY cũ chỉ giữ khi sửa.
    @NotBlank(message = "WORK_SHIFT_TYPE_INVALID")
    @Size(max = 50, message = "WORK_SHIFT_TYPE_INVALID")
    String shiftType;

    @NotNull(message = "WORK_SHIFT_STATUS_INVALID")
    ShiftStatus shiftStatus;

    // 1–50 khi tạo hoặc khi đổi giá trị (WorkShiftServiceImpl); ca cũ lớn hơn 50 vẫn sửa được các trường khác.
    @NotNull(message = "WORK_SHIFT_CAPACITY_INVALID")
    @Min(value = 1, message = "WORK_SHIFT_CAPACITY_INVALID")
    @Max(value = 1000, message = "WORK_SHIFT_CAPACITY_INVALID")
    Integer capacity;

    // Bỏ trống khi tạo → mặc định giờ bắt đầu − 24h (hoặc − 1h nếu không còn kịp).
    Instant registrationDeadline;

    // ≤ 500 ký tự sau khi trim khi tạo hoặc khi đổi giá trị.
    @Size(max = 5000, message = "WORK_SHIFT_NOTE_TOO_LONG")
    String note;

    Long version;
}
