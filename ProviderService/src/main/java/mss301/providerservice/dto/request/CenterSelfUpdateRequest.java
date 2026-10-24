package mss301.providerservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CenterSelfUpdateRequest {
    @Size(min = 1, max = 255, message = "CENTER_NAME_TOO_LONG")
    String centerName;
    @NotBlank(message = "INVALID_PHONE")
    @Pattern(regexp = "[0-9+]{10,15}", message = "INVALID_PHONE")
    String phoneNumber;
    @NotBlank(message = "CENTER_ADDRESS_REQUIRED")
    @Size(max = 2000, message = "CENTER_ADDRESS_TOO_LONG")
    String address;
    @Size(max = 500, message = "OPERATING_HOURS_TOO_LONG")
    String operatingHours;
}
