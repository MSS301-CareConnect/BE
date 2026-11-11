package mss301.bookingservice.client;

import mss301.bookingservice.client.dto.OutstandingDebtResponse;
import mss301.bookingservice.client.dto.PaymentPolicyResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.UUID;

@FeignClient(name = "payment", url = "${booking.payment.url}", configuration = PaymentClientConfig.class)
public interface PaymentClient {

    // C2.6: khách đang nợ (hóa đơn REMAINING quá hạn) không được đặt booking mới — gọi bằng token của chính khách.
    @GetMapping("/api/v1/payments/me/outstanding")
    RemoteApiResponse<OutstandingDebtResponse> findMyOutstanding();

    // Gọi PaymentService lấy chính sách thanh toán hiệu lực của trung tâm.
    @GetMapping("/api/v1/payment-policies/effective")
    RemoteApiResponse<PaymentPolicyResponse> findEffectivePolicy(@RequestParam("centerId") UUID centerId,
                                                                 @RequestParam("at") Instant at);
}
