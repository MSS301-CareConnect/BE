package mss301.providerservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.ShiftStatus;

// Mở / đóng đăng ký thủ công: chỉ nhận OPEN hoặc CLOSED (hủy ca đi qua /cancel).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WorkShiftStatusRequest {

    @NotNull(message = "WORK_SHIFT_STATUS_INVALID")
    ShiftStatus status;

    @NotNull(message = "WORK_SHIFT_VERSION_CONFLICT")
    Long version;
}
