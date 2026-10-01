package mss301.identityservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.validation.StrongPassword;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResetPasswordRequest {
    @NotBlank(message = "EMAIL_REQUIRED")
    @Email(message = "INVALID_EMAIL_FORMAT")
    String email;

    @NotBlank(message = "OTP_REQUIRED")
    @Pattern(regexp = "[0-9]{6}", message = "INVALID_OTP")
    @ToString.Exclude
    String otpCode;

    @NotBlank(message = "PASSWORD_REQUIRED")
    @StrongPassword
    @ToString.Exclude
    String newPassword;
}
