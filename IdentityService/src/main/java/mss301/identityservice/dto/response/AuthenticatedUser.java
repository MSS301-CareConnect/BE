package mss301.identityservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Kết quả kiểm tra thông tin đăng nhập — dùng nội bộ giữa AccountService và AuthenticationService
 * để phát token, không trả thẳng ra client.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticatedUser {
    UserResponse user;
    long authzVersion;
}
