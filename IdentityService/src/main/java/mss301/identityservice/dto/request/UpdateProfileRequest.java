package mss301.identityservice.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateProfileRequest {
    @Size(max = 255, message = "INVALID_FULLNAME")
    @Pattern(regexp = ".*\\S.*", message = "FULLNAME_REQUIRED")
    String fullName;

    @Pattern(regexp = "[0-9]{10,11}", message = "INVALID_PHONE")
    String phoneNumber;
}
