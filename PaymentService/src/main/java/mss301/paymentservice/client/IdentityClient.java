package mss301.paymentservice.client;

import mss301.paymentservice.client.dto.IdentityUserBatchRequest;
import mss301.paymentservice.client.dto.IdentityUserResponse;
import mss301.paymentservice.client.dto.RemoteApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

// Tên / SĐT người yêu cầu rút tiền cho trung tâm / admin (2026-11-11) — token SERVICE vì manager không đọc được
// tài khoản khách (khách không thuộc trung tâm nào).
@FeignClient(name = "payment-identity-client", url = "${payment.identity-url}",
        configuration = IdentityClientConfig.class)
public interface IdentityClient {

    // Gọi IdentityService lấy thông tin nhiều người dùng.
    @PostMapping("/api/v1/users/batch")
    RemoteApiResponse<List<IdentityUserResponse>> list(@RequestBody IdentityUserBatchRequest request);
}
