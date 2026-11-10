package mss301.providerservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.CenterStatus;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CenterStatusUpdateRequest {

    @NotNull(message = "CENTER_STATUS_REQUIRED")
    CenterStatus status;

    @Size(max = 500, message = "CENTER_STATUS_REASON_TOO_LONG")
    String reason;

    @NotNull(message = "CENTER_VERSION_REQUIRED")
    Long version;
}
