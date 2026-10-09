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

/**
 * Fallback khi CareRecipientService lỗi / timeout / circuit mở: cả hai lời gọi dùng để TẠO booking → không đoán,
 * ném lại lỗi nghiệp vụ (404) hoặc 503 — không bao giờ tạo booking thiếu kiểm quyền / thiếu snapshot.
 */
@Component
@Slf4j
public class CareRecipientClientFallbackFactory implements FallbackFactory<CareRecipientClient> {

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

    private static AppException toAppException(Throwable cause) {
        if (cause instanceof AppException appException) {
            return appException;
        }
        // Lỗi kết nối / timeout / circuit mở — chỉ log loại lỗi, không log dữ liệu người bệnh.
        log.warn("CareRecipientService call failed ({})", cause.getClass().getSimpleName());
        return new AppException(ErrorCode.CARE_RECIPIENT_UNAVAILABLE);
    }
}
