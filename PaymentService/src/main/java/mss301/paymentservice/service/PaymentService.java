package mss301.paymentservice.service;

import jakarta.servlet.http.HttpServletRequest;
import mss301.paymentservice.dto.request.CreateCheckoutRequest;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.CheckoutResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.dto.response.PaymentResponse;
import mss301.paymentservice.dto.response.PaymentReturnResponse;
import mss301.paymentservice.dto.response.RevenueResponse;
import mss301.paymentservice.dto.response.VnPayIpnResponse;
import mss301.paymentservice.enums.PaymentStatus;
import mss301.paymentservice.enums.PaymentType;
import mss301.paymentservice.enums.RoleCode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface PaymentService {
    APIResponse<PaymentResponse> get(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID paymentId);

    APIResponse<PaymentResponse> getByBooking(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID bookingId,
                                              PaymentType paymentType);

    APIResponse<PageResponse<PaymentResponse>> list(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID bookingId,
                                                    PaymentStatus status, PaymentType paymentType, int page, int size);

    APIResponse<RevenueResponse> getRevenue(Instant from, Instant to, UUID centerId);

    APIResponse<CheckoutResponse> createCheckout(UUID customerId, UUID bookingId, CreateCheckoutRequest request,
                                                 HttpServletRequest httpServletRequest);

    // Ngoại lệ rule #3: JSON thô {RspCode, Message} theo chuẩn VNPay.
    VnPayIpnResponse handleVnPayIpn(Map<String, String> params);

    APIResponse<PaymentReturnResponse> handleVnPayReturn(Map<String, String> params);

    APIResponse<Void> handleStripeWebhook(String payload, String signature);

    APIResponse<PaymentReturnResponse> handleStripeReturn(String sessionId);

    // PAY-07: phiên PENDING đã quá hạn + thời gian chờ IPN trễ.
    List<UUID> findStaleAttemptIds();

    // Hỏi lại VNPay (querydr, nếu bật) ngoài transaction rồi ghi nhận SUCCESS hoặc EXPIRED.
    void reconcileAttempt(UUID attemptId);
}
