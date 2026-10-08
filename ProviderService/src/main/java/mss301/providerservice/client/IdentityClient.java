package mss301.providerservice.client;

import mss301.providerservice.client.dto.IdentityUserResponse;
import mss301.providerservice.dto.response.APIResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@FeignClient(name = "identity", url = "${provider.identity.url}",
        configuration = IdentityClientConfig.class, fallbackFactory = IdentityClientFallbackFactory.class)
public interface IdentityClient {
    @GetMapping("/internal/users/{userId}")
    APIResponse<IdentityUserResponse> get(@PathVariable("userId") UUID userId);
}
