package mss301.paymentservice.gateway;

import mss301.paymentservice.enums.PaymentProvider;
import mss301.paymentservice.exception.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PaymentGatewayFactory {
    private final Map<PaymentProvider, PaymentGateway> gateways = new EnumMap<>(PaymentProvider.class);

    public PaymentGatewayFactory(List<PaymentGateway> list) {
        list.forEach(g -> gateways.put(g.provider(), g));
    }

    public PaymentGateway get(PaymentProvider provider) {
        PaymentGateway gateway = gateways.get(provider);
        if (gateway == null) throw new AppException(ErrorCode.PAYMENT_PROVIDER_DISABLED);
        return gateway;
    }
}
