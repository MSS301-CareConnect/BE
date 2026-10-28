package mss301.paymentservice.gateway;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.config.PaymentProperties;
import mss301.paymentservice.enums.PaymentProvider;
import mss301.paymentservice.exception.*;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StripeGateway implements PaymentGateway {
    PaymentProperties properties;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public CheckoutResult createCheckout(CheckoutCommand c) {
        var config = properties.getStripe();
        if (!config.isEnabled() || blank(config.getSecretKey()))
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_DISABLED);
        try {
            long unitAmount = c.amount().setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT).setSuccessUrl(config.getSuccessUrl()).setCancelUrl(config.getCancelUrl())
                    .setClientReferenceId(c.reference()).setExpiresAt(c.expiresAt().getEpochSecond())
                    .putMetadata("paymentReference", c.reference())
                    .addLineItem(SessionCreateParams.LineItem.builder().setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder().setCurrency("vnd").setUnitAmount(unitAmount)
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("CareConnect booking " + c.bookingCode()).build()).build()).build()).build();
            Session session = Session.create(params, RequestOptions.builder().setApiKey(config.getSecretKey()).build());
            return new CheckoutResult(session.getId(), session.getUrl());
        } catch (StripeException | ArithmeticException e) {
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
