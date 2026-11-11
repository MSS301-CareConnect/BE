package mss301.bookingservice.client;

import mss301.bookingservice.client.dto.ProviderCenterResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "provider", url = "${booking.provider.url}",
        configuration = ProviderClientConfig.class, fallbackFactory = ProviderClientFallbackFactory.class)
public interface ProviderClient {

    // Gọi ProviderService lấy thông tin trung tâm (token người dùng).
    @GetMapping("/api/v1/care-centers/{centerId}")
    RemoteApiResponse<ProviderCenterResponse> getCenter(@PathVariable("centerId") UUID centerId);

}
