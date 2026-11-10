package mss301.providerservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CenterCreateRequest {

    @NotBlank(message = "CENTER_NAME_REQUIRED")
    @Size(max = 255, message = "CENTER_NAME_TOO_LONG")
    String centerName;

    @Pattern(regexp = "^$|^[0-9]{10,15}$", message = "INVALID_PHONE")
    String phoneNumber;

    @NotBlank(message = "CENTER_EMAIL_REQUIRED")
    @Email(message = "INVALID_EMAIL_FORMAT")
    @Size(max = 255, message = "INVALID_EMAIL_FORMAT")
    String email;

    @NotBlank(message = "CENTER_ADDRESS_REQUIRED")
    @Size(max = 2000, message = "CENTER_ADDRESS_TOO_LONG")
    String address;

    @Size(max = 500, message = "OPERATING_HOURS_TOO_LONG")
    String operatingHours;

    UUID managerUserId;
}
