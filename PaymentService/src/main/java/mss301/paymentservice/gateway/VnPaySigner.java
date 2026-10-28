package mss301.paymentservice.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class VnPaySigner {
    private VnPaySigner() {
    }

    public static String signedQuery(Map<String, String> input, String secret) {
        String query = canonical(input);
        return query + "&vnp_SecureHash=" + hmacSha512(secret, query);
    }

    public static boolean verify(Map<String, String> input, String secret) {
        String received = input.get("vnp_SecureHash");
        if (received == null) return false;
        Map<String, String> copy = new HashMap<>(input);
        copy.remove("vnp_SecureHash");
        copy.remove("vnp_SecureHashType");
        return java.security.MessageDigest.isEqual(hmacSha512(secret, canonical(copy)).getBytes(StandardCharsets.UTF_8), received.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    static String canonical(Map<String, String> input) {
        return input.entrySet().stream().filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey()).map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .reduce((a, b) -> a + "&" + b).orElse("");
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.US_ASCII).replace("+", "%20");
    }

    private static String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign VNPay data", e);
        }
    }
}
