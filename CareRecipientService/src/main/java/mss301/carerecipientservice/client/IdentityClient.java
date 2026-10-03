package mss301.carerecipientservice.client;

import mss301.carerecipientservice.client.dto.IdentityApiResponse;
import mss301.carerecipientservice.client.dto.IdentityUserBatchRequest;
import mss301.carerecipientservice.client.dto.IdentityUserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * API nội bộ của IdentityService (/internal/users/**). Không có service discovery → URL qua env
 * CARE_RECIPIENT_IDENTITY_URL; header X-Internal-Secret, timeout, ErrorDecoder ở {@link IdentityClientConfig};
 * circuit breaker + fallback ở {@link IdentityClientFallbackFactory}. Dùng qua IdentityUserService, không gọi trực tiếp.
 */
@FeignClient(name = "identity", url = "${care-recipient.identity.url}",
        configuration = IdentityClientConfig.class, fallbackFactory = IdentityClientFallbackFactory.class)
public interface IdentityClient {

    @GetMapping("/internal/users/{userId}")
    IdentityApiResponse<IdentityUserResponse> get(@PathVariable("userId") UUID userId);

    @GetMapping("/internal/users/by-email")
    IdentityApiResponse<IdentityUserResponse> getByEmail(@RequestParam("email") String email);

    @PostMapping("/internal/users/batch")
    IdentityApiResponse<List<IdentityUserResponse>> list(@RequestBody IdentityUserBatchRequest request);
}
