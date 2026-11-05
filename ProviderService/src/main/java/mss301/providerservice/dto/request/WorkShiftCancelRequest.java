package mss301.providerservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WorkShiftCancelRequest {

    @NotBlank(message = "WORK_SHIFT_CANCEL_REASON_REQUIRED")
    @Size(max = 500, message = "WORK_SHIFT_CANCEL_REASON_REQUIRED")
    String reason;

    @NotNull(message = "WORK_SHIFT_VERSION_CONFLICT")
    Long version;
}
