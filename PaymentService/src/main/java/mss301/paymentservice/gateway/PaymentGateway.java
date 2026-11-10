package mss301.paymentservice.gateway;

import mss301.paymentservice.enums.PaymentProvider;

public interface PaymentGateway {
    PaymentProvider provider();

    // Cổng đã bật và đủ cấu hình — kiểm trước khi ghi DB để không tạo phiên mồ côi.
    boolean isEnabled();

    CheckoutResult createCheckout(CheckoutCommand command);
}
