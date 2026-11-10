package mss301.bookingservice.client;

import mss301.bookingservice.client.dto.AvailabilityCheckRequest;
import mss301.bookingservice.client.dto.AvailabilityCheckResponse;
import mss301.bookingservice.client.dto.ProviderCaregiverCardPageResponse;
import mss301.bookingservice.client.dto.ProviderCaregiverResponse;
import mss301.bookingservice.client.dto.ProviderScheduleResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;
import mss301.bookingservice.client.dto.ProviderCenterResponse;
import mss301.bookingservice.client.dto.CaregiverRatingRequest;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * API của ProviderService chỉ dành cho service khác (token SERVICE), khác ProviderClient (token người dùng).
 * C5.2: caregiver có lịch làm việc và không nghỉ trong các buổi đặt (PRV-02). Dùng qua CaregiverPort.
 */
@FeignClient(name = "provider", contextId = "providerServiceClient", url = "${booking.provider.url}",
        configuration = ProviderServiceClientConfig.class)
public interface ProviderServiceClient {
    @GetMapping("/api/v1/care-centers/{centerId}")
    RemoteApiResponse<ProviderCenterResponse> getCenter(@PathVariable("centerId") UUID centerId);

    @PutMapping("/api/v1/caregivers/{caregiverId}/rating")
    void updateRating(@PathVariable("caregiverId") UUID caregiverId, @RequestBody CaregiverRatingRequest request);


    // Hồ sơ caregiver (trung tâm, trạng thái, dịch vụ) — Provider chỉ cho ADMIN / manager / chính caregiver / SERVICE
    // (SEC-08), nên khách chọn caregiver lúc đặt lịch phải tra bằng token SERVICE của Booking.
    @GetMapping("/api/v1/caregivers/{caregiverId}")
    RemoteApiResponse<ProviderCaregiverResponse> getCaregiver(@PathVariable("caregiverId") UUID caregiverId);

    // SHIFT (spec §6): lịch caregiver khách chọn — trung tâm, caregiver, ngày giờ ca, trạng thái.
    @GetMapping("/api/v1/caregivers/schedules/{scheduleId}")
    RemoteApiResponse<ProviderScheduleResponse> getSchedule(@PathVariable("scheduleId") UUID scheduleId);

    @PostMapping("/api/v1/caregivers/availability-checks")
    RemoteApiResponse<AvailabilityCheckResponse> checkAvailability(@RequestBody AvailabilityCheckRequest request);

    // SRCH-01 — tự phân công (2026-11-09): caregiver VERIFIED + ACTIVE của trung tâm, làm được mọi dịch vụ, có lịch làm
    // việc / không nghỉ và không bận booking khác trong MỌI khung (`<startISO>/<endISO>`, tối đa 60 khung).
    @GetMapping("/api/v1/caregivers/search")
    RemoteApiResponse<ProviderCaregiverCardPageResponse> searchCaregivers(@RequestParam("centerId") UUID centerId,
                                                                          @RequestParam("careServiceIds") List<UUID> careServiceIds,
                                                                          @RequestParam("slots") List<String> slots,
                                                                          @RequestParam("size") int size);
}
