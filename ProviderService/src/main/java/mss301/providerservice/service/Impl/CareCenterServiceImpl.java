package mss301.providerservice.service.Impl;

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
import mss301.providerservice.dto.response.APIResponse;
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
import mss301.providerservice.repository.CareCenterSpecifications;
import mss301.providerservice.service.CareCenterService;
import mss301.providerservice.service.IdentityAccountService;
import mss301.providerservice.service.ProviderMediaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CareCenterServiceImpl implements CareCenterService {

    static int MAX_PAGE_SIZE = 100;
    static int CARD_IMAGE_LIMIT = 3;
    static int DETAIL_IMAGE_LIMIT = 10;

    CareCenterRepository careCenterRepository;
    CareCenterMapper careCenterMapper;
    IdentityAccountService identityAccountService;
    TransactionTemplate transactionTemplate;
    CareCatalogClient careCatalogClient;
    ProviderMediaService providerMediaService;

    /**
     * Không đặt @Transactional: kiểm tài khoản manager ở IdentityService là lời gọi mạng nên chạy TRƯỚC, ngoài
     * transaction; phần ghi DB chạy trong transactionTemplate (rule #24).
     */
    @Override
    public APIResponse<CenterResponse> create(UUID actorId, CenterCreateRequest request) {
        checkManagerAccount(request.getManagerUserId());
        CenterResponse response = transactionTemplate.execute(status -> createCenter(actorId, request));
        return APIResponse.success("Tạo trung tâm chăm sóc thành công", response);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CenterResponse>> list(CenterStatus status, String keyword,
                                                           boolean includeDeleted, int page, int size) {
        validatePage(page, size);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CareCenter> centerPage = careCenterRepository.findAll(
                CareCenterSpecifications.filter(status, keyword, includeDeleted), pageRequest);
        List<CenterResponse> items = careCenterMapper.toResponseList(centerPage.getContent());
        PageResponse<CenterResponse> result = PageResponse.of(centerPage, items);
        return APIResponse.success("Lấy danh sách trung tâm chăm sóc thành công", result);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CenterResponse> get(UUID id) {
        CareCenter careCenter = careCenterRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        CenterResponse response = careCenterMapper.toResponse(careCenter);
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
        checkManagerAccount(request.getManagerUserId());
        CenterResponse response = transactionTemplate.execute(status -> updateCenter(actorId, id, request));
        return APIResponse.success("Cập nhật trung tâm chăm sóc thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<CenterResponse> updateStatus(UUID actorId, UUID id, CenterStatusUpdateRequest request) {
        CareCenter careCenter = findActiveCenter(id);
        checkVersion(careCenter, request.getVersion());
        validateStatusReason(request);

        careCenter.setCenterStatus(request.getStatus());
        careCenter.setStatusReason(normalizeNullable(request.getReason()));
        careCenter.setUpdatedBy(actorId);
        if (request.getStatus() == CenterStatus.ARCHIVED) {
            careCenter.setDeletedAt(Instant.now());
        }

        CareCenter savedCenter = careCenterRepository.save(careCenter);
        log.info("Admin {} changed care center {} status to {}", actorId, id, request.getStatus());

        CenterResponse response = careCenterMapper.toResponse(savedCenter);
        return APIResponse.success("Cập nhật trạng thái trung tâm thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID actorId, UUID id, Long version) {
        CareCenter careCenter = findActiveCenter(id);
        checkVersion(careCenter, version);
        careCenter.setCenterStatus(CenterStatus.ARCHIVED);
        careCenter.setStatusReason("Archived by administrator");
        careCenter.setUpdatedBy(actorId);
        careCenter.setDeletedAt(Instant.now());
        careCenterRepository.save(careCenter);
        log.info("Admin {} archived care center {}", actorId, id);
        return APIResponse.success("Lưu trữ trung tâm chăm sóc thành công");
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

    // managerUserId là tùy chọn; có thì phải là tài khoản CENTER_MANAGER đang ACTIVE.
    private void checkManagerAccount(UUID managerUserId) {
        if (managerUserId != null) {
            identityAccountService.requireActiveRole(managerUserId, RoleCode.CENTER_MANAGER);
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
                || request.getStatus() == CenterStatus.SUSPENDED;
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
