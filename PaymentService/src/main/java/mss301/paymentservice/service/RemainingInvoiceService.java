package mss301.paymentservice.service;

import jakarta.servlet.http.HttpServletRequest;
import mss301.paymentservice.dto.request.CreateCheckoutRequest;
import mss301.paymentservice.dto.request.RemainingInvoiceRequest;
import mss301.paymentservice.dto.request.WaiveInvoiceRequest;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.CheckoutResponse;
import mss301.paymentservice.dto.response.OutstandingDebtResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.dto.response.RemainingInvoiceResponse;
import mss301.paymentservice.dto.response.RemainingInvoiceResultResponse;
import mss301.paymentservice.entity.BookingFinancial;
import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.enums.InvoiceStatus;
import mss301.paymentservice.enums.RoleCode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RemainingInvoiceService {
    // Danh sách hóa đơn phần còn lại.
    APIResponse<PageResponse<RemainingInvoiceResponse>> list(UUID actorId, Set<RoleCode> roles, UUID customerId,
                                                             InvoiceStatus status, int page, int size);

    // Tổng nợ quá hạn của khách.
    APIResponse<OutstandingDebtResponse> getOutstanding(UUID customerId);

    // C2.5: idempotent theo booking — gửi lại trả đúng hóa đơn đã tạo.
    APIResponse<RemainingInvoiceResultResponse> create(String idempotencyKey, RemainingInvoiceRequest request);

    // Thanh toán hóa đơn phần còn lại.
    APIResponse<CheckoutResponse> pay(UUID customerId, UUID invoiceId, CreateCheckoutRequest request,
                                      HttpServletRequest httpServletRequest);

    // Miễn thu hóa đơn.
    APIResponse<RemainingInvoiceResponse> waive(UUID actorId, UUID invoiceId, WaiveInvoiceRequest request);

    // Spec §9.1: trong transaction IPN của khoản đầu (financial đã khóa) — còn phần chưa trả thì mở hóa đơn, hạn trước
    // buổi đầu. Không có hạn phần còn lại (SHIFT 100 %, booking cũ) → không làm gì.
    void openBeforeService(BookingFinancial financial);

    // Trong transaction IPN của payment REMAINING thành công. false = hóa đơn không còn mở (đã miễn / đã trả / hết hạn)
    // → tiền thừa. Hóa đơn trước buổi đầu: xếp outbox báo Booking đã trả đủ.
    boolean markPaid(Payment payment, String paymentReference);

    // Lấy id hóa đơn quá hạn.
    List<UUID> findOverdueCandidateIds();

    // Trước khi giữ cọc vì quá hạn: caregiver của buổi có thể được phân / đổi sau lúc khách trả khoản đầu → đọc lại từ
    // Booking (ngoài transaction). Booking lỗi thì giữ bản chụp cũ, không chặn việc xử lý quá hạn.
    void refreshCaregivers(UUID invoiceId);

    // Đánh dấu hóa đơn quá hạn.
    void markOverdue(UUID invoiceId);
}
