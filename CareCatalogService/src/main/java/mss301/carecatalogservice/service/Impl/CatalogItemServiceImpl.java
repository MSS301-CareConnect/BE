package mss301.carecatalogservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carecatalogservice.client.MediaClient;
import mss301.carecatalogservice.client.dto.DownloadUrlClientResponse;
import mss301.carecatalogservice.client.dto.MediaClientResponse;
import mss301.carecatalogservice.client.dto.RemoteApiResponse;
import mss301.carecatalogservice.dto.request.CatalogItemRequest;
import mss301.carecatalogservice.dto.request.CatalogItemSearchRequest;
import mss301.carecatalogservice.dto.request.ServiceStatusRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CatalogImageResponse;
import mss301.carecatalogservice.dto.response.CatalogItemResponse;
import mss301.carecatalogservice.dto.response.PageResponse;
import mss301.carecatalogservice.entity.CatalogItem;
import mss301.carecatalogservice.enums.RoleCode;
import mss301.carecatalogservice.enums.ServiceStatus;
import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import mss301.carecatalogservice.mapper.CatalogItemMapper;
import mss301.carecatalogservice.repository.CatalogItemRepository;
import mss301.carecatalogservice.repository.CenterOfferingRepository;
import mss301.carecatalogservice.repository.PricePlanRepository;
import mss301.carecatalogservice.service.CatalogItemService;
import mss301.carecatalogservice.util.CatalogItemSpecifications;
import mss301.carecatalogservice.util.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CatalogItemServiceImpl implements CatalogItemService {

    static Sort NAME_ASC = Sort.by(Sort.Direction.ASC, "name");
    static String IMAGE_PURPOSE = "CATALOG_ITEM_IMAGE";
    static String IMAGE_REFERENCE_TYPE = "CATALOG_ITEM";
    static String IMAGE_PATH = "/api/v1/catalog-items/images/";
    // Khớp giới hạn CATALOG_ITEM_IMAGE của MediaService; kiểm trước để báo lỗi rõ mà không tốn một lượt tải lên.
    static long IMAGE_MAX_BYTES = 5L * 1024L * 1024L;
    static Set<String> IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    // Trình duyệt giữ redirect ngắn hơn hạn của URL tải để không bao giờ dùng lại một URL đã hết hạn.
    static Duration IMAGE_REDIRECT_SAFETY_MARGIN = Duration.ofSeconds(60);
    static Duration IMAGE_REDIRECT_MAX_AGE = Duration.ofHours(1);

    CatalogItemRepository catalogItemRepository;
    CenterOfferingRepository centerOfferingRepository;
    PricePlanRepository pricePlanRepository;
    CatalogItemMapper catalogItemMapper;
    MediaClient mediaClient;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CatalogItemResponse>> list(Set<RoleCode> roles, CatalogItemSearchRequest request) {
        // Khách / caregiver không thấy dịch vụ đã vô hiệu hóa, bất kể status gửi lên.
        ServiceStatus status = canSeeAllStatuses(roles) ? request.getStatus() : ServiceStatus.ACTIVE;
        Page<CatalogItem> items = catalogItemRepository.findAll(
                CatalogItemSpecifications.filter(status, request.getCategory(), request.getKeyword()),
                PageRequests.of(request.getPage(), request.getSize(), NAME_ASC));
        List<CatalogItemResponse> catalogItemResponses = catalogItemMapper.toResponseList(items.getContent());
        PageResponse<CatalogItemResponse> pageResponse = PageResponse.of(items, catalogItemResponses);
        return APIResponse.success("Lấy danh sách dịch vụ chuẩn thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CatalogItemResponse> get(Set<RoleCode> roles, UUID id) {
        CatalogItem item = findActiveCatalogItem(id);
        if (!canSeeAllStatuses(roles) && item.getStatus() != ServiceStatus.ACTIVE) {
            throw new AppException(ErrorCode.CARE_SERVICE_NOT_FOUND);
        }
        CatalogItemResponse catalogItemResponse = catalogItemMapper.toResponse(item);
        return APIResponse.success("Lấy thông tin dịch vụ chuẩn thành công", catalogItemResponse);
    }

    @Override
    public ResponseEntity<Void> redirectToImage(UUID mediaFileId) {
        RemoteApiResponse<DownloadUrlClientResponse> response = mediaClient.createDownloadUrl(mediaFileId);
        DownloadUrlClientResponse download = response == null ? null : response.getResult();
        if (download == null || download.getDownloadUrl() == null) {
            throw new AppException(ErrorCode.CATALOG_IMAGE_NOT_FOUND);
        }
        CacheControl cacheControl = CacheControl.maxAge(findRedirectMaxAge(download.getExpiresAt())).cachePublic();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(download.getDownloadUrl()))
                .cacheControl(cacheControl)
                .build();
    }

    @Override
    @Transactional
    public APIResponse<CatalogItemResponse> create(CatalogItemRequest request) {
        CatalogItem item = catalogItemMapper.toEntity(request);
        CatalogItem saved = catalogItemRepository.save(item);
        log.info("Created catalog item: {}", saved.getId());
        CatalogItemResponse catalogItemResponse = catalogItemMapper.toResponse(saved);
        return APIResponse.success("Tạo dịch vụ chuẩn thành công", catalogItemResponse);
    }

    @Override
    public APIResponse<CatalogImageResponse> uploadImage(UUID actorId, MultipartFile file) {
        checkImageFile(file);
        // Không mở transaction: chỉ gọi MediaService, Catalog không lưu gì cho tới khi dịch vụ được tạo / sửa.
        RemoteApiResponse<MediaClientResponse> response =
                mediaClient.upload(file, IMAGE_PURPOSE, actorId, IMAGE_REFERENCE_TYPE);
        MediaClientResponse media = response == null ? null : response.getResult();
        if (media == null || media.getMediaFileId() == null) {
            throw new AppException(ErrorCode.MEDIA_SERVICE_UNAVAILABLE);
        }
        String imageUrl = IMAGE_PATH + media.getMediaFileId();
        CatalogImageResponse catalogImageResponse = catalogItemMapper.toImageResponse(media, imageUrl);
        log.info("Catalog image uploaded: mediaFileId={}, actorId={}", media.getMediaFileId(), actorId);
        return APIResponse.success("Tải ảnh minh họa thành công", catalogImageResponse);
    }

    @Override
    @Transactional
    public APIResponse<CatalogItemResponse> update(UUID id, CatalogItemRequest request) {
        CatalogItem item = findActiveCatalogItem(id);
        checkVersion(request.getVersion(), item.getVersion());
        catalogItemMapper.updateEntityFromRequest(request, item);
        CatalogItem saved = catalogItemRepository.saveAndFlush(item);
        log.info("Updated catalog item: {}", id);
        CatalogItemResponse catalogItemResponse = catalogItemMapper.toResponse(saved);
        return APIResponse.success("Cập nhật dịch vụ chuẩn thành công", catalogItemResponse);
    }

    // Vô hiệu hóa an toàn: offering / gói giá giữ nguyên trong DB, nhưng duyệt của khách và báo giá loại ra khi đọc;
    // booking đã tạo không đổi vì giá đã snapshot trong booking_details.
    @Override
    @Transactional
    public APIResponse<CatalogItemResponse> updateStatus(UUID id, ServiceStatusRequest request) {
        CatalogItem item = findActiveCatalogItem(id);
        item.setStatus(request.getStatus());
        CatalogItem saved = catalogItemRepository.save(item);
        log.info("Catalog item {} status -> {}", id, request.getStatus());
        CatalogItemResponse catalogItemResponse = catalogItemMapper.toResponse(saved);
        return APIResponse.success("Cập nhật trạng thái dịch vụ chuẩn thành công", catalogItemResponse);
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID id) {
        CatalogItem item = findActiveCatalogItem(id);
        if (centerOfferingRepository.existsByCatalogItemIdAndStatusAndDeletedAtIsNull(id, ServiceStatus.ACTIVE)
                || pricePlanRepository.existsActiveByCatalogItem(id)) {
            throw new AppException(ErrorCode.CARE_SERVICE_IN_USE);
        }
        item.setDeletedAt(Instant.now());
        catalogItemRepository.save(item);
        log.info("Soft deleted catalog item: {}", id);
        return APIResponse.success("Xóa dịch vụ chuẩn thành công");
    }

    private void checkImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.CATALOG_IMAGE_REQUIRED);
        }
        if (file.getSize() > IMAGE_MAX_BYTES) {
            throw new AppException(ErrorCode.CATALOG_IMAGE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !IMAGE_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new AppException(ErrorCode.CATALOG_IMAGE_INVALID);
        }
    }

    private Duration findRedirectMaxAge(Instant expiresAt) {
        if (expiresAt == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(clock.instant(), expiresAt).minus(IMAGE_REDIRECT_SAFETY_MARGIN);
        if (remaining.isNegative()) {
            return Duration.ZERO;
        }
        if (remaining.compareTo(IMAGE_REDIRECT_MAX_AGE) > 0) {
            return IMAGE_REDIRECT_MAX_AGE;
        }
        return remaining;
    }

    private boolean canSeeAllStatuses(Set<RoleCode> roles) {
        return roles.contains(RoleCode.ADMIN) || roles.contains(RoleCode.CENTER_MANAGER);
    }

    private CatalogItem findActiveCatalogItem(UUID id) {
        return catalogItemRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CARE_SERVICE_NOT_FOUND));
    }

    // M-04: form cũ (version khác bản đang lưu) không được ghi đè thay đổi của người khác.
    private void checkVersion(Long expected, Long current) {
        if (expected != null && !expected.equals(current)) {
            throw new AppException(ErrorCode.CONCURRENT_UPDATE);
        }
    }
}
