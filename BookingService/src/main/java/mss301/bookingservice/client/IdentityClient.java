package mss301.bookingservice.client;

import mss301.bookingservice.client.dto.IdentityUserBatchRequest;
import mss301.bookingservice.client.dto.IdentityUserResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "identity", url = "${booking.identity.url}",
        configuration = IdentityClientConfig.class, fallbackFactory = IdentityClientFallbackFactory.class)
public interface IdentityClient {

    // Gọi IdentityService lấy thông tin nhiều người dùng.
    @PostMapping("/api/v1/users/batch")
    RemoteApiResponse<List<IdentityUserResponse>> list(@RequestBody IdentityUserBatchRequest request);
}
