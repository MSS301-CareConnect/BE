package mss301.carerecipientservice.client;

import mss301.carerecipientservice.client.dto.BookingApiResponse;
import mss301.carerecipientservice.client.dto.CaregiverAccessResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * BookingService (qua ApiGateway): caregiver có đang được phân công booking còn hiệu lực cho người bệnh không. URL qua env
 * CARE_RECIPIENT_BOOKING_URL; endpoint yêu cầu hasRole('SERVICE') nên {@link BookingClientConfig} gắn token SERVICE
 * ({@link ServiceTokenInterceptor}); fallback fail-closed ở {@link BookingClientFallbackFactory}. Chỉ dùng trong
 * CareRecipientServiceImpl.get (nhánh CAREGIVER).
 */
@FeignClient(name = "booking", url = "${care-recipient.booking.url}",
        configuration = BookingClientConfig.class, fallbackFactory = BookingClientFallbackFactory.class)
public interface BookingClient {

    @GetMapping("/api/v1/bookings/caregiver-access")
    BookingApiResponse<CaregiverAccessResponse> checkCaregiverAccess(@RequestParam("caregiverUserId") UUID caregiverUserId,
                                                                     @RequestParam("recipientId") UUID recipientId);
}
