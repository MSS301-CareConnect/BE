package mss301.carerecipientservice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

// primary = true → liên hệ này thành liên hệ chính (liên hệ chính cũ bị bỏ cờ); null khi PUT = giữ nguyên.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmergencyContactRequest {
    @NotBlank(message = "CONTACT_NAME_REQUIRED")
    @Size(max = 255, message = "TEXT_TOO_LONG")
    String fullName;

    @NotBlank(message = "CONTACT_RELATIONSHIP_REQUIRED")
    @Size(max = 100, message = "TEXT_TOO_LONG")
    String relationship;

    @NotBlank(message = "CONTACT_PHONE_REQUIRED")
    @Pattern(regexp = "[0-9]{10,11}", message = "INVALID_PHONE")
    String phoneNumber;

    Boolean primary;

    @Min(value = 0, message = "INVALID_PRIORITY")
    @Max(value = 100, message = "INVALID_PRIORITY")
    Integer priority;
}
