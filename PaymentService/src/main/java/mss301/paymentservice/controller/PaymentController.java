package mss301.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.dto.request.CreateCheckoutRequest;
import mss301.paymentservice.dto.response.*;
import mss301.paymentservice.enums.PaymentStatus;
import mss301.paymentservice.security.CurrentUserId;
import mss301.paymentservice.service.PaymentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Payments", description = "Tạo phiên thanh toán, tra cứu giao dịch và thống kê doanh thu")
public class PaymentController {
    PaymentService paymentService;

    @PostMapping("/bookings/{bookingId}/checkout")
    @Operation(
            summary = "Tạo phiên thanh toán cho booking",
            description = "Chỉ CUSTOMER sở hữu booking được gọi. Booking phải ở trạng thái AWAITING_DEPOSIT. "
                    + "Chọn provider VNPAY hoặc STRIPE; frontend mở checkoutUrl trong kết quả để người dùng thanh toán."
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CheckoutResponse> checkout(@CurrentUserId UUID customerId, @PathVariable UUID bookingId,
                                                  @Valid @RequestBody CreateCheckoutRequest body, HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }

        else ip = ip.split(",")[0].trim();
        return APIResponse.success("Tạo phiên thanh toán thành công",
                paymentService.createCheckout(customerId, bookingId, body.getProvider(), ip));
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Xem giao dịch theo paymentId", description = "Chỉ CUSTOMER sở hữu giao dịch được xem.")
    public APIResponse<PaymentResponse> get(@CurrentUserId UUID customerId, @PathVariable UUID paymentId) {
        return APIResponse.success("Lấy giao dịch thành công", paymentService.getById(customerId, paymentId));
    }

    @GetMapping("/by-booking/{bookingId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Xem giao dịch của một booking", description = "Dùng endpoint này để frontend kiểm tra trạng thái thanh toán chính thức sau khi quay về từ cổng thanh toán.")
    public APIResponse<PaymentResponse> getByBooking(@CurrentUserId UUID customerId, @PathVariable UUID bookingId) {
        return APIResponse.success("Lấy giao dịch thành công", paymentService.getByBooking(customerId, bookingId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SERVICE','ADMIN')")
    @Operation(summary = "Danh sách giao dịch", description = "Dành cho ADMIN hoặc service nội bộ; có thể lọc theo bookingId và status.")
    public APIResponse<PageResponse<PaymentResponse>> list(@RequestParam(required = false) UUID bookingId,
                                                           @RequestParam(required = false) PaymentStatus status,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return APIResponse.success("Lấy danh sách giao dịch thành công",
                paymentService.list(bookingId, status, page, Math.min(size, 100)));
    }

    @GetMapping("/me/outstanding")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Kiểm tra công nợ của khách hàng hiện tại", description = "BookingService gọi để chặn tạo booking mới khi khách còn công nợ.")
    public APIResponse<OutstandingDebtResponse> outstanding(@CurrentUserId UUID customerId) {
        return APIResponse.success("Kiểm tra công nợ thành công", paymentService.getOutstandingDebt(customerId));
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Thống kê doanh thu", description = "Tổng hợp các payment SUCCESS trong khoảng thời gian; centerId là bộ lọc tùy chọn.")
    public APIResponse<RevenueResponse> revenue(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                @RequestParam(required = false) UUID centerId) {
        return APIResponse.success("Thống kê doanh thu thành công", paymentService.getRevenue(from, to, centerId));
    }
}
