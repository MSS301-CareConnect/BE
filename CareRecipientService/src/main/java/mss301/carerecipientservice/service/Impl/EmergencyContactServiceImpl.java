package mss301.carerecipientservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carerecipientservice.dto.request.EmergencyContactRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.EmergencyContactResponse;
import mss301.carerecipientservice.entity.CareRecipient;
import mss301.carerecipientservice.entity.EmergencyContact;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import mss301.carerecipientservice.mapper.EmergencyContactMapper;
import mss301.carerecipientservice.repository.CareRecipientRepository;
import mss301.carerecipientservice.repository.EmergencyContactRepository;
import mss301.carerecipientservice.service.EmergencyContactService;
import mss301.carerecipientservice.service.RecipientAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Xem: VIEWER trở lên; thêm/sửa/xóa/đặt liên hệ chính: EDITOR trở lên.
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class EmergencyContactServiceImpl implements EmergencyContactService {

    EmergencyContactRepository emergencyContactRepository;
    CareRecipientRepository careRecipientRepository;
    RecipientAccessService recipientAccessService;
    EmergencyContactMapper emergencyContactMapper;

    @Override
    @Transactional
    public APIResponse<EmergencyContactResponse> create(UUID actorId, UUID recipientId, EmergencyContactRequest request) {
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.EDITOR);
        CareRecipient recipient = lockRecipient(recipientId);
        if (Boolean.TRUE.equals(request.getPrimary())) {
            clearPrimary(recipientId, null);
        }
        EmergencyContact emergencyContact = emergencyContactMapper.toEmergencyContact(request, recipient);
        EmergencyContact savedContact = emergencyContactRepository.saveAndFlush(emergencyContact);
        log.info("Emergency contact {} added to care recipient {} by user {}", savedContact.getId(), recipientId, actorId);
        EmergencyContactResponse emergencyContactResponse = emergencyContactMapper.toResponse(savedContact);
        return APIResponse.success("Thêm liên hệ khẩn cấp thành công", emergencyContactResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<EmergencyContactResponse>> list(UUID actorId, UUID recipientId) {
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.VIEWER);
        List<EmergencyContact> emergencyContacts = emergencyContactRepository.findAllByRecipient(recipientId);
        List<EmergencyContactResponse> emergencyContactResponses = emergencyContactMapper.toResponseList(emergencyContacts);
        return APIResponse.success("Lấy danh sách liên hệ khẩn cấp thành công", emergencyContactResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<EmergencyContactResponse> get(UUID actorId, UUID recipientId, UUID id) {
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.VIEWER);
        EmergencyContact emergencyContact = findContact(recipientId, id);
        EmergencyContactResponse emergencyContactResponse = emergencyContactMapper.toResponse(emergencyContact);
        return APIResponse.success("Lấy liên hệ khẩn cấp thành công", emergencyContactResponse);
    }

    @Override
    @Transactional
    public APIResponse<EmergencyContactResponse> update(UUID actorId, UUID recipientId, UUID id,
                                                        EmergencyContactRequest request) {
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.EDITOR);
        lockRecipient(recipientId);
        EmergencyContact emergencyContact = findContact(recipientId, id);
        // primary null = giữ nguyên; true = thành liên hệ chính; false = bỏ cờ chính.
        if (Boolean.TRUE.equals(request.getPrimary())) {
            clearPrimary(recipientId, id);
            emergencyContact.setPrimary(true);
        } else if (Boolean.FALSE.equals(request.getPrimary())) {
            emergencyContact.setPrimary(false);
        }
        emergencyContactMapper.update(request, emergencyContact);
        emergencyContactRepository.flush();
        EmergencyContactResponse emergencyContactResponse = emergencyContactMapper.toResponse(emergencyContact);
        return APIResponse.success("Cập nhật liên hệ khẩn cấp thành công", emergencyContactResponse);
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID actorId, UUID recipientId, UUID id) {
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.EDITOR);
        lockRecipient(recipientId);
        EmergencyContact emergencyContact = findContact(recipientId, id);
        // Xóa mềm liên hệ chính → hồ sơ tạm không có liên hệ chính cho tới khi người quản lý chọn lại.
        emergencyContact.setPrimary(false);
        emergencyContact.setDeletedAt(Instant.now());
        log.info("Emergency contact {} of care recipient {} deleted by user {}", id, recipientId, actorId);
        return APIResponse.success("Xóa liên hệ khẩn cấp thành công");
    }

    @Override
    @Transactional
    public APIResponse<EmergencyContactResponse> assignPrimary(UUID actorId, UUID recipientId, UUID id) {
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.EDITOR);
        lockRecipient(recipientId);
        EmergencyContact emergencyContact = findContact(recipientId, id);
        clearPrimary(recipientId, id);
        emergencyContact.setPrimary(true);
        emergencyContactRepository.flush();
        EmergencyContactResponse emergencyContactResponse = emergencyContactMapper.toResponse(emergencyContact);
        return APIResponse.success("Đã đặt liên hệ khẩn cấp chính", emergencyContactResponse);
    }

    // Bỏ cờ của liên hệ chính hiện tại (khác keepId) và flush TRƯỚC khi đặt cờ mới — partial unique index
    // uk_emergency_contacts_primary không cho hai dòng primary cùng lúc.
    private void clearPrimary(UUID recipientId, UUID keepId) {
        emergencyContactRepository.findPrimary(recipientId)
                .filter(current -> !current.getId().equals(keepId))
                .ifPresent(current -> {
                    current.setPrimary(false);
                    emergencyContactRepository.flush();
                });
    }

    private EmergencyContact findContact(UUID recipientId, UUID id) {
        return emergencyContactRepository.findByIdAndRecipient(id, recipientId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTACT_NOT_FOUND));
    }

    // Khóa hồ sơ để hai request đặt liên hệ chính song song chạy tuần tự.
    private CareRecipient lockRecipient(UUID recipientId) {
        return careRecipientRepository.findForUpdate(recipientId)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_NOT_FOUND));
    }
}
