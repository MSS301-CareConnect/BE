package mss301.paymentservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.PaymentPolicyResponse;
import mss301.paymentservice.entity.PlatformPolicy;
import mss301.paymentservice.enums.PolicyScope;
import mss301.paymentservice.exception.AppException;
import mss301.paymentservice.exception.ErrorCode;
import mss301.paymentservice.mapper.PaymentPolicyMapper;
import mss301.paymentservice.repository.PlatformPolicyRepository;
import mss301.paymentservice.service.PaymentPolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentPolicyServiceImpl implements PaymentPolicyService {
    PlatformPolicyRepository platformPolicyRepository;
    PaymentPolicyMapper paymentPolicyMapper;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PaymentPolicyResponse> getEffective(UUID centerId, Instant at) {
        PlatformPolicy policy = findEffective(centerId, at);
        PaymentPolicyResponse paymentPolicyResponse = paymentPolicyMapper.toResponse(policy);
        return APIResponse.success("Lấy chính sách thanh toán thành công", paymentPolicyResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformPolicy findEffective(UUID centerId, Instant at) {
        Instant effectiveAt = at == null ? clock.instant() : at;
        if (centerId != null) {
            Optional<PlatformPolicy> centerPolicy = platformPolicyRepository
                    .findFirstByScopeAndCenterIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(PolicyScope.CENTER,
                            centerId, effectiveAt);
            if (centerPolicy.isPresent()) {
                return centerPolicy.get();
            }
        }
        return platformPolicyRepository
                .findFirstByScopeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(PolicyScope.GLOBAL, effectiveAt)
                .orElseThrow(() -> new AppException(ErrorCode.NO_EFFECTIVE_POLICY));
    }
}
