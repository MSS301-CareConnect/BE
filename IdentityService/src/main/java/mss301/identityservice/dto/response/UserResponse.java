package mss301.identityservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.enums.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    UUID id;
    String email;
    String fullName;
    String phoneNumber;
    UserStatus status;
    UUID centerId;
    Set<String> roles;
    Set<String> permissions;
    Instant createdAt;
}
