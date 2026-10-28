package mss301.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.dto.response.*;
import mss301.paymentservice.service.PaymentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@SecurityRequirements({})
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Payment callbacks", description = "Callback công khai do VNPay/Stripe gọi; frontend không tự giả mạo các request này")
public class PaymentCallbackController {
    PaymentService paymentService;

    @GetMapping("/vnpay/ipn")
    @Operation(summary = "VNPay IPN", description = "VNPay server gọi để xác nhận kết quả chính thức. PaymentService kiểm tra checksum, số tiền, cập nhật payment và báo BookingService đã thanh toán cọc.")
    public VnPayIpnResponse vnPayIpn(@RequestParam Map<String, String> params) {
        return paymentService.handleVnPayIpn(params);
    }

    @GetMapping("/vnpay/return")
    @Operation(summary = "VNPay Return URL", description = "Trình duyệt người dùng được VNPay chuyển về đây. Đây không phải nguồn xác nhận cuối cùng; trạng thái chính thức đến từ VNPay IPN.")
    public APIResponse<PaymentReturnResponse> vnPayReturn(@RequestParam Map<String, String> params) {
        return APIResponse.success("Nhận kết quả VNPay thành công", paymentService.handleVnPayReturn(params));
    }

    @PostMapping(value = "/stripe/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Stripe webhook", description = "Stripe gọi và ký request bằng Stripe-Signature. PaymentService xử lý checkout.session.completed/expired và cập nhật BookingService.")
    public APIResponse<Void> stripeWebhook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) {
        paymentService.handleStripeWebhook(payload, signature);
        return APIResponse.success("Webhook processed");
    }

    @GetMapping("/stripe/return")
    @Operation(summary = "Stripe success return", description = "Trình duyệt được Stripe Checkout chuyển về sau khi nhập thẻ. Frontend vẫn nên gọi API tra cứu payment vì webhook mới là nguồn xác nhận chính thức.")
    public APIResponse<PaymentReturnResponse> stripeReturn(@RequestParam("session_id") String sessionId) {
        return APIResponse.success("Nhận kết quả Stripe thành công", paymentService.handleStripeReturn(sessionId));
    }
}
