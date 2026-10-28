package mss301.paymentservice.gateway;

import mss301.paymentservice.enums.PaymentProvider;

public interface PaymentGateway {
    PaymentProvider provider();

    CheckoutResult createCheckout(CheckoutCommand command);
}
