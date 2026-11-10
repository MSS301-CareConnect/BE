package mss301.providerservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import mss301.providerservice.client.dto.RemoteApiResponse;
import mss301.providerservice.client.dto.BookingCountResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.UUID;

@FeignClient(name = "booking", contextId = "bookingArchiveClient", url = "${provider.booking.url}", configuration = BookingArchiveClientConfig.class)
public interface BookingArchiveClient {
    @GetMapping("/api/v1/bookings")
    RemoteApiResponse<BookingCountResponse> list(@RequestParam("centerId") UUID centerId, @RequestParam("status") String status,
                                                @RequestParam("page") int page, @RequestParam("size") int size);
}
