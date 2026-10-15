package mss301.carerecipientservice.client;

import mss301.carerecipientservice.client.dto.BookingApiResponse;
import mss301.carerecipientservice.client.dto.CaregiverAccessResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * API nội bộ của BookingService: caregiver có đang được phân công booking còn hiệu lực cho người bệnh không. URL qua env
 * CARE_RECIPIENT_BOOKING_URL; header X-Internal-Secret ở {@link BookingClientConfig}; fallback fail-closed ở
 * {@link BookingClientFallbackFactory}. Dùng qua CaregiverAssignmentService, không gọi trực tiếp.
 */
@FeignClient(name = "booking", url = "${care-recipient.booking.url}",
        configuration = BookingClientConfig.class, fallbackFactory = BookingClientFallbackFactory.class)
public interface BookingClient {

    @GetMapping("/internal/bookings/caregiver-access")
    BookingApiResponse<CaregiverAccessResponse> checkCaregiverAccess(@RequestParam("caregiverUserId") UUID caregiverUserId,
                                                                     @RequestParam("recipientId") UUID recipientId);
}
