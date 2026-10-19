package mss301.carerecipientservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carerecipientservice.client.BookingClient;
import mss301.carerecipientservice.client.dto.BookingApiResponse;
import mss301.carerecipientservice.client.dto.CaregiverAccessResponse;
import mss301.carerecipientservice.dto.request.CareRecipientCreateRequest;
import mss301.carerecipientservice.dto.request.CareRecipientUpdateRequest;
import mss301.carerecipientservice.dto.request.EmergencyContactRequest;
import mss301.carerecipientservice.dto.request.MedicationRequest;
import mss301.carerecipientservice.dto.request.RecipientBatchRequest;
import mss301.carerecipientservice.dto.request.ManagerCreateRequest;
import mss301.carerecipientservice.dto.request.ManagerUpdateRequest;
import mss301.carerecipientservice.dto.response.EmergencyContactResponse;
import mss301.carerecipientservice.dto.response.MedicationResponse;
import mss301.carerecipientservice.dto.response.RecipientManagerResponse;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.AccessCheckResponse;
import mss301.carerecipientservice.dto.response.CareRecipientDetailResponse;
import mss301.carerecipientservice.dto.response.CareRecipientResponse;
import mss301.carerecipientservice.dto.response.CareRecipientSummaryResponse;
import mss301.carerecipientservice.dto.response.PageResponse;
import mss301.carerecipientservice.dto.response.RecipientSnapshotResponse;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
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
    BookingClient bookingClient;
    CareRecipientMapper careRecipientMapper;
    RecipientManagerMapper recipientManagerMapper;
    MedicationMapper medicationMapper;
    EmergencyContactMapper emergencyContactMapper;
    MedicationServiceImpl medicationServiceImpl;
    EmergencyContactServiceImpl emergencyContactServiceImpl;
    RecipientManagerServiceImpl recipientManagerServiceImpl;

    @Override
    @Transactional
    public APIResponse<CareRecipientDetailResponse> create(UUID actorId, CareRecipientCreateRequest request) {
        checkCoordinates(request.getLatitude(), request.getLongitude());
        checkSinglePrimaryContact(request.getEmergencyContacts());
        checkMedicationDates(request.getMedications());
        CareRecipient recipient = careRecipientMapper.toCareRecipient(request, actorId);
        recipient.setStatus(RecipientStatus.ACTIVE);
        CareRecipient savedRecipient = careRecipientRepository.save(recipient);

        RecipientManager owner = recipientManagerMapper.toRecipientManager(savedRecipient, actorId,
                request.getRelationshipType(), RecipientAccessLevel.OWNER, true, actorId);
        recipientManagerRepository.save(owner);

        List<EmergencyContact> emergencyContacts = emergencyContactRepository.saveAll(
                emergencyContactMapper.toEmergencyContactList(request.getEmergencyContacts(), savedRecipient));
        List<Medication> medications = medicationRepository.saveAll(
                medicationMapper.toMedicationList(request.getMedications(), savedRecipient, actorId));
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
        if (recipient.getVersion() != request.getVersion()) {
            throw new AppException(ErrorCode.RECIPIENT_VERSION_CONFLICT);
        }
        checkCoordinates(request.getLatitude(), request.getLongitude());
        careRecipientMapper.update(request, actorId, recipient);
        careRecipientRepository.flush();
        log.info("Care recipient {} updated by user {}", id, actorId);
        CareRecipientResponse careRecipientResponse = careRecipientMapper.toResponse(recipient);
        return APIResponse.success("Cập nhật hồ sơ người bệnh thành công", careRecipientResponse);
    }

    @Override
    @Transactional
    public APIResponse<CareRecipientResponse> updateStatus(UUID actorId, UUID id, RecipientStatus status) {
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
        CareRecipient recipient = careRecipientRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_NOT_FOUND));
        CareRecipientDetailResponse detailResponse = toDetailResponse(recipient, null);
        return APIResponse.success("Lấy hồ sơ người bệnh thành công", detailResponse);
    }

    // ===== Merged Caregiver Logic =====

    @Override
    public APIResponse<RecipientSnapshotResponse> getSnapshotForCaregiver(UUID actorId, UUID id) {
        if (!isAssigned(id, actorId)) {
            throw new AppException(ErrorCode.RECIPIENT_NOT_FOUND);
        }
        return getSnapshot(id);
    }

    private boolean isAssigned(UUID recipientId, UUID caregiverUserId) {
        BookingApiResponse<CaregiverAccessResponse> response = bookingClient.checkCaregiverAccess(caregiverUserId, recipientId);
        if (response == null || response.getResult() == null) {
            throw new AppException(ErrorCode.BOOKING_UNAVAILABLE);
        }
        return response.getResult().isAllowed();
    }

    // ===== Merged Inter-Service Logic =====

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

    @Override
    @Transactional(readOnly = true)
    public APIResponse<AccessCheckResponse> checkAccess(UUID id, UUID userId, RecipientAccessLevel minLevel) {
        Optional<RecipientAccessLevel> accessLevel = recipientAccessService.findAccessLevel(id, userId);
        boolean allowed = accessLevel.map(level -> level.covers(minLevel)).orElse(false);
        AccessCheckResponse accessCheckResponse = recipientManagerMapper.toAccessCheckResponse(id, userId, allowed,
                accessLevel.orElse(null));
        return APIResponse.success("Kiểm tra quyền thành công", accessCheckResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<CareRecipientSummaryResponse>> listBatch(RecipientBatchRequest request) {
        List<CareRecipient> recipients = careRecipientRepository.findAllByIdInAndDeletedAtIsNull(
                new LinkedHashSet<>(request.getRecipientIds()));
        List<CareRecipientSummaryResponse> summaryResponses = careRecipientMapper.toSummaryResponseList(recipients);
        return APIResponse.success("Lấy danh sách hồ sơ thành công", summaryResponses);
    }

    // ===== Helpers =====

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

    // ===== Medication =====
    @Override
    public APIResponse<MedicationResponse> createMedication(UUID actorId, UUID recipientId, MedicationRequest request) {
        return medicationServiceImpl.create(actorId, recipientId, request);
    }

    @Override
    public APIResponse<List<MedicationResponse>> listMedications(UUID actorId, UUID recipientId, Boolean active) {
        return medicationServiceImpl.list(actorId, recipientId, active);
    }

    @Override
    public APIResponse<MedicationResponse> getMedication(UUID actorId, UUID recipientId, UUID medicationId) {
        return medicationServiceImpl.get(actorId, recipientId, medicationId);
    }

    @Override
    public APIResponse<MedicationResponse> updateMedication(UUID actorId, UUID recipientId, UUID medicationId, MedicationRequest request) {
        return medicationServiceImpl.update(actorId, recipientId, medicationId, request);
    }

    @Override
    public APIResponse<MedicationResponse> updateMedicationActive(UUID actorId, UUID recipientId, UUID medicationId, boolean active) {
        return medicationServiceImpl.updateActive(actorId, recipientId, medicationId, active);
    }

    @Override
    public APIResponse<Void> deleteMedication(UUID actorId, UUID recipientId, UUID medicationId) {
        return medicationServiceImpl.delete(actorId, recipientId, medicationId);
    }

    // ===== Emergency Contact =====
    @Override
    public APIResponse<EmergencyContactResponse> createEmergencyContact(UUID actorId, UUID recipientId, EmergencyContactRequest request) {
        return emergencyContactServiceImpl.create(actorId, recipientId, request);
    }

    @Override
    public APIResponse<List<EmergencyContactResponse>> listEmergencyContacts(UUID actorId, UUID recipientId) {
        return emergencyContactServiceImpl.list(actorId, recipientId);
    }

    @Override
    public APIResponse<EmergencyContactResponse> getEmergencyContact(UUID actorId, UUID recipientId, UUID contactId) {
        return emergencyContactServiceImpl.get(actorId, recipientId, contactId);
    }

    @Override
    public APIResponse<EmergencyContactResponse> updateEmergencyContact(UUID actorId, UUID recipientId, UUID contactId, EmergencyContactRequest request) {
        return emergencyContactServiceImpl.update(actorId, recipientId, contactId, request);
    }

    @Override
    public APIResponse<EmergencyContactResponse> assignPrimaryEmergencyContact(UUID actorId, UUID recipientId, UUID contactId) {
        return emergencyContactServiceImpl.assignPrimary(actorId, recipientId, contactId);
    }

    @Override
    public APIResponse<Void> deleteEmergencyContact(UUID actorId, UUID recipientId, UUID contactId) {
        return emergencyContactServiceImpl.delete(actorId, recipientId, contactId);
    }

    // ===== Recipient Manager =====
    @Override
    public APIResponse<List<RecipientManagerResponse>> listManagers(UUID actorId, UUID recipientId) {
        return recipientManagerServiceImpl.list(actorId, recipientId);
    }

    @Override
    public APIResponse<RecipientManagerResponse> createManager(UUID actorId, UUID recipientId, ManagerCreateRequest request) {
        return recipientManagerServiceImpl.create(actorId, recipientId, request);
    }

    @Override
    public APIResponse<RecipientManagerResponse> updateManager(UUID actorId, UUID recipientId, UUID managerId, ManagerUpdateRequest request) {
        return recipientManagerServiceImpl.update(actorId, recipientId, managerId, request);
    }

    @Override
    public APIResponse<RecipientManagerResponse> assignPrimaryManager(UUID actorId, UUID recipientId, UUID managerId) {
        return recipientManagerServiceImpl.assignPrimary(actorId, recipientId, managerId);
    }

    @Override
    public APIResponse<Void> deleteManager(UUID actorId, UUID recipientId, UUID managerId) {
        return recipientManagerServiceImpl.delete(actorId, recipientId, managerId);
    }
}
