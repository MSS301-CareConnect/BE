package mss301.carerecipientservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carerecipientservice.dto.request.MedicationRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.MedicationResponse;
import mss301.carerecipientservice.entity.Medication;
import mss301.carerecipientservice.entity.RecipientManager;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import mss301.carerecipientservice.mapper.MedicationMapper;
import mss301.carerecipientservice.repository.MedicationRepository;
import mss301.carerecipientservice.service.MedicationService;
import mss301.carerecipientservice.service.RecipientAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Xem: VIEWER trở lên; thêm/sửa/xóa/bật-tắt: EDITOR trở lên.
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class MedicationServiceImpl implements MedicationService {

    MedicationRepository medicationRepository;
    RecipientAccessService recipientAccessService;
    MedicationMapper medicationMapper;

    @Override
    @Transactional
    public APIResponse<MedicationResponse> create(UUID actorId, UUID recipientId, MedicationRequest request) {
        RecipientManager membership = recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.EDITOR);
        checkDates(request);
        Medication medication = medicationMapper.toMedication(request, membership.getRecipient(), actorId);
        Medication savedMedication = medicationRepository.saveAndFlush(medication);
        log.info("Medication {} added to care recipient {} by user {}", savedMedication.getId(), recipientId, actorId);
        MedicationResponse medicationResponse = medicationMapper.toResponse(savedMedication);
        return APIResponse.success("Thêm thuốc thành công", medicationResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<MedicationResponse>> list(UUID actorId, UUID recipientId, Boolean active) {
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.VIEWER);
        List<Medication> medications = active == null
                ? medicationRepository.findAllByRecipient(recipientId)
                : medicationRepository.findAllByRecipientAndActive(recipientId, active);
        List<MedicationResponse> medicationResponses = medicationMapper.toResponseList(medications);
        return APIResponse.success("Lấy danh sách thuốc thành công", medicationResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<MedicationResponse> get(UUID actorId, UUID recipientId, UUID id) {
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.VIEWER);
        Medication medication = findMedication(recipientId, id);
        MedicationResponse medicationResponse = medicationMapper.toResponse(medication);
        return APIResponse.success("Lấy thông tin thuốc thành công", medicationResponse);
    }

    @Override
    @Transactional
    public APIResponse<MedicationResponse> update(UUID actorId, UUID recipientId, UUID id, MedicationRequest request) {
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.EDITOR);
        checkDates(request);
        Medication medication = findMedication(recipientId, id);
        medicationMapper.update(request, actorId, medication);
        medicationRepository.flush();
        MedicationResponse medicationResponse = medicationMapper.toResponse(medication);
        return APIResponse.success("Cập nhật thuốc thành công", medicationResponse);
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID actorId, UUID recipientId, UUID id) {
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.EDITOR);
        Medication medication = findMedication(recipientId, id);
        medication.setDeletedAt(Instant.now());
        medication.setUpdatedBy(actorId);
        log.info("Medication {} of care recipient {} deleted by user {}", id, recipientId, actorId);
        return APIResponse.success("Xóa thuốc thành công");
    }

    @Override
    @Transactional
    public APIResponse<MedicationResponse> updateActive(UUID actorId, UUID recipientId, UUID id, boolean active) {
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.EDITOR);
        Medication medication = findMedication(recipientId, id);
        medication.setActive(active);
        medication.setUpdatedBy(actorId);
        medicationRepository.flush();
        MedicationResponse medicationResponse = medicationMapper.toResponse(medication);
        return APIResponse.success("Cập nhật trạng thái thuốc thành công", medicationResponse);
    }

    private Medication findMedication(UUID recipientId, UUID id) {
        return medicationRepository.findByIdAndRecipient(id, recipientId)
                .orElseThrow(() -> new AppException(ErrorCode.MEDICATION_NOT_FOUND));
    }

    private void checkDates(MedicationRequest request) {
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new AppException(ErrorCode.INVALID_MEDICATION_DATES);
        }
    }
}
