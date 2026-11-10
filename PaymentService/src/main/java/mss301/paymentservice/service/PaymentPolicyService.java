package mss301.paymentservice.service;

import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.PaymentPolicyResponse;
import mss301.paymentservice.entity.PlatformPolicy;

import java.time.Instant;
import java.util.UUID;

public interface PaymentPolicyService {
    APIResponse<PaymentPolicyResponse> getEffective(UUID centerId, Instant at);

    // Bản CENTER mới nhất có hiệu lực tại thời điểm at, không có thì GLOBAL; không có → NO_EFFECTIVE_POLICY.
    PlatformPolicy findEffective(UUID centerId, Instant at);
}
