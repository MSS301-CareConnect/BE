package mss301.bookingservice.service;

import mss301.bookingservice.client.dto.IdentityUserResponse;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

// Bọc IdentityClient (Feign). Không gọi trong DB transaction đang mở (rule #24).
public interface IdentityUserService {
    // Hồ sơ khách theo userId (batch ≤ 100 / lần). Identity lỗi → map rỗng (fallback), không ném lỗi.
    Map<UUID, IdentityUserResponse> getProfiles(Collection<UUID> userIds);
}
