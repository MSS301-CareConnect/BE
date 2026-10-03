package mss301.carerecipientservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.dto.request.RecipientBatchRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.AccessCheckResponse;
import mss301.carerecipientservice.dto.response.CareRecipientSummaryResponse;
import mss301.carerecipientservice.dto.response.RecipientSnapshotResponse;
import mss301.carerecipientservice.entity.CareRecipient;
import mss301.carerecipientservice.entity.EmergencyContact;
import mss301.carerecipientservice.entity.Medication;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import mss301.carerecipientservice.mapper.CareRecipientMapper;
import mss301.carerecipientservice.mapper.RecipientManagerMapper;
import mss301.carerecipientservice.repository.CareRecipientRepository;
import mss301.carerecipientservice.repository.EmergencyContactRepository;
import mss301.carerecipientservice.repository.MedicationRepository;
import mss301.carerecipientservice.service.InternalCareRecipientService;
import mss301.carerecipientservice.service.RecipientAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalCareRecipientServiceImpl implements InternalCareRecipientService {

    CareRecipientRepository careRecipientRepository;
    MedicationRepository medicationRepository;
    EmergencyContactRepository emergencyContactRepository;
    RecipientAccessService recipientAccessService;
    CareRecipientMapper careRecipientMapper;
    RecipientManagerMapper recipientManagerMapper;

    // Hồ sơ đã lưu trữ → 404 (không đặt lịch mới); status INACTIVE vẫn trả về để Booking tự quyết.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<RecipientSnapshotResponse> getSnapshot(UUID id) {
        CareRecipient recipient = careRecipientRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_NOT_FOUND));
        List<Medication> activeMedications = medicationRepository.findAllByRecipientAndActive(id, true);
        EmergencyContact primaryContact = emergencyContactRepository.findPrimary(id).orElse(null);
        RecipientSnapshotResponse snapshotResponse = careRecipientMapper.toSnapshotResponse(recipient, activeMedications,
                primaryContact, Instant.now());
        return APIResponse.success("Lấy snapshot hồ sơ thành công", snapshotResponse);
    }

    // Luôn 200: allowed=false khi user không quản lý hồ sơ / hồ sơ không tồn tại / thiếu cấp.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<AccessCheckResponse> checkAccess(UUID id, UUID userId, RecipientAccessLevel minLevel) {
        Optional<RecipientAccessLevel> accessLevel = recipientAccessService.findAccessLevel(id, userId);
        boolean allowed = accessLevel.map(level -> level.covers(minLevel)).orElse(false);
        AccessCheckResponse accessCheckResponse = recipientManagerMapper.toAccessCheckResponse(id, userId, allowed,
                accessLevel.orElse(null));
        return APIResponse.success("Kiểm tra quyền thành công", accessCheckResponse);
    }

    // Id không tồn tại / đã lưu trữ bị bỏ qua.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<CareRecipientSummaryResponse>> list(RecipientBatchRequest request) {
        List<CareRecipient> recipients = careRecipientRepository.findAllByIdInAndDeletedAtIsNull(
                new LinkedHashSet<>(request.getRecipientIds()));
        List<CareRecipientSummaryResponse> summaryResponses = careRecipientMapper.toSummaryResponseList(recipients);
        return APIResponse.success("Lấy danh sách hồ sơ thành công", summaryResponses);
    }
}
