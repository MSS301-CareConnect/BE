package mss301.carerecipientservice.client;

import lombok.extern.slf4j.Slf4j;
import mss301.carerecipientservice.client.dto.IdentityApiResponse;
import mss301.carerecipientservice.client.dto.IdentityUserBatchRequest;
import mss301.carerecipientservice.client.dto.IdentityUserResponse;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Fallback khi IdentityService lỗi / timeout / circuit đang mở.
 * - list (batch, chỉ để hiển thị profile người thân): trả danh sách rỗng → API vẫn chạy, chỉ thiếu profile.
 * - get / getByEmail (dùng để XÁC MINH trước khi ghi): không đoán — ném lại lỗi nghiệp vụ (404) hoặc 503.
 */
@Component
@Slf4j
public class IdentityClientFallbackFactory implements FallbackFactory<IdentityClient> {

    @Override
    public IdentityClient create(Throwable cause) {
        return new IdentityClient() {
            @Override
            public IdentityApiResponse<IdentityUserResponse> get(UUID userId) {
                throw toAppException(cause);
            }

            @Override
            public IdentityApiResponse<IdentityUserResponse> getByEmail(String email) {
                throw toAppException(cause);
            }

            @Override
            public IdentityApiResponse<List<IdentityUserResponse>> list(IdentityUserBatchRequest request) {
                log.warn("IdentityService unavailable, returning managers without profile ({})",
                        cause.getClass().getSimpleName());
                IdentityApiResponse<List<IdentityUserResponse>> emptyResponse = new IdentityApiResponse<>();
                emptyResponse.setResult(List.of());
                return emptyResponse;
            }
        };
    }

    private static AppException toAppException(Throwable cause) {
        if (cause instanceof AppException appException) {
            return appException;
        }
        // Lỗi kết nối / timeout / circuit mở — log loại lỗi, không log URL hay dữ liệu người dùng.
        log.warn("IdentityService call failed ({})", cause.getClass().getSimpleName());
        return new AppException(ErrorCode.IDENTITY_UNAVAILABLE);
    }
}
