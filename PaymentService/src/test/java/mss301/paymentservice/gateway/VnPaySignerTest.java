package mss301.paymentservice.gateway;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
class VnPaySignerTest {
    @Test void signsAndVerifiesWithoutDependingOnInputOrder(){
        Map<String,String> params=new HashMap<>(); params.put("vnp_TxnRef","ABC 123"); params.put("vnp_Amount","100000");
        String query=VnPaySigner.signedQuery(params,"secret");
        Map<String,String> callback=new HashMap<>(params); callback.put("vnp_SecureHash",query.substring(query.indexOf("vnp_SecureHash=")+15));
        assertThat(VnPaySigner.verify(callback,"secret")).isTrue();
        callback.put("vnp_Amount","200000"); assertThat(VnPaySigner.verify(callback,"secret")).isFalse();
    }
}
