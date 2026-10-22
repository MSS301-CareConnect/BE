package mss301.identityservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LogoutRequest {
    // Mobile gửi refresh trong body; web để trống (đọc từ cookie).
    @ToString.Exclude
    String refreshToken;

    // true → đăng xuất mọi phiên của tài khoản (thay cho /auth/logout-all cũ).
    Boolean allSessions;
}
