package mss301.providerservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

// Thay toàn bộ kỹ năng của caregiver; [] = bỏ hết. Id trùng lặp được gộp ở service.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverSkillRequest {
    @NotNull(message = "CAREGIVER_SERVICES_REQUIRED")
    @Size(max = 50, message = "TOO_MANY_CAREGIVER_SERVICES")
    List<@NotNull(message = "CAREGIVER_SERVICES_REQUIRED") UUID> centerServiceIds;
}
