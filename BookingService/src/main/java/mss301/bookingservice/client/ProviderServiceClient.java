package mss301.bookingservice.client;

import mss301.bookingservice.client.dto.AvailabilityCheckRequest;
import mss301.bookingservice.client.dto.AvailabilityCheckResponse;
import mss301.bookingservice.client.dto.ProviderCaregiverResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * API của ProviderService chỉ dành cho service khác (token SERVICE), khác ProviderClient (token người dùng).
 * C5.2: caregiver có lịch làm việc và không nghỉ trong các buổi đặt (PRV-02). Dùng qua CaregiverPort.
 */
@FeignClient(name = "provider", contextId = "providerServiceClient", url = "${booking.provider.url}",
        configuration = ProviderServiceClientConfig.class)
public interface ProviderServiceClient {

    // Hồ sơ caregiver (trung tâm, trạng thái, dịch vụ) — Provider chỉ cho ADMIN / manager / chính caregiver / SERVICE
    // (SEC-08), nên khách chọn caregiver lúc đặt lịch phải tra bằng token SERVICE của Booking.
    @GetMapping("/api/v1/caregivers/{caregiverId}")
    RemoteApiResponse<ProviderCaregiverResponse> getCaregiver(@PathVariable("caregiverId") UUID caregiverId);

    @PostMapping("/api/v1/caregivers/availability-checks")
    RemoteApiResponse<AvailabilityCheckResponse> checkAvailability(@RequestBody AvailabilityCheckRequest request);
}
