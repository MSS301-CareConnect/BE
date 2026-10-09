package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.client.CareRecipientClient;
import mss301.bookingservice.client.dto.RecipientAccessResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.dto.response.RecipientSnapshot;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.service.CareRecipientDirectoryService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CareRecipientDirectoryServiceImpl implements CareRecipientDirectoryService {

    // Quyền tối thiểu trên hồ sơ để đặt lịch (đã chốt: EDITOR; VIEWER chỉ xem).
    static String MIN_BOOKING_ACCESS_LEVEL = "EDITOR";

    CareRecipientClient careRecipientClient;

    @Override
    public RecipientSnapshot getBookableSnapshot(UUID recipientId, UUID customerId) {
        RemoteApiResponse<RecipientAccessResponse> accessResponse = careRecipientClient.checkAccess(recipientId,
                customerId, MIN_BOOKING_ACCESS_LEVEL);
        if (accessResponse == null || accessResponse.getResult() == null) {
            throw new AppException(ErrorCode.CARE_RECIPIENT_UNAVAILABLE);
        }
        if (!accessResponse.getResult().isAllowed()) {
            throw new AppException(ErrorCode.RECIPIENT_NOT_FOUND);
        }
        RemoteApiResponse<RecipientSnapshot> snapshotResponse = careRecipientClient.getSnapshot(recipientId);
        RecipientSnapshot snapshot = snapshotResponse == null ? null : snapshotResponse.getResult();
        if (snapshot == null || snapshot.getRecipientId() == null) {
            throw new AppException(ErrorCode.CARE_RECIPIENT_UNAVAILABLE);
        }
        if (!RecipientSnapshot.STATUS_ACTIVE.equals(snapshot.getStatus())) {
            throw new AppException(ErrorCode.RECIPIENT_NOT_ACTIVE);
        }
        return snapshot;
    }
}
