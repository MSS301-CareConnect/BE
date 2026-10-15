package mss301.carerecipientservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.RecipientSnapshotResponse;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import mss301.carerecipientservice.service.CaregiverAssignmentService;
import mss301.carerecipientservice.service.CaregiverRecipientService;
import mss301.carerecipientservice.service.InternalCareRecipientService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Không @Transactional: hỏi BookingService (mạng) trước, rồi mới đọc snapshot (transaction đọc ngắn của
 * InternalCareRecipientService — cùng dữ liệu Booking lưu lúc đặt lịch: thông tin cần để chăm sóc, không có người thân).
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CaregiverRecipientServiceImpl implements CaregiverRecipientService {

    CaregiverAssignmentService caregiverAssignmentService;
    InternalCareRecipientService internalCareRecipientService;

    @Override
    public APIResponse<RecipientSnapshotResponse> getSnapshot(UUID actorId, UUID recipientId) {
        // Không phân biệt "không tồn tại" và "không được phân công" → caregiver không dò được hồ sơ nào tồn tại.
        if (!caregiverAssignmentService.isAssigned(recipientId, actorId)) {
            throw new AppException(ErrorCode.RECIPIENT_NOT_FOUND);
        }
        return internalCareRecipientService.getSnapshot(recipientId);
    }
}
