package mss301.paymentservice.gateway;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.config.PaymentProperties;
import mss301.paymentservice.enums.PaymentProvider;
import mss301.paymentservice.util.VndMoney;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

// Tạo URL thanh toán VNPay 2.1.0 (vnp_Command=pay) và kiểm chữ ký callback (IPN / return).
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VnPayGateway implements PaymentGateway {
    public static final ZoneId VNPAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    public static final String VERSION = "2.1.0";
    static String CURRENCY = "VND";

    PaymentProperties paymentProperties;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.VNPAY;
    }

    @Override
    public boolean isEnabled() {
        PaymentProperties.Vnpay vnpay = paymentProperties.getVnpay();
        return vnpay.isEnabled() && isPresent(vnpay.getTmnCode()) && isPresent(vnpay.getHashSecret());
    }

    @Override
    public CheckoutResult createCheckout(CheckoutCommand command) {
        PaymentProperties.Vnpay vnpay = paymentProperties.getVnpay();
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", VERSION);
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", vnpay.getTmnCode());
        params.put("vnp_Amount", VndMoney.toVnPayAmount(command.amount()));
        params.put("vnp_CurrCode", CURRENCY);
        params.put("vnp_TxnRef", command.reference());
        params.put("vnp_OrderInfo", "Thanh toan " + command.orderLabel());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", vnpay.getReturnUrl());
        params.put("vnp_IpAddr", command.clientIp());
        params.put("vnp_CreateDate", VNPAY_DATE_FORMAT.format(command.createdAt().atZone(VNPAY_ZONE)));
        params.put("vnp_ExpireDate", VNPAY_DATE_FORMAT.format(command.expiresAt().atZone(VNPAY_ZONE)));
        String checkoutUrl = vnpay.getPayUrl() + "?" + VnPaySigner.signedQuery(params, vnpay.getHashSecret());
        return new CheckoutResult(command.reference(), checkoutUrl);
    }

    public boolean isValidSignature(Map<String, String> params) {
        return isEnabled() && VnPaySigner.verify(params, paymentProperties.getVnpay().getHashSecret());
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
