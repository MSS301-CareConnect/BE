package mss301.paymentservice.gateway;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.config.PaymentProperties;
import mss301.paymentservice.enums.PaymentProvider;
import mss301.paymentservice.exception.AppException;
import mss301.paymentservice.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;

// D16: giữ code Stripe nhưng chỉ chạy khi STRIPE_ENABLED=true; không hoàn tiền qua Stripe.
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class StripeGateway implements PaymentGateway {
    static String STRIPE_CURRENCY = "vnd";

    PaymentProperties paymentProperties;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public boolean isEnabled() {
        PaymentProperties.Stripe stripe = paymentProperties.getStripe();
        return stripe.isEnabled() && stripe.getSecretKey() != null && !stripe.getSecretKey().isBlank();
    }

    @Override
    public CheckoutResult createCheckout(CheckoutCommand command) {
        PaymentProperties.Stripe stripe = paymentProperties.getStripe();
        try {
            long unitAmount = command.amount().setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            SessionCreateParams.LineItem.PriceData.ProductData productData =
                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                            .setName("CareConnect " + command.orderLabel())
                            .build();
            SessionCreateParams.LineItem.PriceData priceData = SessionCreateParams.LineItem.PriceData.builder()
                    .setCurrency(STRIPE_CURRENCY)
                    .setUnitAmount(unitAmount)
                    .setProductData(productData)
                    .build();
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(stripe.getSuccessUrl())
                    .setCancelUrl(stripe.getCancelUrl())
                    .setClientReferenceId(command.reference())
                    .setExpiresAt(command.expiresAt().getEpochSecond())
                    .putMetadata("paymentReference", command.reference())
                    .addLineItem(SessionCreateParams.LineItem.builder().setQuantity(1L).setPriceData(priceData).build())
                    .build();
            Session session = Session.create(params, RequestOptions.builder().setApiKey(stripe.getSecretKey()).build());
            return new CheckoutResult(session.getId(), session.getUrl());
        } catch (StripeException | ArithmeticException e) {
            log.error("Stripe checkout failed ({})", e.getClass().getSimpleName());
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
    }
}
