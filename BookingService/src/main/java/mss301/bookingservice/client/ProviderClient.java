package mss301.bookingservice.client;

import mss301.bookingservice.client.dto.ProviderCaregiverResponse;
import mss301.bookingservice.client.dto.ProviderCenterResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * API của ProviderService: trung tâm (đang hoạt động?) và caregiver (thuộc trung tâm, trạng thái,
 * kỹ năng, user_id Identity). Dùng qua CenterPort / CaregiverPort, không gọi trực tiếp.
 */
@FeignClient(name = "provider", url = "${booking.provider.url}",
        configuration = ProviderClientConfig.class, fallbackFactory = ProviderClientFallbackFactory.class)
public interface ProviderClient {

    @GetMapping("/api/v1/care-centers/{centerId}")
    RemoteApiResponse<ProviderCenterResponse> getCenter(@PathVariable("centerId") UUID centerId);

    @GetMapping("/api/v1/caregivers/{caregiverId}")
    RemoteApiResponse<ProviderCaregiverResponse> getCaregiver(@PathVariable("caregiverId") UUID caregiverId);
}
