package mss301.providerservice.service.Impl;

import mss301.providerservice.client.dto.IdentityUserResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.client.CareCatalogClient;
import mss301.providerservice.client.dto.CenterPriceSummaryClientResponse;
import mss301.providerservice.client.dto.RemoteApiResponse;
import mss301.providerservice.dto.request.CenterCreateRequest;
import mss301.providerservice.dto.request.CenterStatusUpdateRequest;
import mss301.providerservice.dto.request.CenterUpdateRequest;
import mss301.providerservice.dto.request.CenterSelfUpdateRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.ProviderStatisticsResponse;
import mss301.providerservice.dto.response.CenterCardResponse;
import mss301.providerservice.dto.response.CenterResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.MediaOwnerType;
import mss301.providerservice.enums.RoleCode;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.CareCenterMapper;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.util.CareCenterSpecifications;
import mss301.providerservice.service.CareCenterService;
import mss301.providerservice.service.IdentityAccountService;
import mss301.providerservice.service.ProviderMediaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import mss301.providerservice.security.ProviderAuthorization;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import mss301.providerservice.repository.StatisticsCountView;
import java.util.LinkedHashMap;
import mss301.providerservice.service.ProviderOutboxService;
import mss301.providerservice.client.BookingArchiveClient;
import mss301.providerservice.client.dto.BookingCountResponse;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CareCenterServiceImpl implements CareCenterService {

    static int MAX_PAGE_SIZE = 100;
    static int CARD_IMAGE_LIMIT = 3;
    static int DETAIL_IMAGE_LIMIT = 10;

    CareCenterRepository careCenterRepository;
    ProviderAuthorization providerAuthorization;
    CareCenterMapper careCenterMapper;
    IdentityAccountService identityAccountService;
    TransactionTemplate transactionTemplate;
    CareCatalogClient careCatalogClient;
    ProviderMediaService providerMediaService;
    ProviderOutboxService providerOutboxService;
    BookingArchiveClient bookingArchiveClient;

    /**
     * Không đặt @Transactional: kiểm tài khoản manager ở IdentityService là lời gọi mạng nên chạy TRƯỚC, ngoài
     * transaction; phần ghi DB chạy trong transactionTemplate (rule #24).
     */
    @Override
    public APIResponse<CenterResponse> create(UUID actorId, CenterCreateRequest request) {
        // Trung tâm chưa có id nên tài khoản manager (đã gắn một trung tâm ở Identity) không thể khớp — gán sau khi tạo.
        checkManagerAccount(request.getManagerUserId(), null);
        CenterResponse response = transactionTemplate.execute(status -> createCenter(actorId, request));
        return APIResponse.success("Tạo trung tâm chăm sóc thành công", response);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CenterResponse>> list(CenterStatus status, String keyword,
                                                           boolean includeDeleted, int page, int size) {
        validatePage(page, size);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<CareCenter> specification = CareCenterSpecifications.filter(status, keyword,
                providerAuthorization.isAdmin() && includeDeleted);
        if (!providerAuthorization.isAdmin()) {
            UUID actorId = providerAuthorization.currentUserId();
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("managerUserId"), actorId));
        }
        Page<CareCenter> centerPage = careCenterRepository.findAll(specification, pageRequest);
        List<CenterResponse> items = careCenterMapper.toResponseList(centerPage.getContent());
        PageResponse<CenterResponse> result = PageResponse.of(centerPage, items);
        return APIResponse.success("Lấy danh sách trung tâm chăm sóc thành công", result);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CenterResponse> get(UUID id) {
        CareCenter careCenter = findActiveCenter(id);
        // L-07: khách chỉ xem được trung tâm đang hoạt động (giống /public/{id}); trung tâm chờ duyệt / khóa → 404.
        if (careCenter.getCenterStatus() != CenterStatus.ACTIVE && providerAuthorization.hasRole(RoleCode.CUSTOMER)
                && !providerAuthorization.isAdmin()
                && !providerAuthorization.currentUserId().equals(careCenter.getManagerUserId())) {
            throw new AppException(ErrorCode.CENTER_NOT_FOUND);
        }
        CenterResponse response = careCenterMapper.toResponse(careCenter);
        if (!providerAuthorization.isAdmin() && !providerAuthorization.hasRole(RoleCode.SERVICE)
                && !providerAuthorization.currentUserId().equals(careCenter.getManagerUserId())) {
            response.setEmail(null);
            response.setManagerUserId(null);
            response.setCreatedBy(null);
            response.setUpdatedBy(null);
            response.setStatusReason(null);
        }
        return APIResponse.success("Lấy thông tin trung tâm chăm sóc thành công", response);
    }

    /**
     * SRCH-02. Không đặt @Transactional: Catalog (C9.2) và Media là lời gọi mạng (rule #24); phần DB chỉ là một query
     * đọc. Có lọc dịch vụ / giá → Catalog là bắt buộc (lỗi → 503, không trả danh sách sai); không lọc → giá chỉ để
     * trang trí, Catalog lỗi thì minPrice = null.
     */
    @Override
    public APIResponse<PageResponse<CenterCardResponse>> listPublic(String keyword, UUID careServiceId,
                                                                    BigDecimal minPrice, BigDecimal maxPrice,
                                                                    int page, int size) {
        validatePage(page, size);
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new AppException(ErrorCode.PRICE_RANGE_INVALID);
        }
        boolean filterByCatalog = careServiceId != null || minPrice != null || maxPrice != null;
        Map<UUID, CenterPriceSummaryClientResponse> priceSummaries = filterByCatalog
                ? fetchPriceSummaries(careServiceId, minPrice, maxPrice)
                : fetchPriceSummariesQuietly();
        String normalizedKeyword = normalizeNullable(keyword);
        String keywordPattern = normalizedKeyword == null
                ? null : "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%";
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "centerName"));
        Page<CareCenter> centerPage;
        if (!filterByCatalog) {
            centerPage = careCenterRepository.searchPublic(CenterStatus.ACTIVE, keywordPattern, pageRequest);
        } else if (priceSummaries.isEmpty()) {
            centerPage = Page.empty(pageRequest);
        } else {
            centerPage = careCenterRepository.searchPublicByIds(CenterStatus.ACTIVE, priceSummaries.keySet(),
                    keywordPattern, pageRequest);
        }
        List<UUID> centerIds = centerPage.stream()
                .map(CareCenter::getId)
                .toList();
        Map<UUID, List<String>> imageUrls = providerMediaService.findImageUrls(
                MediaOwnerType.CARE_CENTER, centerIds, CARD_IMAGE_LIMIT);
        List<CenterCardResponse> items = centerPage.stream()
                .map(center -> careCenterMapper.toCardResponse(center, priceSummaries.get(center.getId()),
                        imageUrls.getOrDefault(center.getId(), List.of())))
                .toList();
        PageResponse<CenterCardResponse> pageResponse = PageResponse.of(centerPage, items);
        return APIResponse.success("Lấy danh sách trung tâm đang hoạt động thành công", pageResponse);
    }

    /** Như listPublic: đọc DB trước, rồi Catalog / Media (best-effort) ngoài transaction. Chỉ trung tâm ACTIVE. */
    @Override
    public APIResponse<CenterCardResponse> getPublic(UUID centerId) {
        CareCenter careCenter = careCenterRepository.findByIdAndCenterStatusAndDeletedAtIsNull(centerId, CenterStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        Map<UUID, CenterPriceSummaryClientResponse> priceSummaries = fetchPriceSummariesQuietly();
        Map<UUID, List<String>> imageUrls = providerMediaService.findImageUrls(
                MediaOwnerType.CARE_CENTER, List.of(centerId), DETAIL_IMAGE_LIMIT);
        CenterCardResponse centerCardResponse = careCenterMapper.toCardResponse(careCenter,
                priceSummaries.get(centerId), imageUrls.getOrDefault(centerId, List.of()));
        return APIResponse.success("Lấy thông tin trung tâm thành công", centerCardResponse);
    }

    /** Như create: kiểm manager ngoài transaction rồi mới ghi DB. */
    @Override
    public APIResponse<CenterResponse> update(UUID actorId, UUID id, CenterUpdateRequest request) {
        checkManagerAccount(request.getManagerUserId(), id);
        CenterResponse response = transactionTemplate.execute(status -> updateCenter(actorId, id, request));
        return APIResponse.success("Cập nhật trung tâm chăm sóc thành công", response);
    }

    @Override
    public APIResponse<CenterResponse> updateStatus(UUID actorId, UUID id, CenterStatusUpdateRequest request) {
        CareCenter snapshot = findActiveCenter(id);
        checkVersion(snapshot, request.getVersion());
        if (request.getStatus() == CenterStatus.ACTIVE && snapshot.getCenterStatus() == CenterStatus.PENDING) {
            if (snapshot.getManagerUserId() == null) {
                throw new AppException(ErrorCode.CENTER_MANAGER_REQUIRED);
            }
            checkManagerAccount(snapshot.getManagerUserId(), id);
        }
        // M-06: ngưng hoạt động (INACTIVE) không hủy hộ booking như SUSPENDED → chỉ cho khi không còn booking chưa kết thúc.
        if (request.getStatus() == CenterStatus.ARCHIVED || request.getStatus() == CenterStatus.INACTIVE) {
            checkNoActiveBookings(id);
        }
        CenterResponse response = transactionTemplate.execute(status -> changeStatus(actorId, id, request));
        return APIResponse.success("Cập nhật trạng thái trung tâm thành công", response);
    }

    @Override
    public APIResponse<Void> delete(UUID actorId, UUID id, Long version) {
        checkNoActiveBookings(id);
        transactionTemplate.executeWithoutResult(status -> {
            CenterStatusUpdateRequest request = careCenterMapper.toStatusRequest(CenterStatus.ARCHIVED, "Lưu trữ bởi admin", version);
            changeStatus(actorId, id, request);
        });
        return APIResponse.success("Lưu trữ trung tâm thành công");
    }

    @Override
    @Transactional
    public APIResponse<CenterResponse> updateMine(UUID actorId, CenterSelfUpdateRequest request) {
        CareCenter found = careCenterRepository.findByManagerUserIdAndDeletedAtIsNull(actorId)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        CareCenter center = careCenterRepository.findForUpdate(found.getId())
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        if (center.getCenterStatus() == CenterStatus.ARCHIVED || center.getCenterStatus() == CenterStatus.REJECTED) {
            throw new AppException(ErrorCode.CENTER_INVALID_TRANSITION);
        }
        if (request.getCenterName() != null && request.getCenterName().isBlank()) {
            throw new AppException(ErrorCode.CENTER_NAME_REQUIRED);
        }
        careCenterMapper.update(request, center);
        center.setUpdatedBy(actorId);
        CenterResponse response = careCenterMapper.toResponse(careCenterRepository.saveAndFlush(center));
        return APIResponse.success("Cập nhật trung tâm thành công", response);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<ProviderStatisticsResponse> statistics() {
        ProviderStatisticsResponse response = careCenterMapper.toStatisticsResponse(
                toStatisticsCounts(careCenterRepository.countCentersByStatus()),
                toStatisticsCounts(careCenterRepository.countCaregiversByStatus()),
                toStatisticsCounts(careCenterRepository.countCaregiversByVerification()));
        return APIResponse.success("Lấy thống kê nhà cung cấp thành công", response);
    }

    private Map<String, Long> toStatisticsCounts(List<StatisticsCountView> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        rows.forEach(row -> counts.put(row.getCategory(), row.getCount()));
        return counts;
    }

    private CenterResponse changeStatus(UUID actorId, UUID id, CenterStatusUpdateRequest request) {
        CareCenter center = careCenterRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        checkVersion(center, request.getVersion());
        validateStatusReason(request);
        CenterStatus from = center.getCenterStatus();
        CenterStatus to = request.getStatus();
        boolean allowed = to == CenterStatus.ARCHIVED || switch (from) {
            case PENDING -> to == CenterStatus.ACTIVE || to == CenterStatus.REJECTED;
            case ACTIVE -> to == CenterStatus.SUSPENDED || to == CenterStatus.INACTIVE;
            case SUSPENDED, INACTIVE -> to == CenterStatus.ACTIVE;
            default -> false;
        };
        if (!allowed || from == CenterStatus.ARCHIVED) {
            throw new AppException(ErrorCode.CENTER_INVALID_TRANSITION);
        }
        center.setCenterStatus(to);
        center.setStatusReason(normalizeNullable(request.getReason()));
        center.setUpdatedBy(actorId);
        if (to == CenterStatus.ARCHIVED) {
            center.setDeletedAt(Instant.now());
        }
        careCenterRepository.saveAndFlush(center);
        if (to == CenterStatus.SUSPENDED) {
            providerOutboxService.enqueue(center, "SUSPEND", request.getReason());
        } else if (from == CenterStatus.SUSPENDED && to == CenterStatus.ACTIVE) {
            providerOutboxService.enqueue(center, "RESUME", request.getReason());
        } else if (from == CenterStatus.PENDING && to == CenterStatus.ACTIVE) {
            providerOutboxService.enqueue(center, "ACTIVATE", request.getReason());
        }
        return careCenterMapper.toResponse(center);
    }

    private void checkNoActiveBookings(UUID id) {
        for (String state : List.of("PENDING", "AWAITING_DEPOSIT", "CONFIRMED", "IN_PROGRESS")) {
            RemoteApiResponse<BookingCountResponse> response = bookingArchiveClient.list(id, state, 0, 1);
            if (response == null || response.getResult() == null || response.getResult().getTotalElements() == null) {
                throw new AppException(ErrorCode.BOOKING_SERVICE_UNAVAILABLE);
            }
            if (response.getResult().getTotalElements() > 0) {
                throw new AppException(ErrorCode.CENTER_HAS_ACTIVE_BOOKINGS);
            }
        }
    }

    // Chạy trong transactionTemplate của create — không gọi mạng ở đây.
    private CenterResponse createCenter(UUID actorId, CenterCreateRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        if (careCenterRepository.existsByEmailAndDeletedAtIsNull(normalizedEmail)) {
            throw new AppException(ErrorCode.CENTER_EMAIL_EXISTED);
        }
        if (request.getManagerUserId() != null
                && careCenterRepository.existsByManagerUserIdAndDeletedAtIsNull(request.getManagerUserId())) {
            throw new AppException(ErrorCode.CENTER_MANAGER_EXISTED);
        }

        CareCenter careCenter = careCenterMapper.toCareCenter(request);
        normalizeFields(careCenter, normalizedEmail);
        careCenter.setCenterStatus(CenterStatus.PENDING);
        careCenter.setCreatedBy(actorId);
        careCenter.setUpdatedBy(actorId);

        CareCenter savedCenter = careCenterRepository.save(careCenter);
        log.info("Admin {} created care center {}", actorId, savedCenter.getId());
        return careCenterMapper.toResponse(savedCenter);
    }

    // Chạy trong transactionTemplate của update — không gọi mạng ở đây.
    private CenterResponse updateCenter(UUID actorId, UUID id, CenterUpdateRequest request) {
        CareCenter careCenter = findActiveCenter(id);
        checkVersion(careCenter, request.getVersion());

        String normalizedEmail = normalizeEmail(request.getEmail());
        if (careCenterRepository.existsByEmailAndIdNotAndDeletedAtIsNull(normalizedEmail, id)) {
            throw new AppException(ErrorCode.CENTER_EMAIL_EXISTED);
        }
        if (request.getManagerUserId() != null
                && careCenterRepository.existsByManagerUserIdAndIdNotAndDeletedAtIsNull(request.getManagerUserId(), id)) {
            throw new AppException(ErrorCode.CENTER_MANAGER_EXISTED);
        }

        careCenterMapper.update(request, careCenter);
        normalizeFields(careCenter, normalizedEmail);
        careCenter.setUpdatedBy(actorId);

        CareCenter savedCenter = careCenterRepository.save(careCenter);
        log.info("Admin {} updated care center {}", actorId, id);
        return careCenterMapper.toResponse(savedCenter);
    }

    // Gọi mạng (C9.2, token SERVICE) — chỉ chạy ngoài transaction. Kết quả theo centerId.
    private Map<UUID, CenterPriceSummaryClientResponse> fetchPriceSummaries(UUID careServiceId, BigDecimal minPrice,
                                                                         BigDecimal maxPrice) {
        RemoteApiResponse<List<CenterPriceSummaryClientResponse>> response = careCatalogClient.getPriceSummary(
                careServiceId, minPrice, maxPrice);
        if (response == null || response.getResult() == null) {
            log.warn("CareCatalogService returned an empty price summary");
            throw new AppException(ErrorCode.CATALOG_UNAVAILABLE);
        }
        Map<UUID, CenterPriceSummaryClientResponse> priceSummaries = new HashMap<>();
        for (CenterPriceSummaryClientResponse priceSummary : response.getResult()) {
            if (priceSummary.getCenterId() != null) {
                priceSummaries.put(priceSummary.getCenterId(), priceSummary);
            }
        }
        return priceSummaries;
    }

    // Không lọc theo giá: giá chỉ để hiển thị nên Catalog lỗi không được làm hỏng danh sách (fallback có chủ đích).
    private Map<UUID, CenterPriceSummaryClientResponse> fetchPriceSummariesQuietly() {
        try {
            return fetchPriceSummaries(null, null, null);
        } catch (AppException exception) {
            log.warn("Price summary unavailable, listing centers without prices ({})", exception.getErrorCode());
            return Map.of();
        }
    }

    // managerUserId là tùy chọn; có thì phải là tài khoản CENTER_MANAGER đang ACTIVE, thuộc ĐÚNG trung tâm này ở
    // Identity (H-12: claim center_id của manager và care_centers.manager_user_id không được lệch nhau).
    private void checkManagerAccount(UUID managerUserId, UUID centerId) {
        if (managerUserId == null) {
            return;
        }
        IdentityUserResponse manager = identityAccountService.requireActiveRole(managerUserId, RoleCode.CENTER_MANAGER);
        if (centerId == null || !centerId.equals(manager.getCenterId())) {
            throw new AppException(ErrorCode.IDENTITY_CENTER_MISMATCH);
        }
    }

    private CareCenter findActiveCenter(UUID id) {
        return careCenterRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
    }

    private void checkVersion(CareCenter careCenter, Long requestedVersion) {
        if (requestedVersion == null) {
            throw new AppException(ErrorCode.CENTER_VERSION_REQUIRED);
        }
        if (!requestedVersion.equals(careCenter.getVersion())) {
            throw new AppException(ErrorCode.CENTER_VERSION_CONFLICT);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
    }

    private void validateStatusReason(CenterStatusUpdateRequest request) {
        boolean reasonRequired = request.getStatus() == CenterStatus.REJECTED
                || request.getStatus() == CenterStatus.SUSPENDED
                || request.getStatus() == CenterStatus.INACTIVE;
        if (reasonRequired && (request.getReason() == null || request.getReason().isBlank())) {
            throw new AppException(ErrorCode.CENTER_STATUS_REASON_REQUIRED);
        }
    }

    private void normalizeFields(CareCenter careCenter, String normalizedEmail) {
        careCenter.setCenterName(careCenter.getCenterName().trim());
        careCenter.setEmail(normalizedEmail);
        careCenter.setAddress(careCenter.getAddress().trim());
        careCenter.setPhoneNumber(normalizeNullable(careCenter.getPhoneNumber()));
        careCenter.setOperatingHours(normalizeNullable(careCenter.getOperatingHours()));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
