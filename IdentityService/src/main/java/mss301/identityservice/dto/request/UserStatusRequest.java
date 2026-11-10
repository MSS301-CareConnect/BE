package mss301.identityservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mss301.identityservice.enums.UserStatus;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserStatusRequest {
    @NotNull(message = "INVALID_REQUEST")
    UserStatus status;
    @Size(max = 500, message = "INVALID_REQUEST")
    String reason;
}
