package mss301.providerservice.service.Impl;

import mss301.providerservice.service.ProviderOutboxService;
import mss301.providerservice.client.dto.IdentityUserResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.client.CareCatalogClient;
import mss301.providerservice.client.dto.CenterServiceClientResponse;
import mss301.providerservice.client.dto.RemoteApiResponse;
import mss301.providerservice.dto.request.AvailabilityCheckRequest;
import mss301.providerservice.dto.request.CaregiverCreateRequest;
import mss301.providerservice.dto.request.CaregiverSearchRequest;
import mss301.providerservice.dto.request.CaregiverSkillRequest;
import mss301.providerservice.dto.request.CaregiverUpdateRequest;
import mss301.providerservice.dto.request.CaregiverStatusRequest;
import mss301.providerservice.dto.request.CaregiverRatingRequest;
import mss301.providerservice.dto.request.TimeOffRequest;
import mss301.providerservice.dto.request.VerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.AvailabilityCheckResponse;
import mss301.providerservice.dto.response.AvailabilityViolationResponse;
import mss301.providerservice.dto.response.AvailabilityWindowResponse;
import mss301.providerservice.dto.response.CaregiverCardResponse;
import mss301.providerservice.dto.response.CaregiverPublicResponse;
import mss301.providerservice.dto.response.CaregiverResponse;
import mss301.providerservice.dto.response.CaregiverSkillResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.TimeOffResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.CaregiverSchedule;
import mss301.providerservice.entity.CaregiverSkill;
import mss301.providerservice.entity.CaregiverTimeOff;
import mss301.providerservice.entity.Certificate;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.AvailabilityViolationReason;
import mss301.providerservice.enums.CaregiverSearchSort;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.MediaOwnerType;
import mss301.providerservice.enums.RoleCode;
import mss301.providerservice.enums.ScheduleStatus;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.CaregiverMapper;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.repository.CaregiverCertificateCount;
import mss301.providerservice.repository.CaregiverProfileRepository;
import mss301.providerservice.repository.CaregiverScheduleRepository;
import mss301.providerservice.repository.CaregiverSkillRepository;
import mss301.providerservice.repository.CaregiverTimeOffRepository;
import mss301.providerservice.repository.CertificateRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.BookingAvailabilityService;
import mss301.providerservice.service.CaregiverService;
import mss301.providerservice.service.IdentityAccountService;
import mss301.providerservice.service.ProviderMediaService;
import mss301.providerservice.util.ProviderTime;
import mss301.providerservice.util.TimeWindow;
import mss301.providerservice.util.TimeWindows;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import jakarta.persistence.criteria.Predicate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import mss301.providerservice.enums.CertificateVerificationStatus;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CaregiverServiceImpl implements CaregiverService {

    static int MAX_PAGE_SIZE = 100;
    static String ACTIVE_STATUS = "ACTIVE";
    static Duration MAX_TIME_OFF = Duration.ofDays(30);
    static Duration MAX_AVAILABILITY_RANGE = Duration.ofDays(31);
    static int MAX_SLOTS = 60;
    // Quá ngưỡng này thì lọc lịch trong bộ nhớ + gọi Booking quá nặng → yêu cầu khách thu hẹp (SRCH-01 bước (a)).
    static int MAX_SEARCH_CANDIDATES = 500;
    static int DEFAULT_SEARCH_SIZE = 20;
    static String SLOT_SEPARATOR = "/";
    // Không lọc khoảng thời gian khi xem danh sách ngày nghỉ: mốc đủ xa hai phía (timestamptz chịu được).
    static Instant TIME_OFF_LIST_MIN = Instant.parse("1970-01-01T00:00:00Z");
    static Instant TIME_OFF_LIST_MAX = Instant.parse("9999-12-31T00:00:00Z");

    CareCenterRepository careCenterRepository;
    CaregiverProfileRepository caregiverProfileRepository;
    ProviderAuthorization providerAuthorization;
    CaregiverMapper caregiverMapper;
    IdentityAccountService identityAccountService;
    TransactionTemplate transactionTemplate;
    CaregiverSkillRepository caregiverSkillRepository;
    CareCatalogClient careCatalogClient;
    CaregiverTimeOffRepository caregiverTimeOffRepository;
    CaregiverScheduleRepository caregiverScheduleRepository;
    CertificateRepository certificateRepository;
    BookingAvailabilityService bookingAvailabilityService;
    ProviderMediaService providerMediaService;
    Clock clock;
    ProviderOutboxService providerOutboxService;

    /**
     * Không đặt @Transactional: kiểm tài khoản Identity là lời gọi mạng nên chạy TRƯỚC, ngoài transaction;
     * phần ghi DB chạy trong transactionTemplate (rule #24).
     */
    @Override
    public APIResponse<CaregiverResponse> create(UUID actorId, UUID centerId, CaregiverCreateRequest request) {
        IdentityUserResponse account = identityAccountService.requireActiveRole(request.getUserId(), RoleCode.CAREGIVER);
        // H-12: tài khoản caregiver phải được tạo cho đúng trung tâm này (claim center_id) — không gắn chéo trung tâm.
        if (!centerId.equals(account.getCenterId())) {
            throw new AppException(ErrorCode.IDENTITY_CENTER_MISMATCH);
        }
        CaregiverResponse caregiverResponse = transactionTemplate.execute(
                status -> createProfile(actorId, centerId, request));
        return APIResponse.success("Tạo hồ sơ người chăm sóc thành công", caregiverResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CaregiverResponse>> listByCenter(UUID actorId, UUID centerId,
                                                                    VerificationStatus verification,
                                                                    CaregiverStatus status, int page, int size) {
        validatePage(page, size);
        CareCenter center = findActiveCenter(centerId);
        providerAuthorization.requireCenterManagement(center, actorId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CaregiverProfile> caregiverPage;
        if (verification != null) {
            caregiverPage = caregiverProfileRepository.findByCenterIdAndVerificationStatusAndDeletedAtIsNull(
                    centerId, verification, pageable);
        } else if (status != null) {
            caregiverPage = caregiverProfileRepository.findByCenterIdAndCaregiverStatusAndDeletedAtIsNull(
                    centerId, status, pageable);
        } else {
            caregiverPage = caregiverProfileRepository.findByCenterIdAndDeletedAtIsNull(centerId, pageable);
        }
        List<CaregiverResponse> items = caregiverMapper.toResponseList(caregiverPage.getContent());
        PageResponse<CaregiverResponse> pageResponse = PageResponse.of(caregiverPage, items);
        return APIResponse.success("Lấy danh sách hồ sơ người chăm sóc thành công", pageResponse);
    }

    /**
     * Hồ sơ đầy đủ (field nội bộ) chỉ cho ADMIN / manager của trung tâm / chính caregiver / token SERVICE (BookingService
     * đọc kỹ năng — C5.5). Khách dùng {@link #getPublic(UUID)} (SRCH-01 quy tắc 6).
     */
    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverResponse> get(UUID actorId, UUID caregiverId) {
        CaregiverProfile caregiver = findActiveCaregiver(caregiverId);
        checkReadScope(caregiver, actorId);
        CaregiverResponse caregiverResponse = caregiverMapper.toResponse(caregiver);
        return APIResponse.success("Lấy hồ sơ người chăm sóc thành công", caregiverResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverResponse> getByUser(UUID userId) {
        CaregiverProfile caregiver = findActiveCaregiverByUser(userId);
        checkReadScope(caregiver, providerAuthorization.currentUserId());
        CaregiverResponse caregiverResponse = caregiverMapper.toResponse(caregiver);
        return APIResponse.success("Lấy hồ sơ người chăm sóc thành công", caregiverResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverResponse> getMe(UUID actorId) {
        CaregiverProfile caregiver = findActiveCaregiverByUser(actorId);
        CaregiverResponse caregiverResponse = caregiverMapper.toResponse(caregiver);
        return APIResponse.success("Lấy hồ sơ của tôi thành công", caregiverResponse);
    }

    /**
     * Danh mục cho khách: chỉ thẻ công khai (M-02) — không userId, ngày sinh, lý do xác minh hay người duyệt. Không đặt
     * @Transactional: đọc trang trong transaction, lấy ảnh (Media, mạng) ngoài transaction, rồi dựng thẻ (rule #24).
     */
    @Override
    public APIResponse<PageResponse<CaregiverCardResponse>> list(UUID centerId, String keyword, int page, int size) {
        validatePage(page, size);
        String normalizedKeyword = normalize(keyword);
        Sort sort = Sort.by(Sort.Direction.DESC, "ratingAverage")
                .and(Sort.by(Sort.Direction.DESC, "experienceYears"));
        Page<UUID> idPage = transactionTemplate.execute(status -> caregiverProfileRepository.catalog(
                        VerificationStatus.VERIFIED, CaregiverStatus.ACTIVE, CenterStatus.ACTIVE, centerId, normalizedKeyword,
                        PageRequest.of(page, size, sort))
                .map(CaregiverProfile::getId));
        Map<UUID, List<String>> avatarUrls = providerMediaService.findImageUrls(
                MediaOwnerType.CAREGIVER_PROFILE, idPage.getContent(), 1);
        List<CaregiverCardResponse> items = transactionTemplate.execute(status ->
                buildCards(idPage.getContent(), avatarUrls));
        PageResponse<CaregiverCardResponse> pageResponse = PageResponse.of(idPage, items);
        return APIResponse.success("Lấy danh sách caregiver đang hoạt động thành công", pageResponse);
    }

    /**
     * SRCH-01. Không đặt @Transactional: (a) lấy toàn bộ ứng viên thỏa điều kiện tĩnh (≤ 500) + (b1) lọc lịch rảnh /
     * ngày nghỉ + sắp xếp trong một transaction đọc → (b2) hỏi Booking caregiver bận NGOÀI transaction (rule #24) →
     * (c) phân trang trong bộ nhớ rồi mới dựng thẻ cho đúng trang. Booking lỗi → 503, không trả danh sách sai.
     */
    @Override
    public APIResponse<PageResponse<CaregiverCardResponse>> search(CaregiverSearchRequest request) {
        List<TimeWindow> slots = parseSearchSlots(request);
        List<UUID> candidateIds = transactionTemplate.execute(status -> findSearchCandidateIds(request, slots));
        if (!slots.isEmpty() && !candidateIds.isEmpty()) {
            Set<UUID> busyCaregiverIds = bookingAvailabilityService.findBusyCaregiverIds(candidateIds, slots);
            candidateIds = candidateIds.stream()
                    .filter(caregiverId -> !busyCaregiverIds.contains(caregiverId))
                    .toList();
        }
        int page = request.getPage() == null ? 0 : request.getPage();
        int size = request.getSize() == null ? DEFAULT_SEARCH_SIZE : request.getSize();
        int fromIndex = (int) Math.min((long) page * size, candidateIds.size());
        int toIndex = Math.min(fromIndex + size, candidateIds.size());
        List<UUID> pageIds = candidateIds.subList(fromIndex, toIndex);
        Map<UUID, List<String>> avatarUrls = providerMediaService.findImageUrls(
                MediaOwnerType.CAREGIVER_PROFILE, pageIds, 1);
        List<CaregiverCardResponse> items = transactionTemplate.execute(status -> buildCards(pageIds, avatarUrls));
        Page<UUID> idPage = new PageImpl<>(pageIds, PageRequest.of(page, size), candidateIds.size());
        PageResponse<CaregiverCardResponse> pageResponse = PageResponse.of(idPage, items);
        return APIResponse.success("Tìm người chăm sóc thành công", pageResponse);
    }

    /** Không đặt @Transactional: lấy URL ảnh đại diện (Media, mạng) trước, đọc hồ sơ trong transaction sau. */
    @Override
    public APIResponse<CaregiverPublicResponse> getPublic(UUID caregiverId) {
        Map<UUID, List<String>> avatarUrls = providerMediaService.findImageUrls(
                MediaOwnerType.CAREGIVER_PROFILE, List.of(caregiverId), 1);
        String avatarUrl = firstUrl(avatarUrls.get(caregiverId));
        CaregiverPublicResponse caregiverPublicResponse = transactionTemplate.execute(
                status -> buildPublicProfile(caregiverId, avatarUrl));
        return APIResponse.success("Lấy hồ sơ công khai của người chăm sóc thành công", caregiverPublicResponse);
    }

    @Override
    @Transactional
    public APIResponse<CaregiverResponse> update(UUID actorId, UUID caregiverId, CaregiverUpdateRequest request) {
        CaregiverProfile caregiver = findActiveCaregiver(caregiverId);
        providerAuthorization.requireCenterManagement(caregiver.getCenter(), actorId);
        checkVersion(caregiver, request.getVersion());
        caregiverMapper.update(request, caregiver);
        normalizeFields(caregiver);
        caregiver.setUpdatedBy(actorId);
        CaregiverProfile savedCaregiver = caregiverProfileRepository.save(caregiver);
        CaregiverResponse caregiverResponse = caregiverMapper.toResponse(savedCaregiver);
        return APIResponse.success("Cập nhật hồ sơ người chăm sóc thành công", caregiverResponse);
    }

    @Override
    @Transactional
    public APIResponse<CaregiverResponse> updateStatus(UUID actorId, UUID caregiverId, CaregiverStatusRequest request) {
        CaregiverProfile caregiver = caregiverProfileRepository.findForUpdate(caregiverId)
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_NOT_FOUND));
        providerAuthorization.requireCenterManagement(caregiver.getCenter(), actorId);
        checkVersion(caregiver, request.getVersion());
        if (caregiver.getCaregiverStatus() == CaregiverStatus.ARCHIVED || request.getStatus() == CaregiverStatus.ARCHIVED) {
            throw new AppException(ErrorCode.CAREGIVER_INVALID_TRANSITION);
        }
        if (request.getStatus() == CaregiverStatus.ACTIVE && caregiver.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new AppException(ErrorCode.CAREGIVER_NOT_VERIFIED);
        }
        CaregiverStatus previous = caregiver.getCaregiverStatus();
        caregiver.setCaregiverStatus(request.getStatus());
        caregiver.setUpdatedBy(actorId);
        CaregiverResponse response = caregiverMapper.toResponse(caregiverProfileRepository.saveAndFlush(caregiver));
        cascadeDeactivation(caregiver, previous, "Người chăm sóc chuyển sang " + request.getStatus());
        return APIResponse.success("Cập nhật trạng thái người chăm sóc thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<CaregiverResponse> verify(UUID actorId, UUID caregiverId, VerificationRequest request) {
        CaregiverProfile caregiver = findActiveCaregiver(caregiverId);
        checkVersion(caregiver, request.getVersion());
        if (request.getStatus() == VerificationStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (request.getStatus() == VerificationStatus.REJECTED && isBlank(request.getReason())) {
            throw new AppException(ErrorCode.VERIFICATION_REASON_REQUIRED);
        }
        if (caregiver.getCaregiverStatus() == CaregiverStatus.ARCHIVED
                || caregiver.getCaregiverStatus() == CaregiverStatus.SUSPENDED
                || caregiver.getCenter().getCenterStatus() != CenterStatus.ACTIVE) {
            throw new AppException(ErrorCode.CAREGIVER_INVALID_TRANSITION);
        }
        CaregiverStatus previous = caregiver.getCaregiverStatus();
        caregiver.setVerificationStatus(request.getStatus());
        caregiver.setVerificationReason(normalize(request.getReason()));
        caregiver.setVerifiedBy(actorId);
        caregiver.setVerifiedAt(Instant.now());
        caregiver.setUpdatedBy(actorId);
        caregiver.setCaregiverStatus(request.getStatus() == VerificationStatus.VERIFIED
                ? CaregiverStatus.ACTIVE : CaregiverStatus.INACTIVE);
        CaregiverProfile savedCaregiver = caregiverProfileRepository.save(caregiver);
        cascadeDeactivation(savedCaregiver, previous, "Hồ sơ người chăm sóc bị từ chối xác minh");
        CaregiverResponse caregiverResponse = caregiverMapper.toResponse(savedCaregiver);
        return APIResponse.success("Xác minh hồ sơ người chăm sóc thành công", caregiverResponse);
    }

    @Override
    @Transactional
    public APIResponse<Void> archive(UUID actorId, UUID caregiverId, Long version) {
        CaregiverProfile caregiver = findActiveCaregiver(caregiverId);
        providerAuthorization.requireCenterManagement(caregiver.getCenter(), actorId);
        checkVersion(caregiver, version);
        CaregiverStatus previous = caregiver.getCaregiverStatus();
        caregiver.setCaregiverStatus(CaregiverStatus.ARCHIVED);
        caregiver.setDeletedAt(Instant.now());
        caregiver.setUpdatedBy(actorId);
        caregiverProfileRepository.save(caregiver);
        cascadeDeactivation(caregiver, previous, "Hồ sơ người chăm sóc đã được lưu trữ");
        return APIResponse.success("Lưu trữ hồ sơ người chăm sóc thành công");
    }

    // H-11: caregiver đang ACTIVE ngừng hoạt động → BookingService gắn cờ đổi người cho các buổi sắp tới + báo manager / khách
    // (qua provider_outbox, gửi sau commit, retry khi Booking lỗi).
    private void cascadeDeactivation(CaregiverProfile caregiver, CaregiverStatus previous, String reason) {
        if (previous == CaregiverStatus.ACTIVE && caregiver.getCaregiverStatus() != CaregiverStatus.ACTIVE) {
            providerOutboxService.enqueueCaregiverDeactivated(caregiver, reason);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<CaregiverSkillResponse>> listServices(UUID actorId, UUID caregiverId) {
        CaregiverProfile caregiver = findActiveCaregiver(caregiverId);
        providerAuthorization.requireCaregiverSelfOrCenterManagement(caregiver, actorId);
        List<CaregiverSkill> skills = caregiverSkillRepository.findByIdCaregiverIdOrderByAssignedAtAsc(caregiverId);
        List<CaregiverSkillResponse> skillResponses = caregiverMapper.toSkillResponseList(skills);
        return APIResponse.success("Lấy kỹ năng của người chăm sóc thành công", skillResponses);
    }

    /**
     * Không đặt @Transactional: kiểm phạm vi (transaction đọc ngắn) → gọi Catalog cho từng offering NGOÀI transaction
     * (rule #24) → thay toàn bộ trong một transaction ghi. Catalog lỗi giữa chừng thì chưa ghi gì.
     */
    @Override
    public APIResponse<List<CaregiverSkillResponse>> replaceServices(UUID actorId, UUID caregiverId,
                                                                      CaregiverSkillRequest request) {
        UUID centerId = transactionTemplate.execute(status -> findManagedCaregiverCenterId(actorId, caregiverId));
        Set<UUID> centerServiceIds = new LinkedHashSet<>(request.getCenterServiceIds());
        List<CenterServiceClientResponse> centerServices = new ArrayList<>();
        for (UUID centerServiceId : centerServiceIds) {
            centerServices.add(fetchAssignableCenterService(centerServiceId, centerId));
        }
        List<CaregiverSkillResponse> skillResponses = transactionTemplate.execute(
                status -> replaceSkills(actorId, caregiverId, centerId, centerServices));
        return APIResponse.success("Cập nhật kỹ năng của người chăm sóc thành công", skillResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<TimeOffResponse>> listTimeOffs(UUID actorId, UUID caregiverId, Instant from, Instant to) {
        CaregiverProfile caregiver = findActiveCaregiver(caregiverId);
        providerAuthorization.requireCaregiverSelfOrCenterManagement(caregiver, actorId);
        Instant rangeStart = from == null ? TIME_OFF_LIST_MIN : from;
        Instant rangeEnd = to == null ? TIME_OFF_LIST_MAX : to;
        if (!rangeEnd.isAfter(rangeStart)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        List<CaregiverTimeOff> timeOffs = caregiverTimeOffRepository.findOverlapping(caregiverId, rangeStart, rangeEnd);
        List<TimeOffResponse> timeOffResponses = caregiverMapper.toTimeOffResponseList(timeOffs);
        return APIResponse.success("Lấy danh sách ngày nghỉ thành công", timeOffResponses);
    }

    /**
     * Không đặt @Transactional: kiểm phạm vi (transaction ngắn) → caregiver tự khai báo thì hỏi Booking có buổi đã giao
     * trùng khoảng nghỉ không (C5.1, NGOÀI transaction) → ghi trong transaction có khóa hồ sơ. Manager được tạo dù trùng
     * buổi (PRV-02 quy tắc 3) — họ phải tự đổi người cho các buổi bị ảnh hưởng.
     */
    @Override
    public APIResponse<TimeOffResponse> createTimeOff(UUID actorId, UUID caregiverId, TimeOffRequest request) {
        if (!request.getEndAt().isAfter(request.getStartAt())
                || Duration.between(request.getStartAt(), request.getEndAt()).compareTo(MAX_TIME_OFF) > 0) {
            throw new AppException(ErrorCode.TIME_OFF_RANGE_INVALID);
        }
        boolean requestedByCaregiver = Boolean.TRUE.equals(
                transactionTemplate.execute(status -> checkTimeOffAccess(actorId, caregiverId)));
        if (requestedByCaregiver) {
            TimeWindow timeOffWindow = new TimeWindow(request.getStartAt(), request.getEndAt());
            Set<UUID> busyCaregiverIds = bookingAvailabilityService.findBusyCaregiverIds(
                    List.of(caregiverId), List.of(timeOffWindow));
            if (busyCaregiverIds.contains(caregiverId)) {
                throw new AppException(ErrorCode.TIME_OFF_HAS_BOOKINGS);
            }
        }
        TimeOffResponse timeOffResponse = transactionTemplate.execute(
                status -> saveTimeOff(actorId, caregiverId, request));
        return APIResponse.success("Tạo ngày nghỉ thành công", timeOffResponse);
    }

    @Override
    @Transactional
    public APIResponse<Void> deleteTimeOff(UUID actorId, UUID caregiverId, UUID timeOffId) {
        CaregiverProfile caregiver = findActiveCaregiver(caregiverId);
        providerAuthorization.requireCaregiverSelfOrCenterManagement(caregiver, actorId);
        CaregiverTimeOff timeOff = caregiverTimeOffRepository
                .findByIdAndCaregiverIdAndDeletedAtIsNull(timeOffId, caregiverId)
                .orElseThrow(() -> new AppException(ErrorCode.TIME_OFF_NOT_FOUND));
        timeOff.setDeletedAt(clock.instant());
        caregiverTimeOffRepository.save(timeOff);
        log.info("Actor {} deleted time-off {} of caregiver {}", actorId, timeOffId, caregiverId);
        return APIResponse.success("Xóa ngày nghỉ thành công");
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<AvailabilityWindowResponse>> getAvailability(UUID caregiverId, Instant from, Instant to) {
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(MAX_AVAILABILITY_RANGE) > 0) {
            throw new AppException(ErrorCode.AVAILABILITY_RANGE_TOO_LONG);
        }
        findActiveCaregiver(caregiverId);
        List<UUID> caregiverIds = List.of(caregiverId);
        List<TimeWindow> scheduleWindows = loadScheduleWindows(caregiverIds, from, to)
                .getOrDefault(caregiverId, List.of());
        List<TimeWindow> timeOffWindows = loadTimeOffWindows(caregiverIds, from, to)
                .getOrDefault(caregiverId, List.of());
        List<TimeWindow> availableWindows = TimeWindows.clip(
                TimeWindows.subtract(scheduleWindows, timeOffWindows), from, to);
        List<AvailabilityWindowResponse> windowResponses = caregiverMapper.toAvailabilityWindowResponseList(
                availableWindows);
        return APIResponse.success("Lấy lịch rảnh của người chăm sóc thành công", windowResponses);
    }

    /**
     * C5.2 cho BookingService: mỗi slot phải nằm trọn trong hợp các lịch SCHEDULED (giờ VN, ca liền kề được ghép) và
     * không giao ngày nghỉ nào. Không xét buổi đã đặt — Booking tự kiểm trùng buổi.
     */
    @Override
    @Transactional(readOnly = true)
    public APIResponse<AvailabilityCheckResponse> checkAvailability(AvailabilityCheckRequest request) {
        List<TimeWindow> slots = caregiverMapper.toTimeWindowList(request.getSlots());
        validateSlots(slots);
        UUID caregiverId = request.getCaregiverId();
        findActiveCaregiver(caregiverId);
        Instant from = findEarliestStart(slots);
        Instant to = findLatestEnd(slots);
        List<UUID> caregiverIds = List.of(caregiverId);
        List<TimeWindow> scheduleWindows = loadScheduleWindows(caregiverIds, from, to)
                .getOrDefault(caregiverId, List.of());
        List<TimeWindow> timeOffWindows = loadTimeOffWindows(caregiverIds, from, to)
                .getOrDefault(caregiverId, List.of());
        List<AvailabilityViolationResponse> violations = new ArrayList<>();
        for (TimeWindow slot : slots) {
            AvailabilityViolationReason reason = findViolation(slot, scheduleWindows, timeOffWindows);
            if (reason != null) {
                violations.add(caregiverMapper.toAvailabilityViolationResponse(slot, reason));
            }
        }
        AvailabilityCheckResponse availabilityCheckResponse = caregiverMapper.toAvailabilityCheckResponse(
                violations.isEmpty(), violations);
        return APIResponse.success("Kiểm tra lịch rảnh thành công", availabilityCheckResponse);
    }

    @Override
    public CaregiverProfile findActiveCaregiverByUser(UUID userId) {
        return caregiverProfileRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_NOT_FOUND));
    }

    @Override
    public CaregiverProfile findActiveCaregiver(UUID id) {
        return caregiverProfileRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_NOT_FOUND));
    }

    // Chạy trong transactionTemplate của create — không gọi mạng ở đây.
    private CaregiverResponse createProfile(UUID actorId, UUID centerId, CaregiverCreateRequest request) {
        CareCenter center = findActiveCenter(centerId);
        providerAuthorization.requireCenterManagement(center, actorId);
        if (center.getCenterStatus() != CenterStatus.ACTIVE) {
            throw new AppException(ErrorCode.CENTER_NOT_ACTIVE);
        }
        if (caregiverProfileRepository.existsByUserIdAndDeletedAtIsNull(request.getUserId())) {
            throw new AppException(ErrorCode.CAREGIVER_USER_EXISTED);
        }
        CaregiverProfile caregiver = caregiverMapper.toCaregiverProfile(request, center);
        normalizeFields(caregiver);
        // Hồ sơ mới chưa có đánh giá, chờ xác minh và chưa được nhận ca.
        caregiver.setRatingAverage(BigDecimal.ZERO);
        caregiver.setRatingCount(0);
        caregiver.setVerificationStatus(VerificationStatus.PENDING);
        caregiver.setCaregiverStatus(CaregiverStatus.INACTIVE);
        caregiver.setCreatedBy(actorId);
        caregiver.setUpdatedBy(actorId);
        CaregiverProfile savedCaregiver = caregiverProfileRepository.save(caregiver);
        log.info("Actor {} created caregiver profile {} in center {}", actorId, savedCaregiver.getId(), centerId);
        return caregiverMapper.toResponse(savedCaregiver);
    }

    // Chạy trong transaction đọc: center là quan hệ LAZY, cần session để đọc managerUserId.
    private UUID findManagedCaregiverCenterId(UUID actorId, UUID caregiverId) {
        CaregiverProfile caregiver = findActiveCaregiver(caregiverId);
        providerAuthorization.requireCenterManagement(caregiver.getCenter(), actorId);
        return caregiver.getCenter().getId();
    }

    // Gọi mạng — chỉ chạy ngoài transaction. Kiểm trung tâm trước để offering của trung tâm khác luôn là 400.
    private CenterServiceClientResponse fetchAssignableCenterService(UUID centerServiceId, UUID centerId) {
        RemoteApiResponse<CenterServiceClientResponse> response = careCatalogClient.getCenterService(centerServiceId);
        CenterServiceClientResponse centerService = response == null ? null : response.getResult();
        if (centerService == null || centerService.getCatalogItem() == null) {
            log.warn("CareCatalogService returned an empty center service {}", centerServiceId);
            throw new AppException(ErrorCode.CATALOG_UNAVAILABLE);
        }
        if (!centerId.equals(centerService.getCenterId())) {
            throw new AppException(ErrorCode.CAREGIVER_SERVICE_NOT_IN_CENTER);
        }
        if (!ACTIVE_STATUS.equals(centerService.getStatus())
                || !ACTIVE_STATUS.equals(centerService.getCatalogItem().getStatus())) {
            throw new AppException(ErrorCode.CAREGIVER_SERVICE_NOT_ACTIVE);
        }
        return centerService;
    }

    // Chạy trong transactionTemplate của replaceServices — không gọi mạng ở đây.
    private List<CaregiverSkillResponse> replaceSkills(UUID actorId, UUID caregiverId, UUID centerId,
                                                       List<CenterServiceClientResponse> centerServices) {
        CaregiverProfile caregiver = caregiverProfileRepository.findForUpdate(caregiverId)
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_NOT_FOUND));
        // Kiểm lại sau khi khóa: hồ sơ có thể vừa bị lưu trữ / đổi quản lý trong lúc gọi Catalog.
        providerAuthorization.requireCenterManagement(caregiver.getCenter(), actorId);
        if (!centerId.equals(caregiver.getCenter().getId())) {
            throw new AppException(ErrorCode.CAREGIVER_SERVICE_NOT_IN_CENTER);
        }
        List<CaregiverSkill> currentSkills = caregiverSkillRepository.findByIdCaregiverIdOrderByAssignedAtAsc(caregiverId);
        Set<UUID> requestedIds = new LinkedHashSet<>();
        for (CenterServiceClientResponse centerService : centerServices) {
            requestedIds.add(centerService.getId());
        }
        Set<UUID> keptIds = new LinkedHashSet<>();
        List<CaregiverSkill> removedSkills = new ArrayList<>();
        for (CaregiverSkill skill : currentSkills) {
            if (requestedIds.contains(skill.getId().getCenterServiceId())) {
                keptIds.add(skill.getId().getCenterServiceId());
            } else {
                removedSkills.add(skill);
            }
        }
        Instant now = clock.instant();
        List<CaregiverSkill> addedSkills = new ArrayList<>();
        for (CenterServiceClientResponse centerService : centerServices) {
            if (!keptIds.contains(centerService.getId())) {
                addedSkills.add(caregiverMapper.toCaregiverSkill(caregiver, centerService, actorId, now));
            }
        }
        caregiverSkillRepository.deleteAll(removedSkills);
        caregiverSkillRepository.saveAll(addedSkills);
        caregiverSkillRepository.flush();
        log.info("Actor {} replaced services of caregiver {}: {} added, {} removed, {} kept",
                actorId, caregiverId, addedSkills.size(), removedSkills.size(), keptIds.size());
        List<CaregiverSkill> savedSkills = caregiverSkillRepository.findByIdCaregiverIdOrderByAssignedAtAsc(caregiverId);
        return caregiverMapper.toSkillResponseList(savedSkills);
    }

    // Chạy trong transactionTemplate của createTimeOff. true = chính caregiver khai báo (phải kiểm buổi đã giao).
    private boolean checkTimeOffAccess(UUID actorId, UUID caregiverId) {
        CaregiverProfile caregiver = findActiveCaregiver(caregiverId);
        providerAuthorization.requireCaregiverSelfOrCenterManagement(caregiver, actorId);
        return actorId.equals(caregiver.getUserId());
    }

    // Chạy trong transactionTemplate của createTimeOff — không gọi mạng ở đây.
    private TimeOffResponse saveTimeOff(UUID actorId, UUID caregiverId, TimeOffRequest request) {
        // Khóa hồ sơ: hai yêu cầu nghỉ song song của cùng caregiver không cùng lọt qua bước kiểm chồng lấn.
        CaregiverProfile caregiver = caregiverProfileRepository.findForUpdate(caregiverId)
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_NOT_FOUND));
        providerAuthorization.requireCaregiverSelfOrCenterManagement(caregiver, actorId);
        if (caregiverTimeOffRepository.existsOverlapping(caregiverId, request.getStartAt(), request.getEndAt())) {
            throw new AppException(ErrorCode.TIME_OFF_OVERLAP);
        }
        CaregiverTimeOff timeOff = caregiverMapper.toCaregiverTimeOff(request, caregiver, actorId);
        timeOff.setReason(normalize(timeOff.getReason()));
        CaregiverTimeOff savedTimeOff = caregiverTimeOffRepository.save(timeOff);
        log.info("Actor {} created time-off {} for caregiver {}", actorId, savedTimeOff.getId(), caregiverId);
        return caregiverMapper.toTimeOffResponse(savedTimeOff);
    }

    /**
     * Lịch rảnh của từng caregiver giao [from, to): lịch SCHEDULED, quy đổi giờ VN → Instant rồi gộp ca liền kề.
     * Ca không qua đêm (end_time > start_time) nên chỉ cần các ngày làm việc từ ngày của from tới ngày của to.
     */
    private Map<UUID, List<TimeWindow>> loadScheduleWindows(Collection<UUID> caregiverIds, Instant from, Instant to) {
        LocalDate fromDate = LocalDate.ofInstant(from, ProviderTime.ZONE);
        LocalDate toDate = LocalDate.ofInstant(to, ProviderTime.ZONE);
        List<CaregiverSchedule> schedules = caregiverScheduleRepository.findByCaregiversAndWorkDateRange(
                caregiverIds, ScheduleStatus.SCHEDULED, fromDate, toDate);
        Map<UUID, List<TimeWindow>> windowsByCaregiver = new HashMap<>();
        for (CaregiverSchedule schedule : schedules) {
            WorkShift shift = schedule.getWorkShift();
            TimeWindow window = new TimeWindow(ProviderTime.toInstant(shift.getWorkDate(), shift.getStartTime()),
                    ProviderTime.toInstant(shift.getWorkDate(), shift.getEndTime()));
            windowsByCaregiver.computeIfAbsent(schedule.getCaregiver().getId(), id -> new ArrayList<>()).add(window);
        }
        Map<UUID, List<TimeWindow>> mergedByCaregiver = new HashMap<>();
        for (Map.Entry<UUID, List<TimeWindow>> entry : windowsByCaregiver.entrySet()) {
            mergedByCaregiver.put(entry.getKey(), TimeWindows.merge(entry.getValue()));
        }
        return mergedByCaregiver;
    }

    private Map<UUID, List<TimeWindow>> loadTimeOffWindows(Collection<UUID> caregiverIds, Instant from, Instant to) {
        List<CaregiverTimeOff> timeOffs = caregiverTimeOffRepository.findOverlappingForCaregivers(caregiverIds, from, to);
        Map<UUID, List<TimeWindow>> windowsByCaregiver = new HashMap<>();
        for (CaregiverTimeOff timeOff : timeOffs) {
            windowsByCaregiver.computeIfAbsent(timeOff.getCaregiver().getId(), id -> new ArrayList<>())
                    .add(new TimeWindow(timeOff.getStartAt(), timeOff.getEndAt()));
        }
        return windowsByCaregiver;
    }

    // Ngày nghỉ ưu tiên hơn "ngoài lịch": bên gọi cần biết caregiver nghỉ, không chỉ "chưa có ca".
    private AvailabilityViolationReason findViolation(TimeWindow slot, List<TimeWindow> scheduleWindows,
                                                      List<TimeWindow> timeOffWindows) {
        if (TimeWindows.overlapsAny(timeOffWindows, slot)) {
            return AvailabilityViolationReason.TIME_OFF;
        }
        if (!TimeWindows.covers(scheduleWindows, slot)) {
            return AvailabilityViolationReason.NO_SCHEDULE;
        }
        return null;
    }

    private void validateSlots(List<TimeWindow> slots) {
        if (slots.isEmpty() || slots.size() > MAX_SLOTS) {
            throw new AppException(ErrorCode.SLOTS_INVALID);
        }
        for (TimeWindow slot : slots) {
            if (slot.startAt() == null || slot.endAt() == null || !slot.endAt().isAfter(slot.startAt())) {
                throw new AppException(ErrorCode.SLOT_RANGE_INVALID);
            }
        }
    }

    private Instant findEarliestStart(List<TimeWindow> slots) {
        return slots.stream()
                .map(TimeWindow::startAt)
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new AppException(ErrorCode.SLOTS_INVALID));
    }

    private Instant findLatestEnd(List<TimeWindow> slots) {
        return slots.stream()
                .map(TimeWindow::endAt)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new AppException(ErrorCode.SLOTS_INVALID));
    }

    // slotStart/slotEnd (một cặp) + slots "<startISO>/<endISO>" lặp lại; không có khung nào → tìm không xét lịch.
    private List<TimeWindow> parseSearchSlots(CaregiverSearchRequest request) {
        List<TimeWindow> slots = new ArrayList<>();
        if (request.getSlotStart() != null || request.getSlotEnd() != null) {
            slots.add(new TimeWindow(request.getSlotStart(), request.getSlotEnd()));
        }
        if (request.getSlots() != null) {
            for (String slot : request.getSlots()) {
                slots.add(parseSlot(slot));
            }
        }
        if (!slots.isEmpty()) {
            validateSlots(slots);
        }
        return slots;
    }

    private TimeWindow parseSlot(String slot) {
        String[] parts = slot == null ? new String[0] : slot.split(SLOT_SEPARATOR);
        if (parts.length != 2) {
            throw new AppException(ErrorCode.SLOT_RANGE_INVALID);
        }
        try {
            return new TimeWindow(Instant.parse(parts[0].trim()), Instant.parse(parts[1].trim()));
        } catch (DateTimeParseException exception) {
            throw new AppException(ErrorCode.SLOT_RANGE_INVALID);
        }
    }

    // Chạy trong transactionTemplate của search: bước (a) điều kiện tĩnh + (b1) lịch rảnh / ngày nghỉ + sắp xếp.
    private List<UUID> findSearchCandidateIds(CaregiverSearchRequest request, List<TimeWindow> slots) {
        CareCenter center = findActiveCenter(request.getCenterId());
        if (center.getCenterStatus() != CenterStatus.ACTIVE) {
            throw new AppException(ErrorCode.CENTER_NOT_ACTIVE);
        }
        Set<UUID> careServiceIds = new LinkedHashSet<>(request.getCareServiceIds());
        String keyword = normalize(request.getKeyword());
        String keywordPattern = keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%";
        List<CaregiverProfile> candidates = caregiverProfileRepository.searchCandidates(center.getId(),
                VerificationStatus.VERIFIED, CaregiverStatus.ACTIVE, careServiceIds, careServiceIds.size(),
                request.getGender(), request.getMinExperienceYears(), request.getMinRating(), keywordPattern,
                PageRequest.of(0, MAX_SEARCH_CANDIDATES + 1));
        if (candidates.size() > MAX_SEARCH_CANDIDATES) {
            throw new AppException(ErrorCode.SEARCH_TOO_BROAD);
        }
        List<CaregiverProfile> availableCandidates = slots.isEmpty()
                ? candidates : filterByProviderAvailability(candidates, slots);
        List<CaregiverProfile> sortedCandidates = new ArrayList<>(availableCandidates);
        sortedCandidates.sort(buildSearchComparator(request.getSort()));
        return sortedCandidates.stream()
                .map(CaregiverProfile::getId)
                .toList();
    }

    private List<CaregiverProfile> filterByProviderAvailability(List<CaregiverProfile> candidates,
                                                                List<TimeWindow> slots) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        List<UUID> caregiverIds = candidates.stream()
                .map(CaregiverProfile::getId)
                .toList();
        Instant from = findEarliestStart(slots);
        Instant to = findLatestEnd(slots);
        Map<UUID, List<TimeWindow>> scheduleWindows = loadScheduleWindows(caregiverIds, from, to);
        Map<UUID, List<TimeWindow>> timeOffWindows = loadTimeOffWindows(caregiverIds, from, to);
        List<CaregiverProfile> availableCandidates = new ArrayList<>();
        for (CaregiverProfile candidate : candidates) {
            List<TimeWindow> schedules = scheduleWindows.getOrDefault(candidate.getId(), List.of());
            List<TimeWindow> timeOffs = timeOffWindows.getOrDefault(candidate.getId(), List.of());
            boolean availableForAllSlots = slots.stream()
                    .allMatch(slot -> findViolation(slot, schedules, timeOffs) == null);
            if (availableForAllSlots) {
                availableCandidates.add(candidate);
            }
        }
        return availableCandidates;
    }

    // Khóa phụ cuối cùng là id để thứ tự ổn định giữa các trang.
    private Comparator<CaregiverProfile> buildSearchComparator(CaregiverSearchSort sort) {
        Comparator<CaregiverProfile> byRating = Comparator.comparing(CaregiverProfile::getRatingAverage).reversed()
                .thenComparing(Comparator.comparing(CaregiverProfile::getRatingCount).reversed());
        Comparator<CaregiverProfile> byExperience = Comparator.comparing(CaregiverProfile::getExperienceYears)
                .reversed();
        Comparator<CaregiverProfile> byName = Comparator.comparing(
                caregiver -> caregiver.getFullName().toLowerCase(Locale.ROOT));
        Comparator<CaregiverProfile> comparator;
        if (sort == CaregiverSearchSort.EXPERIENCE_DESC) {
            comparator = byExperience.thenComparing(byRating);
        } else if (sort == CaregiverSearchSort.NAME_ASC) {
            comparator = byName;
        } else {
            comparator = byRating.thenComparing(byExperience);
        }
        return comparator.thenComparing(CaregiverProfile::getId);
    }

    // Chạy trong transactionTemplate của search: kỹ năng là quan hệ LAZY, cần session khi map.
    private List<CaregiverCardResponse> buildCards(List<UUID> pageIds, Map<UUID, List<String>> avatarUrls) {
        if (pageIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, CaregiverProfile> caregiversById = new HashMap<>();
        for (CaregiverProfile caregiver : caregiverProfileRepository.findByIdInAndDeletedAtIsNull(pageIds)) {
            caregiversById.put(caregiver.getId(), caregiver);
        }
        Map<UUID, Long> certificateCounts = new HashMap<>();
        for (CaregiverCertificateCount count : certificateRepository.countByCaregiverIds(pageIds,
                CertificateVerificationStatus.ADMIN_VERIFIED, LocalDate.now(clock.withZone(ProviderTime.ZONE)))) {
            certificateCounts.put(count.getCaregiverId(), count.getCertificateCount());
        }
        List<CaregiverCardResponse> cards = new ArrayList<>();
        for (UUID caregiverId : pageIds) {
            CaregiverProfile caregiver = caregiversById.get(caregiverId);
            // Hồ sơ vừa bị lưu trữ giữa hai bước — bỏ qua thay vì trả thẻ rỗng.
            if (caregiver != null) {
                cards.add(caregiverMapper.toCardResponse(caregiver, firstUrl(avatarUrls.get(caregiverId)),
                        certificateCounts.getOrDefault(caregiverId, 0L)));
            }
        }
        return cards;
    }

    // Chạy trong transactionTemplate của getPublic. Hồ sơ chưa công khai (chưa duyệt / ngừng / trung tâm không ACTIVE)
    // trả 404 như không tồn tại — không lộ trạng thái nội bộ.
    private CaregiverPublicResponse buildPublicProfile(UUID caregiverId, String avatarUrl) {
        CaregiverProfile caregiver = findActiveCaregiver(caregiverId);
        boolean visible = caregiver.getVerificationStatus() == VerificationStatus.VERIFIED
                && caregiver.getCaregiverStatus() == CaregiverStatus.ACTIVE
                && caregiver.getCenter().getCenterStatus() == CenterStatus.ACTIVE
                && caregiver.getCenter().getDeletedAt() == null;
        if (!visible) {
            throw new AppException(ErrorCode.CAREGIVER_NOT_FOUND);
        }
        // VERIFIED = đã được ADMIN duyệt (CertificateController.verify chỉ cho ADMIN) — PENDING/REJECTED không hiện.
        List<Certificate> certificates = certificateRepository
                .findByCaregiverIdAndVerificationStatusAndDeletedAtIsNullOrderByIssuedDateDesc(
                        caregiverId, CertificateVerificationStatus.ADMIN_VERIFIED).stream()
                        .filter(certificate -> certificate.getExpiryDate() == null
                                || !certificate.getExpiryDate().isBefore(LocalDate.now(clock.withZone(ProviderTime.ZONE))))
                        .toList();
        return caregiverMapper.toPublicResponse(caregiver, certificates, avatarUrl);
    }

    private String firstUrl(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        return urls.getFirst();
    }


    private CareCenter findActiveCenter(UUID centerId) {
        return careCenterRepository.findByIdAndDeletedAtIsNull(centerId)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
    }

    private void normalizeFields(CaregiverProfile caregiver) {
        caregiver.setFullName(caregiver.getFullName().trim());
        caregiver.setBio(normalize(caregiver.getBio()));
    }

    private void checkVersion(CaregiverProfile caregiver, Long version) {
        if (version == null || !version.equals(caregiver.getVersion())) {
            throw new AppException(ErrorCode.CAREGIVER_VERSION_CONFLICT);
        }
    }

    @Override
    @Transactional
    public APIResponse<Void> updateRating(UUID caregiverId, CaregiverRatingRequest request) {
        CaregiverProfile caregiver = caregiverProfileRepository.findForUpdate(caregiverId)
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_NOT_FOUND));
        caregiver.setRatingAverage(request.getRatingAverage());
        caregiver.setRatingCount(request.getRatingCount());
        return APIResponse.success("Cập nhật điểm đánh giá thành công");
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CaregiverResponse>> listPendingVerification(UUID centerId, int page, int size) {
        validatePage(page, size);
        Page<CaregiverProfile> profiles = caregiverProfileRepository.findAll((root, query, builder) -> {
            Predicate predicate = builder.and(builder.isNull(root.get("deletedAt")),
                    builder.equal(root.get("verificationStatus"), VerificationStatus.PENDING));
            return centerId == null ? predicate : builder.and(predicate, builder.equal(root.get("center").get("id"), centerId));
        }, PageRequest.of(page, size));
        PageResponse<CaregiverResponse> response = PageResponse.of(profiles, caregiverMapper.toResponseList(profiles.getContent()));
        return APIResponse.success("Lấy hồ sơ chờ xác minh thành công", response);
    }

    private void checkReadScope(CaregiverProfile caregiver, UUID actorId) {
        if (providerAuthorization.isAdmin() || providerAuthorization.hasRole(RoleCode.SERVICE)
                || (providerAuthorization.hasRole(RoleCode.CAREGIVER) && actorId.equals(caregiver.getUserId()))
                || (providerAuthorization.hasRole(RoleCode.CENTER_MANAGER)
                    && actorId.equals(caregiver.getCenter().getManagerUserId()))) {
            return;
        }
        throw new AppException(ErrorCode.CAREGIVER_NOT_FOUND);
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
