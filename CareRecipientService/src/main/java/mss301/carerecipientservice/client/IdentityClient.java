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

@FeignClient(name = "identity", url = "${care-recipient.identity.url}",
        configuration = IdentityClientConfig.class, fallbackFactory = IdentityClientFallbackFactory.class)
public interface IdentityClient {

    // Gọi IdentityService lấy thông tin một người dùng.
    @GetMapping("/api/v1/users/{userId}")
    IdentityApiResponse<IdentityUserResponse> get(@PathVariable("userId") UUID userId);

    // Gọi IdentityService tìm người dùng theo email.
    @GetMapping("/api/v1/users/by-email")
    IdentityApiResponse<IdentityUserResponse> getByEmail(@RequestParam("email") String email);

    // Gọi IdentityService lấy thông tin nhiều người dùng một lần.
    @PostMapping("/api/v1/users/batch")
    IdentityApiResponse<List<IdentityUserResponse>> list(@RequestBody IdentityUserBatchRequest request);
}
