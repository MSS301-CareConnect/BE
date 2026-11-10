package mss301.providerservice.client;

import mss301.providerservice.client.dto.CenterAccountsStatusRequest;
import mss301.providerservice.client.dto.SystemCancellationRequest;
import mss301.providerservice.client.dto.ServiceNotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.UUID;

@FeignClient(name = "booking", contextId = "centerCascadeClient", url = "${provider.booking.url}", configuration = CenterCascadeClientConfig.class)
public interface CenterCascadeClient {
    // Cùng gateway nên một base URL cho cả Identity và Booking.
    @PatchMapping("/api/v1/users/centers/{centerId}/status")
    void updateAccounts(@PathVariable("centerId") UUID centerId, @RequestBody CenterAccountsStatusRequest request);

    @PostMapping("/api/v1/bookings/system-cancellations")
    void cancelBookings(@RequestBody SystemCancellationRequest request);

    @PostMapping("/api/v1/notifications")
    void notifyManager(@RequestBody ServiceNotificationRequest request);
}
