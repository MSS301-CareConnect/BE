package mss301.paymentservice.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Chữ ký HMAC-SHA512 của VNPay (bản 2.1.0). Thanh toán / IPN: ký chuỗi query đã sắp theo tên tham số; API merchant (refund):
 * ký chuỗi các giá trị nối bằng {@code |} theo thứ tự đặc tả ({@link #hmacSha512}).
 */
public final class VnPaySigner {
    static final String SECURE_HASH = "vnp_SecureHash";
    static final String SECURE_HASH_TYPE = "vnp_SecureHashType";
    static final String HMAC_ALGORITHM = "HmacSHA512";

    private VnPaySigner() {
    }

    public static String signedQuery(Map<String, String> input, String secret) {
        String query = canonical(input);
        return query + "&" + SECURE_HASH + "=" + hmacSha512(secret, query);
    }

    public static boolean verify(Map<String, String> input, String secret) {
        String received = input.get(SECURE_HASH);
        if (received == null) {
            return false;
        }
        Map<String, String> unsigned = new HashMap<>(input);
        unsigned.remove(SECURE_HASH);
        unsigned.remove(SECURE_HASH_TYPE);
        byte[] expected = hmacSha512(secret, canonical(unsigned)).getBytes(StandardCharsets.UTF_8);
        byte[] actual = received.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    public static String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot sign VNPay data", e);
        }
    }

    static String canonical(Map<String, String> input) {
        return input.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    // Mẫu chính thức VNPay 2.1.0 (Java): URLEncoder US_ASCII cho cả chuỗi ký lẫn query — khoảng trắng thành '+'.
    // Thay '+' bằng '%20' làm lệch chữ ký khi giá trị có khoảng trắng (vd vnp_OrderInfo).
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }
}
