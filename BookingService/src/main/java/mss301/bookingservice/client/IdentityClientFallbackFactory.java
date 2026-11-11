package mss301.bookingservice.client;

import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.dto.IdentityUserBatchRequest;
import mss301.bookingservice.client.dto.IdentityUserResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class IdentityClientFallbackFactory implements FallbackFactory<IdentityClient> {

    // Fallback khi gọi IdentityService lỗi: ném AppException tương ứng.
    @Override
    public IdentityClient create(Throwable cause) {
        return new IdentityClient() {
            @Override
            public RemoteApiResponse<List<IdentityUserResponse>> list(IdentityUserBatchRequest request) {
                log.warn("IdentityService unavailable, returning bookings without customer profile ({})",
                        cause.getClass().getSimpleName());
                RemoteApiResponse<List<IdentityUserResponse>> emptyResponse = new RemoteApiResponse<>();
                emptyResponse.setResult(List.of());
                return emptyResponse;
            }
        };
    }
}
