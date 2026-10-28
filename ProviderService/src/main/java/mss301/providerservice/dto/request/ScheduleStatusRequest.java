package mss301.providerservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.ScheduleStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ScheduleStatusRequest {

    @NotNull(message = "INVALID_REQUEST")
    ScheduleStatus status;

    @Size(max = 500, message = "INVALID_REQUEST")
    String reason;

    @NotNull(message = "INVALID_REQUEST")
    Long version;
}
