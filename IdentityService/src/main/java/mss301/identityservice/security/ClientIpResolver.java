package mss301.identityservice.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.config.IdentityProperties;
import org.springframework.stereotype.Component;

/**
 * IP của người gọi cho rate limit (login, service-token). Chỉ tin X-Forwarded-For khi request tới từ proxy tin cậy
 * (gateway) và lấy địa chỉ CUỐI do proxy đó gắn — client không tự giả được IP để né / đổ lỗi cho IP khác.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClientIpResolver {
    static String FORWARDED_FOR = "X-Forwarded-For";
    static String UNKNOWN = "unknown";

    IdentityProperties identityProperties;

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (identityProperties.getTrustedProxies().contains(request.getRemoteAddr())
                && forwarded != null && !forwarded.isBlank()) {
            String[] addresses = forwarded.split(",");
            return addresses[addresses.length - 1].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : UNKNOWN;
    }
}
