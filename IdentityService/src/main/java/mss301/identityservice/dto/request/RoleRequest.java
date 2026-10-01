package mss301.identityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoleRequest {
    @NotBlank(message = "ROLE_CODE_REQUIRED")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "INVALID_ROLE")
    String roleCode;

    @NotBlank(message = "ROLE_NAME_REQUIRED")
    String roleName;

    String description;
}
