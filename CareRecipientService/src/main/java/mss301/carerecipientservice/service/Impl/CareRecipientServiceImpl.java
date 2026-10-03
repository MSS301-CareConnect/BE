package mss301.carerecipientservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carerecipientservice.dto.request.CareRecipientCreateRequest;
import mss301.carerecipientservice.dto.request.CareRecipientUpdateRequest;
import mss301.carerecipientservice.dto.request.EmergencyContactRequest;
import mss301.carerecipientservice.dto.request.MedicationRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.CareRecipientDetailResponse;
import mss301.carerecipientservice.dto.response.CareRecipientResponse;
import mss301.carerecipientservice.dto.response.CareRecipientSummaryResponse;
import mss301.carerecipientservice.dto.response.PageResponse;
import mss301.carerecipientservice.entity.CareRecipient;
import mss301.carerecipientservice.entity.EmergencyContact;
import mss301.carerecipientservice.entity.Medication;
import mss301.carerecipientservice.entity.RecipientManager;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RecipientStatus;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import mss301.carerecipientservice.mapper.CareRecipientMapper;
import mss301.carerecipientservice.mapper.EmergencyContactMapper;
import mss301.carerecipientservice.mapper.MedicationMapper;
import mss301.carerecipientservice.mapper.RecipientManagerMapper;
import mss301.carerecipientservice.repository.CareRecipientRepository;
import mss301.carerecipientservice.repository.CareRecipientSpecifications;
import mss301.carerecipientservice.repository.EmergencyContactRepository;
import mss301.carerecipientservice.repository.MedicationRepository;
import mss301.carerecipientservice.repository.RecipientManagerRepository;
import mss301.carerecipientservice.service.CareRecipientService;
import mss301.carerecipientservice.service.RecipientAccessService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CareRecipientServiceImpl implements CareRecipientService {

    static int MAX_PAGE_SIZE = 100;

    CareRecipientRepository careRecipientRepository;
    RecipientManagerRepository recipientManagerRepository;
    MedicationRepository medicationRepository;
    EmergencyContactRepository emergencyContactRepository;
    RecipientAccessService recipientAccessService;
    CareRecipientMapper careRecipientMapper;
    RecipientManagerMapper recipientManagerMapper;
    MedicationMapper medicationMapper;
    EmergencyContactMapper emergencyContactMapper;

    @Override
    @Transactional
    public APIResponse<CareRecipientDetailResponse> create(UUID actorId, CareRecipientCreateRequest request) {
        checkCoordinates(request.getLatitude(), request.getLongitude());
        checkSinglePrimaryContact(request.getEmergencyContacts());
        checkMedicationDates(request.getMedications());
        CareRecipient recipient = careRecipientMapper.toCareRecipient(request, actorId);
        recipient.setStatus(RecipientStatus.ACTIVE);
        CareRecipient savedRecipient = careRecipientRepository.save(recipient);

        // Người tạo luôn là OWNER + quản lý chính.
        RecipientManager owner = recipientManagerMapper.toRecipientManager(savedRecipient, actorId,
                request.getRelationshipType(), RecipientAccessLevel.OWNER, true, actorId);
        recipientManagerRepository.save(owner);

        List<EmergencyContact> emergencyContacts = emergencyContactRepository.saveAll(
                emergencyContactMapper.toEmergencyContactList(request.getEmergencyContacts(), savedRecipient));
        List<Medication> medications = medicationRepository.saveAll(
                medicationMapper.toMedicationList(request.getMedications(), savedRecipient, actorId));
        // Flush để createdAt/version của mọi bản ghi có trong response.
        careRecipientRepository.flush();
        log.info("Care recipient {} created by user {}", savedRecipient.getId(), actorId);
        CareRecipientDetailResponse detailResponse = careRecipientMapper.toDetailResponse(savedRecipient,
                RecipientAccessLevel.OWNER, medications, emergencyContacts);
        return APIResponse.success("Tạo hồ sơ người bệnh thành công", detailResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CareRecipientSummaryResponse>> list(UUID actorId, RecipientStatus status,
                                                                        String keyword, int page, int size) {
        PageRequest pageRequest = toPageRequest(page, size, "recipient.createdAt");
        Page<RecipientManager> memberships = recipientManagerRepository.findAll(
                CareRecipientSpecifications.managedBy(actorId, status, keyword), pageRequest);
        List<CareRecipientSummaryResponse> summaryResponses = memberships.getContent().stream()
                .map(membership -> careRecipientMapper.toSummaryResponse(membership.getRecipient(), membership))
                .toList();
        PageResponse<CareRecipientSummaryResponse> pageResponse = PageResponse.of(memberships, summaryResponses);
        return APIResponse.success("Lấy danh sách hồ sơ thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CareRecipientDetailResponse> get(UUID actorId, UUID id) {
        RecipientManager membership = recipientAccessService.checkAccess(id, actorId, RecipientAccessLevel.VIEWER);
        CareRecipientDetailResponse detailResponse = toDetailResponse(membership.getRecipient(), membership.getAccessLevel());
        return APIResponse.success("Lấy hồ sơ người bệnh thành công", detailResponse);
    }

    @Override
    @Transactional
    public APIResponse<CareRecipientResponse> update(UUID actorId, UUID id, CareRecipientUpdateRequest request) {
        recipientAccessService.checkAccess(id, actorId, RecipientAccessLevel.EDITOR);
        CareRecipient recipient = lockRecipient(id);
        // Client sửa trên bản cũ → không ghi đè thay đổi của người khác.
        if (recipient.getVersion() != request.getVersion()) {
            throw new AppException(ErrorCode.RECIPIENT_VERSION_CONFLICT);
        }
        checkCoordinates(request.getLatitude(), request.getLongitude());
        careRecipientMapper.update(request, actorId, recipient);
        // Flush để response có version mới.
        careRecipientRepository.flush();
        log.info("Care recipient {} updated by user {}", id, actorId);
        CareRecipientResponse careRecipientResponse = careRecipientMapper.toResponse(recipient);
        return APIResponse.success("Cập nhật hồ sơ người bệnh thành công", careRecipientResponse);
    }

    @Override
    @Transactional
    public APIResponse<CareRecipientResponse> updateStatus(UUID actorId, UUID id, RecipientStatus status) {
        // ARCHIVED chỉ qua DELETE (kèm deleted_at).
        if (status != RecipientStatus.ACTIVE && status != RecipientStatus.INACTIVE) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }
        recipientAccessService.checkAccess(id, actorId, RecipientAccessLevel.OWNER);
        CareRecipient recipient = lockRecipient(id);
        recipient.setStatus(status);
        recipient.setUpdatedBy(actorId);
        careRecipientRepository.flush();
        log.info("Care recipient {} status changed to {} by user {}", id, status, actorId);
        CareRecipientResponse careRecipientResponse = careRecipientMapper.toResponse(recipient);
        return APIResponse.success("Cập nhật trạng thái hồ sơ thành công", careRecipientResponse);
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID actorId, UUID id) {
        recipientAccessService.checkAccess(id, actorId, RecipientAccessLevel.OWNER);
        CareRecipient recipient = lockRecipient(id);
        // Lưu trữ thay vì xóa vật lý: booking/khiếu nại cũ vẫn tham chiếu được, admin vẫn xem được.
        recipient.setStatus(RecipientStatus.ARCHIVED);
        recipient.setDeletedAt(Instant.now());
        recipient.setUpdatedBy(actorId);
        log.info("Care recipient {} archived by user {}", id, actorId);
        return APIResponse.success("Đã lưu trữ hồ sơ người bệnh");
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CareRecipientSummaryResponse>> listForAdmin(RecipientStatus status, String keyword,
                                                                                UUID createdBy, boolean includeDeleted,
                                                                                int page, int size) {
        PageRequest pageRequest = toPageRequest(page, size, "createdAt");
        Page<CareRecipient> recipients = careRecipientRepository.findAll(
                CareRecipientSpecifications.forAdmin(status, keyword, createdBy, includeDeleted), pageRequest);
        List<CareRecipientSummaryResponse> summaryResponses = careRecipientMapper.toSummaryResponseList(recipients.getContent());
        PageResponse<CareRecipientSummaryResponse> pageResponse = PageResponse.of(recipients, summaryResponses);
        return APIResponse.success("Lấy danh sách hồ sơ thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CareRecipientDetailResponse> getForAdmin(UUID id) {
        // Admin xem cả hồ sơ đã lưu trữ (xử lý khiếu nại).
        CareRecipient recipient = careRecipientRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_NOT_FOUND));
        CareRecipientDetailResponse detailResponse = toDetailResponse(recipient, null);
        return APIResponse.success("Lấy hồ sơ người bệnh thành công", detailResponse);
    }

    private CareRecipientDetailResponse toDetailResponse(CareRecipient recipient, RecipientAccessLevel myAccessLevel) {
        List<Medication> medications = medicationRepository.findAllByRecipient(recipient.getId());
        List<EmergencyContact> emergencyContacts = emergencyContactRepository.findAllByRecipient(recipient.getId());
        return careRecipientMapper.toDetailResponse(recipient, myAccessLevel, medications, emergencyContacts);
    }

    private CareRecipient lockRecipient(UUID id) {
        return careRecipientRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_NOT_FOUND));
    }

    private PageRequest toPageRequest(int page, int size, String sortProperty) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortProperty));
    }

    // Tọa độ đi theo cặp (DB cũng có CHECK care_recipients_coordinates_pair).
    private void checkCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new AppException(ErrorCode.INVALID_COORDINATES);
        }
    }

    private void checkSinglePrimaryContact(List<EmergencyContactRequest> emergencyContacts) {
        if (emergencyContacts == null) {
            return;
        }
        long primaryCount = emergencyContacts.stream()
                .filter(contact -> Boolean.TRUE.equals(contact.getPrimary()))
                .count();
        if (primaryCount > 1) {
            throw new AppException(ErrorCode.DUPLICATE_PRIMARY_CONTACT);
        }
    }

    private void checkMedicationDates(List<MedicationRequest> medications) {
        if (medications == null) {
            return;
        }
        for (MedicationRequest medication : medications) {
            if (medication.getEndDate() != null && medication.getEndDate().isBefore(medication.getStartDate())) {
                throw new AppException(ErrorCode.INVALID_MEDICATION_DATES);
            }
        }
    }
}
