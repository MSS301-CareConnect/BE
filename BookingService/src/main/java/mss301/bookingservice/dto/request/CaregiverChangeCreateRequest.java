package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CaregiverChangeReasonCode;
import mss301.bookingservice.enums.CaregiverChangeScopeType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverChangeCreateRequest {

    @NotNull(message = "SCOPE_TYPE_REQUIRED")
    CaregiverChangeScopeType scopeType;

    List<UUID> occurrenceIds;

    LocalDate fromDate;

    @NotNull(message = "REASON_CODE_REQUIRED")
    CaregiverChangeReasonCode reasonCode;

    @Size(max = 1000, message = "TEXT_TOO_LONG")
    String reasonDetail;

    UUID preferredCaregiverId;
}
