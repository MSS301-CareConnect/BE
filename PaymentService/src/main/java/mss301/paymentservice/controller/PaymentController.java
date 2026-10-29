package mss301.paymentservice.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.dto.request.CreateCheckoutRequest;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.CheckoutResponse;
import mss301.paymentservice.dto.response.OutstandingDebtResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.dto.response.PaymentResponse;
import mss301.paymentservice.dto.response.PaymentReturnResponse;
import mss301.paymentservice.dto.response.RevenueResponse;
import mss301.paymentservice.dto.response.VnPayIpnResponse;
import mss301.paymentservice.enums.PaymentStatus;
import mss301.paymentservice.enums.PaymentType;
import mss301.paymentservice.enums.RoleCode;
import mss301.paymentservice.security.CurrentCenterId;
import mss301.paymentservice.security.CurrentRoles;
import mss301.paymentservice.security.CurrentUserId;
import mss301.paymentservice.service.PaymentService;
import mss301.paymentservice.service.RemainingInvoiceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Payments", description = "Phiên thanh toán cọc, tra cứu giao dịch, công nợ, doanh thu và callback cổng thanh toán")
public class PaymentController {
    PaymentService paymentService;
    RemainingInvoiceService remainingInvoiceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SERVICE','ADMIN','CUSTOMER','CENTER_MANAGER')")
    public APIResponse<PageResponse<PaymentResponse>> list(@CurrentUserId UUID actorId,
                                                           @CurrentRoles Set<RoleCode> roles,
                                                           @CurrentCenterId(required = false) UUID centerId,
                                                           @RequestParam(required = false) UUID bookingId,
                                                           @RequestParam(required = false) PaymentStatus status,
                                                           @RequestParam(required = false) PaymentType type,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return paymentService.list(actorId, roles, centerId, bookingId, status, type, page, size);
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('SERVICE','ADMIN','CUSTOMER','CENTER_MANAGER')")
    public APIResponse<PaymentResponse> get(@CurrentUserId UUID actorId, @CurrentRoles Set<RoleCode> roles,
                                            @CurrentCenterId(required = false) UUID centerId,
                                            @PathVariable UUID paymentId) {
        return paymentService.get(actorId, roles, centerId, paymentId);
    }

    @GetMapping("/by-booking/{bookingId}")
    @PreAuthorize("hasAnyRole('SERVICE','ADMIN','CUSTOMER','CENTER_MANAGER')")
    public APIResponse<PaymentResponse> getByBooking(@CurrentUserId UUID actorId, @CurrentRoles Set<RoleCode> roles,
                                                     @CurrentCenterId(required = false) UUID centerId,
                                                     @PathVariable UUID bookingId,
                                                     @RequestParam(required = false) PaymentType type) {
        return paymentService.getByBooking(actorId, roles, centerId, bookingId, type);
    }

    // BookingService gọi bằng token của chính khách để chặn đặt lịch mới khi còn nợ quá hạn (C2.6).
    @GetMapping("/me/outstanding")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<OutstandingDebtResponse> getOutstanding(@CurrentUserId UUID customerId) {
        return remainingInvoiceService.getOutstanding(customerId);
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<RevenueResponse> getRevenue(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                   @RequestParam(required = false) UUID centerId) {
        return paymentService.getRevenue(from, to, centerId);
    }

    // Ngoại lệ rule #3: VNPay chỉ đọc JSON thô {RspCode, Message}; xác thực bằng chữ ký HMAC thay cho JWT.
    @GetMapping("/vnpay/ipn")
    @SecurityRequirements({})
    public VnPayIpnResponse handleVnPayIpn(@RequestParam Map<String, String> params) {
        return paymentService.handleVnPayIpn(params);
    }

    // Trình duyệt quay về từ VNPay — không phải nguồn xác nhận; trạng thái chính thức đến từ IPN.
    @GetMapping("/vnpay/return")
    @SecurityRequirements({})
    public APIResponse<PaymentReturnResponse> handleVnPayReturn(@RequestParam Map<String, String> params) {
        return paymentService.handleVnPayReturn(params);
    }

    @GetMapping("/stripe/return")
    @SecurityRequirements({})
    public APIResponse<PaymentReturnResponse> handleStripeReturn(@RequestParam("session_id") String sessionId) {
        return paymentService.handleStripeReturn(sessionId);
    }

    @PostMapping("/bookings/{bookingId}/checkout")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CheckoutResponse> createCheckout(@CurrentUserId UUID customerId, @PathVariable UUID bookingId,
                                                        @Valid @RequestBody CreateCheckoutRequest request,
                                                        HttpServletRequest httpServletRequest) {
        return paymentService.createCheckout(customerId, bookingId, request, httpServletRequest);
    }

    // D16: chỉ chạy khi STRIPE_ENABLED=true; gateway không mở path này.
    @PostMapping(value = "/stripe/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements({})
    public APIResponse<Void> handleStripeWebhook(@RequestBody String payload,
                                                 @RequestHeader("Stripe-Signature") String signature) {
        return paymentService.handleStripeWebhook(payload, signature);
    }
}
