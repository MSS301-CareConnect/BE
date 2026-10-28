package mss301.providerservice.client;

import mss301.providerservice.client.dto.BusyCaregiverClientRequest;
import mss301.providerservice.client.dto.BusyCaregiverClientResponse;
import mss301.providerservice.client.dto.RemoteApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// Gọi BookingService qua ApiGateway bằng token SERVICE (ServiceTokenInterceptor) — Booking là nguồn sự thật về giờ đã đặt.
@FeignClient(name = "booking", url = "${provider.booking.url}",
        configuration = BookingClientConfig.class, fallbackFactory = BookingClientFallbackFactory.class)
public interface BookingClient {

    // C5.1: caregiverIds ≤ 200, slots ≤ 60 mỗi lần gọi — bên gọi tự chia lô.
    @PostMapping("/api/v1/booking-occurrences/busy-caregivers")
    RemoteApiResponse<BusyCaregiverClientResponse> findBusyCaregivers(@RequestBody BusyCaregiverClientRequest request);
}
