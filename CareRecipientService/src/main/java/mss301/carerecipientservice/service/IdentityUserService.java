package mss301.carerecipientservice.service;

import mss301.carerecipientservice.client.dto.IdentityUserResponse;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

// Bọc IdentityClient (Feign). Không gọi trong DB transaction đang mở (rule #24).
public interface IdentityUserService {
    // Tài khoản CUSTOMER đang ACTIVE theo email; không thỏa → MANAGER_USER_INVALID; Identity lỗi → IDENTITY_UNAVAILABLE.
    IdentityUserResponse getActiveCustomer(String email);

    // Profile theo userId (gọi batch). Identity lỗi → map rỗng (fallback), không ném lỗi.
    Map<UUID, IdentityUserResponse> getProfiles(Collection<UUID> userIds);
}
