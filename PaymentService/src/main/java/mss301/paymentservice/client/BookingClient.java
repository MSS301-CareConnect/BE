package mss301.paymentservice.client;

import mss301.paymentservice.config.FeignConfig;
import mss301.paymentservice.dto.request.DepositPaidRequest;
import mss301.paymentservice.dto.response.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@FeignClient(name="payment-booking-client", url="${payment.booking-url}", configuration=FeignConfig.class)
public interface BookingClient {
    @GetMapping("/api/v1/bookings/{id}/payment-context")
    APIResponse<BookingPaymentContextResponse> getPaymentContext(@PathVariable UUID id);

    @PostMapping("/api/v1/bookings/{id}/deposit-paid")
    APIResponse<Void> markDepositPaid(@PathVariable UUID id, @RequestBody DepositPaidRequest request);
}
