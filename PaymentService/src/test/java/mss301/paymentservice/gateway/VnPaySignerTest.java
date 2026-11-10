package mss301.paymentservice.gateway;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VnPaySignerTest {

    @Test
    void signsAndVerifiesWithoutDependingOnInputOrder() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "ABC 123");
        params.put("vnp_Amount", "100000");
        String query = VnPaySigner.signedQuery(params, "secret");
        Map<String, String> callback = new HashMap<>(params);
        callback.put("vnp_SecureHash", query.substring(query.indexOf("vnp_SecureHash=") + "vnp_SecureHash=".length()));

        assertThat(VnPaySigner.verify(callback, "secret")).isTrue();

        callback.put("vnp_Amount", "200000");
        assertThat(VnPaySigner.verify(callback, "secret")).isFalse();
    }

    // Mẫu chính thức VNPay 2.1.0: URLEncoder US_ASCII — khoảng trắng là '+', không phải '%20'.
    @Test
    void encodesSpacesLikeOfficialSample() {
        assertThat(VnPaySigner.canonical(Map.of("vnp_OrderInfo", "Thanh toan booking A", "vnp_Amount", "100")))
                .isEqualTo("vnp_Amount=100&vnp_OrderInfo=Thanh+toan+booking+A");
    }

    @Test
    void missingHashIsRejected() {
        assertThat(VnPaySigner.verify(Map.of("vnp_TxnRef", "A"), "secret")).isFalse();
    }
}
