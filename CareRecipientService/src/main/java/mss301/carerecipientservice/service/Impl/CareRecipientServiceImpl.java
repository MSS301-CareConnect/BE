package mss301.carerecipientservice.service.Impl;

import mss301.carerecipientservice.client.dto.RecipientActivityResponse;
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
import mss301.carerecipientservice.dto.response.ManagerInvitationResponse;
import mss301.carerecipientservice.dto.response.MyInvitationResponse;
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
import mss301.carerecipientservice.enums.RoleCode;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import mss301.carerecipientservice.mapper.CareRecipientMapper;
import mss301.carerecipientservice.mapper.EmergencyContactMapper;
import mss301.carerecipientservice.mapper.MedicationMapper;
import mss301.carerecipientservice.mapper.RecipientManagerMapper;
import mss301.carerecipientservice.repository.CareRecipientRepository;
import mss301.carerecipientservice.util.CareRecipientSpecifications;
import mss301.carerecipientservice.repository.EmergencyContactRepository;
import mss301.carerecipientservice.repository.MedicationRepository;
import mss301.carerecipientservice.repository.RecipientManagerRepository;
import mss301.carerecipientservice.service.CareRecipientService;
import mss301.carerecipientservice.service.RecipientManagerService;
import mss301.carerecipientservice.service.EmergencyContactService;
import mss301.carerecipientservice.service.MedicationService;
import mss301.carerecipientservice.security.CurrentRequestIdentity;
import mss301.carerecipientservice.service.RecipientAccessService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    CurrentRequestIdentity currentRequestIdentity;
    CareRecipientMapper careRecipientMapper;
    RecipientManagerMapper recipientManagerMapper;
    MedicationMapper medicationMapper;
    EmergencyContactMapper emergencyContactMapper;
    MedicationService medicationService;
    EmergencyContactService emergencyContactService;
    RecipientManagerService recipientManagerService;
    TransactionTemplate transactionTemplate;

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
    public APIResponse<PageResponse<CareRecipientSummaryResponse>> list(UUID actorId, Set<RoleCode> roles,
                                                                        RecipientStatus status, String keyword,
                                                                        UUID createdBy, boolean includeDeleted,
                                                                        int page, int size) {
        PageResponse<CareRecipientSummaryResponse> pageResponse;
        // ADMIN ưu tiên (toàn hệ thống); CUSTOMER chỉ thấy hồ sơ mình quản lý — createdBy / includeDeleted bị bỏ qua để
        // customer không dò được hồ sơ của người khác hay hồ sơ đã lưu trữ.
        if (roles.contains(RoleCode.ADMIN)) {
            pageResponse = listAll(status, keyword, createdBy, includeDeleted, page, size);
        } else if (roles.contains(RoleCode.CUSTOMER)) {
            pageResponse = listManaged(actorId, status, keyword, page, size);
        } else {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return APIResponse.success("Lấy danh sách hồ sơ thành công", pageResponse);
    }

    // Không @Transactional: nhánh CAREGIVER gọi BookingService qua mạng — không giữ transaction DB trong lúc chờ
    // (rule #24). Phần đọc DB của từng nhánh chạy trong transaction ngắn qua TransactionTemplate.
    // Thứ tự ưu tiên khi token có nhiều role: ADMIN → CUSTOMER → CAREGIVER.
    @Override
    public APIResponse<CareRecipientDetailResponse> get(UUID actorId, Set<RoleCode> roles, UUID id) {
        CareRecipientDetailResponse detailResponse;
        if (roles.contains(RoleCode.ADMIN)) {
            detailResponse = transactionTemplate.execute(status -> getAnyDetail(id));
        } else if (roles.contains(RoleCode.CUSTOMER)) {
            detailResponse = transactionTemplate.execute(status -> getManagedDetail(actorId, id));
        } else if (roles.contains(RoleCode.CAREGIVER)) {
            detailResponse = getAssignedDetail(actorId, id);
        } else {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return APIResponse.success("Lấy hồ sơ người bệnh thành công", detailResponse);
    }

    @Override
    @Transactional
    public APIResponse<CareRecipientResponse> update(UUID actorId, UUID id, CareRecipientUpdateRequest request) {
        recipientAccessService.checkAccess(id, actorId, RecipientAccessLevel.EDITOR);
        CareRecipient recipient = lockRecipient(id);
        if (recipient.getStatus() == RecipientStatus.INACTIVE
                && recipientAccessService.findAccessLevel(id, actorId).orElse(null) != RecipientAccessLevel.OWNER) {
            throw new AppException(ErrorCode.RECIPIENT_INACTIVE);
        }
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

    // Không @Transactional: tạm ngưng phải hỏi BookingService (gọi mạng) TRƯỚC transaction ghi (M-07).
    @Override
    public APIResponse<CareRecipientResponse> updateStatus(UUID actorId, UUID id, RecipientStatus status) {
        if (status != RecipientStatus.ACTIVE && status != RecipientStatus.INACTIVE) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }
        recipientAccessService.checkAccess(id, actorId, RecipientAccessLevel.OWNER);
        if (status == RecipientStatus.INACTIVE) {
            checkNoUnfinishedBookings(id);
        }
        CareRecipientResponse careRecipientResponse = transactionTemplate.execute(tx -> {
            CareRecipient recipient = lockRecipient(id);
            recipient.setStatus(status);
            recipient.setUpdatedBy(actorId);
            careRecipientRepository.flush();
            return careRecipientMapper.toResponse(recipient);
        });
        log.info("Care recipient {} status changed to {} by user {}", id, status, actorId);
        return APIResponse.success("Cập nhật trạng thái hồ sơ thành công", careRecipientResponse);
    }

    @Override
    public APIResponse<Void> delete(UUID actorId, UUID id) {
        recipientAccessService.checkAccess(id, actorId, RecipientAccessLevel.OWNER);
        checkNoUnfinishedBookings(id);
        transactionTemplate.executeWithoutResult(tx -> {
            CareRecipient recipient = lockRecipient(id);
            recipient.setStatus(RecipientStatus.ARCHIVED);
            recipient.setDeletedAt(Instant.now());
            recipient.setUpdatedBy(actorId);
        });
        log.info("Care recipient {} archived by user {}", id, actorId);
        return APIResponse.success("Đã lưu trữ hồ sơ người bệnh");
    }

    // M-07: còn booking chưa kết thúc → không tạm ngưng / lưu trữ (caregiver vẫn cần hồ sơ). Booking lỗi → 503 (fail-closed).
    // Khe hở: booking mới tạo giữa lúc kiểm và lúc ghi vẫn lọt — BookingService chặn đặt lịch cho hồ sơ không ACTIVE.
    private void checkNoUnfinishedBookings(UUID id) {
        BookingApiResponse<RecipientActivityResponse> response = bookingClient.getRecipientActivity(id);
        if (response == null || response.getResult() == null) {
            throw new AppException(ErrorCode.BOOKING_UNAVAILABLE);
        }
        if (response.getResult().getUnfinishedBookings() > 0) {
            throw new AppException(ErrorCode.RECIPIENT_HAS_ACTIVE_BOOKINGS);
        }
    }

    // ===== Inter-Service =====

    @Override
    @Transactional(readOnly = true)
    public APIResponse<RecipientSnapshotResponse> getSnapshot(UUID id) {
        if (!currentRequestIdentity.hasRole(RoleCode.SERVICE.name())
                && !currentRequestIdentity.hasRole(RoleCode.ADMIN.name())) {
            recipientAccessService.checkAccess(id, currentRequestIdentity.userId(), RecipientAccessLevel.VIEWER);
        }
        RecipientSnapshotResponse snapshotResponse = buildSnapshot(id);
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
        if (!currentRequestIdentity.hasRole(RoleCode.SERVICE.name())
                && !currentRequestIdentity.hasRole(RoleCode.ADMIN.name())) {
            UUID actorId = currentRequestIdentity.userId();
            recipients = recipients.stream().filter(recipient ->
                    recipientAccessService.findAccessLevel(recipient.getId(), actorId).isPresent()).toList();
        }
        List<CareRecipientSummaryResponse> summaryResponses = careRecipientMapper.toSummaryResponseList(recipients);
        return APIResponse.success("Lấy danh sách hồ sơ thành công", summaryResponses);
    }

    // ===== Helpers =====

    private PageResponse<CareRecipientSummaryResponse> listManaged(UUID actorId, RecipientStatus status, String keyword,
                                                                   int page, int size) {
        PageRequest pageRequest = toPageRequest(page, size, "recipient.createdAt");
        Page<RecipientManager> memberships = recipientManagerRepository.findAll(
                CareRecipientSpecifications.managedBy(actorId, status, keyword), pageRequest);
        List<CareRecipientSummaryResponse> summaryResponses = memberships.getContent().stream()
                .map(membership -> careRecipientMapper.toSummaryResponse(membership.getRecipient(), membership))
                .toList();
        return PageResponse.of(memberships, summaryResponses);
    }

    private PageResponse<CareRecipientSummaryResponse> listAll(RecipientStatus status, String keyword, UUID createdBy,
                                                               boolean includeDeleted, int page, int size) {
        PageRequest pageRequest = toPageRequest(page, size, "createdAt");
        Page<CareRecipient> recipients = careRecipientRepository.findAll(
                CareRecipientSpecifications.forAdmin(status, keyword, createdBy, includeDeleted), pageRequest);
        List<CareRecipientSummaryResponse> summaryResponses = careRecipientMapper.toSummaryResponseList(recipients.getContent());
        return PageResponse.of(recipients, summaryResponses);
    }

    // ADMIN đọc được cả hồ sơ đã lưu trữ (findById không lọc deletedAt) để tra soát / xử lý khiếu nại.
    private CareRecipientDetailResponse getAnyDetail(UUID id) {
        CareRecipient recipient = careRecipientRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_NOT_FOUND));
        return toDetailResponse(recipient, null);
    }

    private CareRecipientDetailResponse getManagedDetail(UUID actorId, UUID id) {
        RecipientManager membership = recipientAccessService.checkAccess(id, actorId, RecipientAccessLevel.VIEWER);
        return toDetailResponse(membership.getRecipient(), membership.getAccessLevel());
    }

    // Không được phân công → 404 như hồ sơ không tồn tại (không lộ sự tồn tại); Booking lỗi → 503, không bao giờ đoán
    // là được phép. Caregiver chỉ nhận careSnapshot, không bao giờ nhận profile / thuốc / liên hệ đầy đủ.
    private CareRecipientDetailResponse getAssignedDetail(UUID caregiverUserId, UUID id) {
        if (!checkAssigned(id, caregiverUserId)) {
            throw new AppException(ErrorCode.RECIPIENT_NOT_FOUND);
        }
        RecipientSnapshotResponse snapshotResponse = transactionTemplate.execute(status -> buildSnapshot(id));
        return careRecipientMapper.toCaregiverDetailResponse(snapshotResponse);
    }

    private boolean checkAssigned(UUID recipientId, UUID caregiverUserId) {
        BookingApiResponse<CaregiverAccessResponse> response = bookingClient.checkCaregiverAccess(caregiverUserId, recipientId);
        if (response == null || response.getResult() == null) {
            throw new AppException(ErrorCode.BOOKING_UNAVAILABLE);
        }
        return response.getResult().isAllowed();
    }

    private RecipientSnapshotResponse buildSnapshot(UUID id) {
        CareRecipient recipient = careRecipientRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_NOT_FOUND));
        List<Medication> activeMedications = medicationRepository.findAllByRecipientAndActive(id, true);
        EmergencyContact primaryContact = emergencyContactRepository.findPrimary(id).orElse(null);
        return careRecipientMapper.toSnapshotResponse(recipient, activeMedications, primaryContact, Instant.now());
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
        return medicationService.create(actorId, recipientId, request);
    }

    @Override
    public APIResponse<List<MedicationResponse>> listMedications(UUID actorId, UUID recipientId, Boolean active) {
        return medicationService.list(actorId, recipientId, active);
    }

    @Override
    public APIResponse<MedicationResponse> getMedication(UUID actorId, UUID recipientId, UUID medicationId) {
        return medicationService.get(actorId, recipientId, medicationId);
    }

    @Override
    public APIResponse<MedicationResponse> updateMedication(UUID actorId, UUID recipientId, UUID medicationId, MedicationRequest request) {
        return medicationService.update(actorId, recipientId, medicationId, request);
    }

    @Override
    public APIResponse<MedicationResponse> updateMedicationActive(UUID actorId, UUID recipientId, UUID medicationId, boolean active) {
        return medicationService.updateActive(actorId, recipientId, medicationId, active);
    }

    @Override
    public APIResponse<Void> deleteMedication(UUID actorId, UUID recipientId, UUID medicationId) {
        return medicationService.delete(actorId, recipientId, medicationId);
    }

    // ===== Emergency Contact =====
    @Override
    public APIResponse<EmergencyContactResponse> createEmergencyContact(UUID actorId, UUID recipientId, EmergencyContactRequest request) {
        return emergencyContactService.create(actorId, recipientId, request);
    }

    @Override
    public APIResponse<List<EmergencyContactResponse>> listEmergencyContacts(UUID actorId, UUID recipientId) {
        return emergencyContactService.list(actorId, recipientId);
    }

    @Override
    public APIResponse<EmergencyContactResponse> getEmergencyContact(UUID actorId, UUID recipientId, UUID contactId) {
        return emergencyContactService.get(actorId, recipientId, contactId);
    }

    @Override
    public APIResponse<EmergencyContactResponse> updateEmergencyContact(UUID actorId, UUID recipientId, UUID contactId, EmergencyContactRequest request) {
        return emergencyContactService.update(actorId, recipientId, contactId, request);
    }

    @Override
    public APIResponse<EmergencyContactResponse> assignPrimaryEmergencyContact(UUID actorId, UUID recipientId, UUID contactId) {
        return emergencyContactService.assignPrimary(actorId, recipientId, contactId);
    }

    @Override
    public APIResponse<Void> deleteEmergencyContact(UUID actorId, UUID recipientId, UUID contactId) {
        return emergencyContactService.delete(actorId, recipientId, contactId);
    }

    // ===== Recipient Manager =====
    @Override
    public APIResponse<List<RecipientManagerResponse>> listManagers(UUID actorId, UUID recipientId) {
        return recipientManagerService.list(actorId, recipientId);
    }

    @Override
    public APIResponse<List<ManagerInvitationResponse>> listInvitations(UUID actorId, UUID recipientId) {
        return recipientManagerService.listInvitations(actorId, recipientId);
    }

    @Override
    public APIResponse<List<MyInvitationResponse>> listMyInvitations(UUID actorId) {
        return recipientManagerService.listMyInvitations(actorId);
    }

    @Override
    public APIResponse<ManagerInvitationResponse> inviteManager(UUID actorId, UUID recipientId,
                                                                ManagerCreateRequest request) {
        return recipientManagerService.invite(actorId, recipientId, request);
    }

    @Override
    public APIResponse<RecipientManagerResponse> acceptInvitation(UUID actorId, UUID invitationId) {
        return recipientManagerService.acceptInvitation(actorId, invitationId);
    }

    @Override
    public APIResponse<Void> declineInvitation(UUID actorId, UUID invitationId) {
        return recipientManagerService.declineInvitation(actorId, invitationId);
    }

    @Override
    public APIResponse<Void> revokeInvitation(UUID actorId, UUID recipientId, UUID invitationId) {
        return recipientManagerService.revokeInvitation(actorId, recipientId, invitationId);
    }

    @Override
    public APIResponse<RecipientManagerResponse> updateManager(UUID actorId, UUID recipientId, UUID managerId, ManagerUpdateRequest request) {
        return recipientManagerService.update(actorId, recipientId, managerId, request);
    }

    @Override
    public APIResponse<RecipientManagerResponse> assignPrimaryManager(UUID actorId, UUID recipientId, UUID managerId) {
        return recipientManagerService.assignPrimary(actorId, recipientId, managerId);
    }

    @Override
    public APIResponse<Void> deleteManager(UUID actorId, UUID recipientId, UUID managerId) {
        return recipientManagerService.delete(actorId, recipientId, managerId);
    }
}
