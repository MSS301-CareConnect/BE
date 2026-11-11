package mss301.identityservice.service;

import mss301.identityservice.dto.response.AuthenticatedUser;
import mss301.identityservice.dto.response.UserResponse;

import java.util.UUID;

public interface CurrentIdentityService {
    // Nạp user + quyền HIỆN HÀNH từ DB; ném AuthenticationException nếu tài khoản không còn hợp lệ cho token này.
    UserResponse getActiveUser(UUID userId, long authzVersion);

    // Lấy người dùng đang hoạt động kèm authz_version để phát token.
    AuthenticatedUser getActiveAuthenticatedUser(UUID userId);
}
