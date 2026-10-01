package mss301.identityservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RolePermissionsRequest {
    @NotNull(message = "INVALID_REQUEST")
    Set<@NotNull(message = "INVALID_REQUEST") UUID> permissionIds;
}
