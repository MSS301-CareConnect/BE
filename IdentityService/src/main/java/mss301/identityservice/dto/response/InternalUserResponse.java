package mss301.identityservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.enums.UserStatus;

import java.util.Set;
import java.util.UUID;

// Thông tin tài khoản cho service khác qua /internal/** — không có permission, centerId hay dữ liệu đăng nhập.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InternalUserResponse {
    UUID id;
    String email;
    String fullName;
    String phoneNumber;
    UserStatus status;
    Set<String> roles;
}
