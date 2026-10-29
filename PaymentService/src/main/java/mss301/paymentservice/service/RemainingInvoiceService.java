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
import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.enums.InvoiceStatus;
import mss301.paymentservice.enums.RoleCode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RemainingInvoiceService {
    APIResponse<PageResponse<RemainingInvoiceResponse>> list(UUID actorId, Set<RoleCode> roles, UUID customerId,
                                                             InvoiceStatus status, int page, int size);

    APIResponse<OutstandingDebtResponse> getOutstanding(UUID customerId);

    // C2.5: idempotent theo booking — gửi lại trả đúng hóa đơn đã tạo.
    APIResponse<RemainingInvoiceResultResponse> create(String idempotencyKey, RemainingInvoiceRequest request);

    APIResponse<CheckoutResponse> pay(UUID customerId, UUID invoiceId, CreateCheckoutRequest request,
                                      HttpServletRequest httpServletRequest);

    APIResponse<RemainingInvoiceResponse> waive(UUID actorId, UUID invoiceId, WaiveInvoiceRequest request);

    // Trong transaction IPN của payment REMAINING thành công. false = hóa đơn không còn mở (đã miễn / đã trả) → tiền thừa.
    boolean markPaid(Payment payment);

    List<UUID> findOverdueCandidateIds();

    void markOverdue(UUID invoiceId);
}
