package mss301.paymentservice.gateway;

import lombok.RequiredArgsConstructor;
import mss301.paymentservice.config.PaymentProperties;
import mss301.paymentservice.enums.PaymentProvider;
import mss301.paymentservice.exception.*;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
public class VnPayGateway implements PaymentGateway {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final PaymentProperties properties;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.VNPAY;
    }

    @Override
    public CheckoutResult createCheckout(CheckoutCommand c) {
        var p = properties.getVnpay();
        if (!p.isEnabled() || blank(p.getTmnCode()) || blank(p.getHashSecret()))
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_DISABLED);
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", p.getTmnCode());
        params.put("vnp_Amount", c.amount().movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).toPlainString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", c.reference());
        params.put("vnp_OrderInfo", "Thanh toan booking " + c.bookingCode());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", p.getReturnUrl());
        params.put("vnp_IpAddr", c.clientIp());
        params.put("vnp_CreateDate", FORMAT.format(ZonedDateTime.now(zone)));
        params.put("vnp_ExpireDate", FORMAT.format(c.expiresAt().atZone(zone)));
        return new CheckoutResult(c.reference(), p.getPayUrl() + "?" + VnPaySigner.signedQuery(params, p.getHashSecret()));
    }

    public boolean validSignature(Map<String, String> params) {
        return properties.getVnpay().isEnabled() && !blank(properties.getVnpay().getHashSecret())
                && VnPaySigner.verify(params, properties.getVnpay().getHashSecret());
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
