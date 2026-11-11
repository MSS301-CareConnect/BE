package mss301.identityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

// Sửa role bổ sung: mã role không đổi được (đã nằm trong token và audit).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoleUpdateRequest {
    @NotBlank(message = "ROLE_NAME_REQUIRED")
    String roleName;

    String description;

    // null = giữ nguyên trạng thái hiện tại.
    Boolean active;
}
