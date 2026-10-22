package mss301.bookingservice.client;

import mss301.bookingservice.client.dto.OutstandingDebtResponse;
import mss301.bookingservice.client.dto.PaymentPolicyResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.UUID;

/**
 * API của PaymentService (qua gateway). C2.1: chính sách thanh toán hiệu lực của trung tâm — Booking chỉ dùng
 * depositPercent để HIỂN THỊ cọc dự kiến khi báo giá (BK-01). Dùng qua PaymentPort, không gọi trực tiếp.
 */
@FeignClient(name = "payment", url = "${booking.payment.url}", configuration = PaymentClientConfig.class)
public interface PaymentClient {

    // C2.6: khách đang nợ (hóa đơn REMAINING quá hạn) không được đặt booking mới — gọi bằng token của chính khách.
    @GetMapping("/api/v1/payments/me/outstanding")
    RemoteApiResponse<OutstandingDebtResponse> findMyOutstanding();

    @GetMapping("/api/v1/payment-policies/effective")
    RemoteApiResponse<PaymentPolicyResponse> findEffectivePolicy(@RequestParam("centerId") UUID centerId,
                                                                 @RequestParam("at") Instant at);
}
