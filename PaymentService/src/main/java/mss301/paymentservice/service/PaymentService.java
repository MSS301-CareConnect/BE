package mss301.paymentservice.service;

import mss301.paymentservice.dto.response.*;
import mss301.paymentservice.enums.*;

import java.time.Instant;
import java.util.*;

public interface PaymentService {
    CheckoutResponse createCheckout(UUID customerId, UUID bookingId, PaymentProvider provider, String clientIp);

    PaymentResponse getById(UUID customerId, UUID paymentId);

    PaymentResponse getByBooking(UUID customerId, UUID bookingId);

    PageResponse<PaymentResponse> list(UUID bookingId, PaymentStatus status, int page, int size);

    OutstandingDebtResponse getOutstandingDebt(UUID customerId);

    PaymentPolicyResponse getEffectivePolicy(UUID centerId, Instant at);

    RevenueResponse getRevenue(Instant from, Instant to, UUID centerId);

    VnPayIpnResponse handleVnPayIpn(Map<String, String> parameters);

    PaymentReturnResponse handleVnPayReturn(Map<String, String> parameters);

    void handleStripeWebhook(String payload, String signature);

    PaymentReturnResponse handleStripeReturn(String sessionId);
}
