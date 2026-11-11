package mss301.paymentservice.client;

import mss301.paymentservice.client.dto.BookingNotificationRequest;
import mss301.paymentservice.client.dto.BookingPaymentContextResponse;
import mss301.paymentservice.client.dto.DepositPaidRequest;
import mss301.paymentservice.client.dto.RemoteApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

// API của BookingService chỉ dành cho service (hasRole('SERVICE')) — gọi qua gateway bằng token SERVICE của Payment.
@FeignClient(name = "payment-booking-client", url = "${payment.booking-url}", configuration = BookingClientConfig.class)
public interface BookingClient {

    // Gọi BookingService lấy thông tin booking cần cho thanh toán.
    @GetMapping("/api/v1/bookings/{id}/payment-context")
    RemoteApiResponse<BookingPaymentContextResponse> getPaymentContext(@PathVariable("id") UUID id);

    // Báo BookingService đã nhận tiền cọc.
    @PostMapping("/api/v1/bookings/{id}/deposit-paid")
    RemoteApiResponse<Object> markDepositPaid(@PathVariable("id") UUID id, @RequestBody DepositPaidRequest request);

    // Báo BookingService đã nhận phần tiền còn lại.
    @PostMapping("/api/v1/bookings/{id}/remaining-paid")
    RemoteApiResponse<Object> markRemainingPaid(@PathVariable("id") UUID id, @RequestBody DepositPaidRequest request);

    // Báo BookingService hóa đơn còn lại đã quá hạn.
    @PostMapping("/api/v1/bookings/{id}/payment-overdue")
    RemoteApiResponse<Object> markPaymentOverdue(@PathVariable("id") UUID id);

    // Thông báo in-app thuộc BookingService (2026-11-10: hoàn tiền xong).
    @PostMapping("/api/v1/notifications")
    RemoteApiResponse<Object> createNotification(@RequestBody BookingNotificationRequest request);
}
