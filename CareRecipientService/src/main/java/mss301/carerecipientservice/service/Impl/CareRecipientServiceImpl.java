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
import mss301.carerecipientservice.dto.request.RecipientAddressRequest;
import mss301.carerecipientservice.dto.request.MedicationRequest;
import mss301.carerecipientservice.dto.request.RecipientBatchRequest;
import mss301.carerecipientservice.dto.request.ManagerCreateRequest;
import mss301.carerecipientservice.dto.request.ManagerUpdateRequest;
import mss301.carerecipientservice.dto.response.EmergencyContactResponse;
import mss301.carerecipientservice.dto.response.RecipientAddressResponse;
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
import mss301.carerecipientservice.service.RecipientAddressService;
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
    RecipientAddressService recipientAddressService;
    RecipientManagerService recipientManagerService;
    TransactionTemplate transactionTemplate;

    // Tạo hồ sơ người bệnh kèm thuốc, liên hệ khẩn cấp; người tạo là quản lý chính (OWNER).
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

    // Danh sách hồ sơ: admin lọc toàn hệ thống, người dùng chỉ thấy hồ sơ mình quản lý.
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

    // Cập nhật hồ sơ (cần quyền EDITOR; hồ sơ ngừng hoạt động chỉ OWNER được sửa).
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

    // Lưu trữ hồ sơ (chỉ OWNER, chặn khi còn booking chưa xong).
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

    // Ảnh chụp thông tin chăm sóc của hồ sơ cho service khác / người có quyền xem.
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

    // Kiểm tra người dùng có quyền tối thiểu trên hồ sơ, trả kèm mức quyền hiện có.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<AccessCheckResponse> checkAccess(UUID id, UUID userId, RecipientAccessLevel minLevel) {
        Optional<RecipientAccessLevel> accessLevel = recipientAccessService.findAccessLevel(id, userId);
        boolean allowed = accessLevel.map(level -> level.covers(minLevel)).orElse(false);
        AccessCheckResponse accessCheckResponse = recipientManagerMapper.toAccessCheckResponse(id, userId, allowed,
                accessLevel.orElse(null));
        return APIResponse.success("Kiểm tra quyền thành công", accessCheckResponse);
    }

    // Tóm tắt nhiều hồ sơ; người dùng thường chỉ nhận hồ sơ mình có quyền.
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

    // Danh sách hồ sơ người dùng đang quản lý, có lọc và phân trang.
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

    // Danh sách toàn bộ hồ sơ cho admin, có lọc và phân trang.
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

    // Chi tiết hồ sơ cho người có quyền xem (hoặc caregiver đang được giao).
    private CareRecipientDetailResponse getManagedDetail(UUID actorId, UUID id) {
        RecipientManager membership = recipientAccessService.checkAccess(id, actorId, RecipientAccessLevel.VIEWER);
        return toDetailResponse(membership.getRecipient(), membership.getAccessLevel());
    }

    // Không được phân công → 404 như hồ sơ không tồn tại (không lộ sự tồn tại); Booking lỗi → 503, không bao giờ đoán
    // là được phép. Caregiver nhận careSnapshot (hồ sơ chăm sóc + thuốc đang dùng), careDetails (tiền sử bệnh, giấc ngủ,
    // chiều cao / cân nặng) và mọi liên hệ khẩn cấp — chỉ khi
    // BookingService xác nhận đang được phân công (booking CONFIRMED / IN_PROGRESS); không bao giờ nhận profile / quyền.
    private CareRecipientDetailResponse getAssignedDetail(UUID caregiverUserId, UUID id) {
        if (!checkAssigned(id, caregiverUserId)) {
            throw new AppException(ErrorCode.RECIPIENT_NOT_FOUND);
        }
        return transactionTemplate.execute(status -> {
            CareRecipient recipient = careRecipientRepository.findByIdAndDeletedAtIsNull(id)
                    .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_NOT_FOUND));
            return careRecipientMapper.toCaregiverDetailResponse(toSnapshot(recipient), recipient,
                    emergencyContactRepository.findByRecipientIdAndDeletedAtIsNullOrderByPrimaryDescPriorityAscCreatedAtAsc(id));
        });
    }

    // Hỏi BookingService caregiver có đang được giao chăm sóc người bệnh này không.
    private boolean checkAssigned(UUID recipientId, UUID caregiverUserId) {
        BookingApiResponse<CaregiverAccessResponse> response = bookingClient.checkCaregiverAccess(caregiverUserId, recipientId);
        if (response == null || response.getResult() == null) {
            throw new AppException(ErrorCode.BOOKING_UNAVAILABLE);
        }
        return response.getResult().isAllowed();
    }

    // Tìm hồ sơ chưa xóa rồi dựng ảnh chụp thông tin chăm sóc.
    private RecipientSnapshotResponse buildSnapshot(UUID id) {
        CareRecipient recipient = careRecipientRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_NOT_FOUND));
        return toSnapshot(recipient);
    }

    // Dựng ảnh chụp gồm thuốc đang dùng và liên hệ khẩn cấp chính.
    private RecipientSnapshotResponse toSnapshot(CareRecipient recipient) {
        List<Medication> activeMedications = medicationRepository.findByRecipientIdAndActiveAndDeletedAtIsNullOrderByStartDateDescCreatedAtAsc(recipient.getId(), true);
        EmergencyContact primaryContact = emergencyContactRepository.findByRecipientIdAndPrimaryTrueAndDeletedAtIsNull(recipient.getId()).orElse(null);
        return careRecipientMapper.toSnapshotResponse(recipient, activeMedications, primaryContact, Instant.now());
    }

    // Dựng chi tiết hồ sơ gồm thuốc, liên hệ khẩn cấp và mức quyền của người xem.
    private CareRecipientDetailResponse toDetailResponse(CareRecipient recipient, RecipientAccessLevel myAccessLevel) {
        List<Medication> medications = medicationRepository.findByRecipientIdAndDeletedAtIsNullOrderByActiveDescStartDateDescCreatedAtAsc(recipient.getId());
        List<EmergencyContact> emergencyContacts = emergencyContactRepository.findByRecipientIdAndDeletedAtIsNullOrderByPrimaryDescPriorityAscCreatedAtAsc(recipient.getId());
        return careRecipientMapper.toDetailResponse(recipient, myAccessLevel, medications, emergencyContacts);
    }

    // Khóa dòng hồ sơ để cập nhật, không có thì báo lỗi.
    private CareRecipient lockRecipient(UUID id) {
        return careRecipientRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_NOT_FOUND));
    }

    // Tạo PageRequest sắp xếp mới nhất trước sau khi kiểm tra page / size.
    private PageRequest toPageRequest(int page, int size, String sortProperty) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortProperty));
    }

    // Báo lỗi nếu chỉ gửi một trong hai tọa độ (vĩ độ / kinh độ).
    private void checkCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new AppException(ErrorCode.INVALID_COORDINATES);
        }
    }

    // Báo lỗi nếu có hơn một liên hệ khẩn cấp chính.
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

    // Báo lỗi nếu ngày kết thúc thuốc trước ngày bắt đầu.
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

    // Thêm thuốc (ủy quyền MedicationService).
    @Override
    public APIResponse<MedicationResponse> createMedication(UUID actorId, UUID recipientId, MedicationRequest request) {
        return medicationService.create(actorId, recipientId, request);
    }

    // Danh sách thuốc (ủy quyền MedicationService).
    @Override
    public APIResponse<List<MedicationResponse>> listMedications(UUID actorId, UUID recipientId, Boolean active) {
        return medicationService.list(actorId, recipientId, active);
    }

    // Chi tiết thuốc (ủy quyền MedicationService).
    @Override
    public APIResponse<MedicationResponse> getMedication(UUID actorId, UUID recipientId, UUID medicationId) {
        return medicationService.get(actorId, recipientId, medicationId);
    }

    // Cập nhật thuốc (ủy quyền MedicationService).
    @Override
    public APIResponse<MedicationResponse> updateMedication(UUID actorId, UUID recipientId, UUID medicationId, MedicationRequest request) {
        return medicationService.update(actorId, recipientId, medicationId, request);
    }

    // Bật / tắt thuốc (ủy quyền MedicationService).
    @Override
    public APIResponse<MedicationResponse> updateMedicationActive(UUID actorId, UUID recipientId, UUID medicationId, boolean active) {
        return medicationService.updateActive(actorId, recipientId, medicationId, active);
    }

    // Xóa thuốc (ủy quyền MedicationService).
    @Override
    public APIResponse<Void> deleteMedication(UUID actorId, UUID recipientId, UUID medicationId) {
        return medicationService.delete(actorId, recipientId, medicationId);
    }

    // Thêm liên hệ khẩn cấp (ủy quyền EmergencyContactService).
    @Override
    public APIResponse<EmergencyContactResponse> createEmergencyContact(UUID actorId, UUID recipientId, EmergencyContactRequest request) {
        return emergencyContactService.create(actorId, recipientId, request);
    }

    // Danh sách địa chỉ (ủy quyền RecipientAddressService).
    @Override
    public APIResponse<List<RecipientAddressResponse>> listAddresses(UUID actorId, UUID recipientId) {
        return recipientAddressService.list(actorId, recipientId);
    }

    // Thêm địa chỉ (ủy quyền RecipientAddressService).
    @Override
    public APIResponse<RecipientAddressResponse> createAddress(UUID actorId, UUID recipientId,
                                                               RecipientAddressRequest request) {
        return recipientAddressService.create(actorId, recipientId, request);
    }

    // Cập nhật địa chỉ (ủy quyền RecipientAddressService).
    @Override
    public APIResponse<RecipientAddressResponse> updateAddress(UUID actorId, UUID recipientId, UUID addressId,
                                                               RecipientAddressRequest request) {
        return recipientAddressService.update(actorId, recipientId, addressId, request);
    }

    // Xóa địa chỉ (ủy quyền RecipientAddressService).
    @Override
    public APIResponse<Void> deleteAddress(UUID actorId, UUID recipientId, UUID addressId) {
        return recipientAddressService.delete(actorId, recipientId, addressId);
    }

    // Danh sách liên hệ khẩn cấp (ủy quyền EmergencyContactService).
    @Override
    public APIResponse<List<EmergencyContactResponse>> listEmergencyContacts(UUID actorId, UUID recipientId) {
        return emergencyContactService.list(actorId, recipientId);
    }

    // Chi tiết liên hệ khẩn cấp (ủy quyền EmergencyContactService).
    @Override
    public APIResponse<EmergencyContactResponse> getEmergencyContact(UUID actorId, UUID recipientId, UUID contactId) {
        return emergencyContactService.get(actorId, recipientId, contactId);
    }

    // Cập nhật liên hệ khẩn cấp (ủy quyền EmergencyContactService).
    @Override
    public APIResponse<EmergencyContactResponse> updateEmergencyContact(UUID actorId, UUID recipientId, UUID contactId, EmergencyContactRequest request) {
        return emergencyContactService.update(actorId, recipientId, contactId, request);
    }

    // Đặt liên hệ chính (ủy quyền EmergencyContactService).
    @Override
    public APIResponse<EmergencyContactResponse> assignPrimaryEmergencyContact(UUID actorId, UUID recipientId, UUID contactId) {
        return emergencyContactService.assignPrimary(actorId, recipientId, contactId);
    }

    // Xóa liên hệ khẩn cấp (ủy quyền EmergencyContactService).
    @Override
    public APIResponse<Void> deleteEmergencyContact(UUID actorId, UUID recipientId, UUID contactId) {
        return emergencyContactService.delete(actorId, recipientId, contactId);
    }

    // Danh sách người cùng quản lý (ủy quyền RecipientManagerService).
    @Override
    public APIResponse<List<RecipientManagerResponse>> listManagers(UUID actorId, UUID recipientId) {
        return recipientManagerService.list(actorId, recipientId);
    }

    // Danh sách lời mời của hồ sơ (ủy quyền RecipientManagerService).
    @Override
    public APIResponse<List<ManagerInvitationResponse>> listInvitations(UUID actorId, UUID recipientId) {
        return recipientManagerService.listInvitations(actorId, recipientId);
    }

    // Lời mời đang chờ người dùng (ủy quyền RecipientManagerService).
    @Override
    public APIResponse<List<MyInvitationResponse>> listMyInvitations(UUID actorId) {
        return recipientManagerService.listMyInvitations(actorId);
    }

    // Mời người cùng quản lý (ủy quyền RecipientManagerService).
    @Override
    public APIResponse<ManagerInvitationResponse> inviteManager(UUID actorId, UUID recipientId,
                                                                ManagerCreateRequest request) {
        return recipientManagerService.invite(actorId, recipientId, request);
    }

    // Chấp nhận lời mời (ủy quyền RecipientManagerService).
    @Override
    public APIResponse<RecipientManagerResponse> acceptInvitation(UUID actorId, UUID invitationId) {
        return recipientManagerService.acceptInvitation(actorId, invitationId);
    }

    // Từ chối lời mời (ủy quyền RecipientManagerService).
    @Override
    public APIResponse<Void> declineInvitation(UUID actorId, UUID invitationId) {
        return recipientManagerService.declineInvitation(actorId, invitationId);
    }

    // Thu hồi lời mời (ủy quyền RecipientManagerService).
    @Override
    public APIResponse<Void> revokeInvitation(UUID actorId, UUID recipientId, UUID invitationId) {
        return recipientManagerService.revokeInvitation(actorId, recipientId, invitationId);
    }

    // Đổi quyền người cùng quản lý (ủy quyền RecipientManagerService).
    @Override
    public APIResponse<RecipientManagerResponse> updateManager(UUID actorId, UUID recipientId, UUID managerId, ManagerUpdateRequest request) {
        return recipientManagerService.update(actorId, recipientId, managerId, request);
    }

    // Chuyển quản lý chính (ủy quyền RecipientManagerService).
    @Override
    public APIResponse<RecipientManagerResponse> assignPrimaryManager(UUID actorId, UUID recipientId, UUID managerId) {
        return recipientManagerService.assignPrimary(actorId, recipientId, managerId);
    }

    // Gỡ người cùng quản lý (ủy quyền RecipientManagerService).
    @Override
    public APIResponse<Void> deleteManager(UUID actorId, UUID recipientId, UUID managerId) {
        return recipientManagerService.delete(actorId, recipientId, managerId);
    }
}
