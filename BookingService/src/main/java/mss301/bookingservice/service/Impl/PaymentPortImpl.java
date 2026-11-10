package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.PaymentClient;
import mss301.bookingservice.client.PaymentServiceClient;
import mss301.bookingservice.client.dto.RefundQuoteRequest;
import mss301.bookingservice.client.dto.RefundQuoteResponse;
import mss301.bookingservice.client.dto.OutstandingDebtResponse;
import mss301.bookingservice.client.dto.PaymentPolicyResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.service.PaymentPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PaymentPortImpl implements PaymentPort {

    PaymentClient paymentClient;
    PaymentServiceClient paymentServiceClient;

    @Override
    public void checkNoOutstandingDebt() {
        RemoteApiResponse<OutstandingDebtResponse> response;
        try {
            response = paymentClient.findMyOutstanding();
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Outstanding debt check unavailable ({})", e.getClass().getSimpleName());
            throw new AppException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        }
        if (response == null || response.getResult() == null) {
            throw new AppException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        }
        if (response.getResult().isHasDebt()) {
            throw new AppException(ErrorCode.CUSTOMER_HAS_OUTSTANDING_DEBT);
        }
    }

    @Override
    public RefundQuoteResponse quoteRefund(RefundQuoteRequest request) {
        RemoteApiResponse<RefundQuoteResponse> response;
        try {
            response = paymentServiceClient.quoteRefund(request);
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Refund quote unavailable for booking {} ({})", request.getBookingId(), e.getClass().getSimpleName());
            throw new AppException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        }
        if (response == null || response.getResult() == null || response.getResult().getItems() == null) {
            throw new AppException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        }
        return response.getResult();
    }

    @Override
    public Optional<PaymentPolicyResponse> findEffective(UUID centerId, Instant at) {
        try {
            RemoteApiResponse<PaymentPolicyResponse> response = paymentClient.findEffectivePolicy(centerId, at);
            PaymentPolicyResponse policy = response == null ? null : response.getResult();
            if (policy == null || policy.getDepositPercent() == null) {
                return Optional.empty();
            }
            return Optional.of(policy);
        } catch (RuntimeException e) {
            // Fallback có chủ đích: cọc dự kiến chỉ để hiển thị, PaymentService quyết định số tiền thật khi tạo phiên cọc.
            log.warn("Payment policy unavailable for center {} ({})", centerId, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
