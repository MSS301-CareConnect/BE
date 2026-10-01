package mss301.identityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.validation.StrongPassword;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangePasswordRequest {
    @NotBlank(message = "PASSWORD_REQUIRED")
    @ToString.Exclude
    String currentPassword;

    @NotBlank(message = "PASSWORD_REQUIRED")
    @StrongPassword
    @ToString.Exclude
    String newPassword;
}
