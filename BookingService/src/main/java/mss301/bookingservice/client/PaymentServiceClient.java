package mss301.bookingservice.client;

import mss301.bookingservice.client.dto.RefundCreateRequest;
import mss301.bookingservice.client.dto.RefundQuoteRequest;
import mss301.bookingservice.client.dto.RefundQuoteResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.client.dto.RemainingInvoiceRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import mss301.bookingservice.client.dto.ComplaintRefundRequest;
import mss301.bookingservice.client.dto.PaymentPageResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * API của PaymentService chỉ dành cho service khác ({@code hasRole('SERVICE')}) — gọi bằng token SERVICE của Booking
 * (ServiceTokenInterceptor), khác PaymentClient (token người dùng). Lệnh ghi (refund, hóa đơn) chỉ gọi từ
 * BookingOutboxService (ngoài transaction, có retry; Payment khử trùng theo Idempotency-Key); preview qua PaymentPort.
 */
@FeignClient(name = "payment", contextId = "paymentServiceClient", url = "${booking.payment.url}",
        configuration = PaymentServiceClientConfig.class)
public interface PaymentServiceClient {

    @GetMapping("/api/v1/payments")
    RemoteApiResponse<PaymentPageResponse> listPayments(@RequestParam("bookingId") UUID bookingId,
            @RequestParam("status") String status, @RequestParam("page") int page, @RequestParam("size") int size);

    @PostMapping("/api/v1/refunds/complaints")
    void createComplaintRefund(@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody ComplaintRefundRequest request);

    String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    // C2.2: preview số tiền hoàn khi khách hủy (BK-03) — Payment không ghi DB; gọi đồng bộ, ngoài transaction.
    @PostMapping("/api/v1/refund-quotes")
    RemoteApiResponse<RefundQuoteResponse> quoteRefund(@RequestBody RefundQuoteRequest request);

    // C2.3: hoàn tiền buổi bị hủy / MISSED — Payment tính số tiền theo chính sách đã snapshot lúc cọc.
    @PostMapping("/api/v1/refunds")
    void createRefund(@RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
                      @RequestBody RefundCreateRequest request);

    @PostMapping("/api/v1/remaining-invoices")
    void createRemainingInvoice(@RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
                                @RequestBody RemainingInvoiceRequest request);
}
