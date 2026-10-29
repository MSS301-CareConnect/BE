package mss301.paymentservice.gateway;

import mss301.paymentservice.enums.PaymentProvider;
import mss301.paymentservice.exception.AppException;
import mss301.paymentservice.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentGatewayFactory {
    private final Map<PaymentProvider, PaymentGateway> gateways = new EnumMap<>(PaymentProvider.class);

    public PaymentGatewayFactory(List<PaymentGateway> paymentGateways) {
        for (PaymentGateway paymentGateway : paymentGateways) {
            gateways.put(paymentGateway.provider(), paymentGateway);
        }
    }

    // D16: chỉ VNPay; Stripe tắt sau cờ STRIPE_ENABLED (mặc định false).
    public PaymentGateway getEnabled(PaymentProvider provider) {
        PaymentGateway gateway = gateways.get(provider);
        if (gateway == null || !gateway.isEnabled()) {
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_DISABLED);
        }
        return gateway;
    }
}
