package mss301.bookingservice.client;

import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.dto.RecipientAccessResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.dto.response.RecipientSnapshot;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class CareRecipientClientFallbackFactory implements FallbackFactory<CareRecipientClient> {

    // Fallback khi gọi CareRecipientService lỗi: ném AppException tương ứng.
    @Override
    public CareRecipientClient create(Throwable cause) {
        return new CareRecipientClient() {
            @Override
            public RemoteApiResponse<RecipientAccessResponse> checkAccess(UUID recipientId, UUID userId, String minLevel) {
                throw toAppException(cause);
            }

            @Override
            public RemoteApiResponse<RecipientSnapshot> getSnapshot(UUID recipientId) {
                throw toAppException(cause);
            }
        };
    }

    // Giữ nguyên AppException, lỗi khác đổi thành dịch vụ không khả dụng.
    private static AppException toAppException(Throwable cause) {
        if (cause instanceof AppException appException) {
            return appException;
        }
        // Lỗi kết nối / timeout / circuit mở — chỉ log loại lỗi, không log dữ liệu người bệnh.
        log.warn("CareRecipientService call failed ({})", cause.getClass().getSimpleName());
        return new AppException(ErrorCode.CARE_RECIPIENT_UNAVAILABLE);
    }
}
