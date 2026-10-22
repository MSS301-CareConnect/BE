package mss301.carecatalogservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carecatalogservice.dto.request.CatalogItemRequest;
import mss301.carecatalogservice.dto.request.CatalogItemSearchRequest;
import mss301.carecatalogservice.dto.request.ServiceStatusRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CatalogItemServiceImpl implements CatalogItemService {

    static Sort NAME_ASC = Sort.by(Sort.Direction.ASC, "name");

    CatalogItemRepository catalogItemRepository;
    CenterOfferingRepository centerOfferingRepository;
    PricePlanRepository pricePlanRepository;
    CatalogItemMapper catalogItemMapper;

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
    @Transactional
    public APIResponse<CatalogItemResponse> create(CatalogItemRequest request) {
        CatalogItem item = catalogItemMapper.toEntity(request);
        CatalogItem saved = catalogItemRepository.save(item);
        log.info("Created catalog item: {}", saved.getId());
        CatalogItemResponse catalogItemResponse = catalogItemMapper.toResponse(saved);
        return APIResponse.success("Tạo dịch vụ chuẩn thành công", catalogItemResponse);
    }

    @Override
    @Transactional
    public APIResponse<CatalogItemResponse> update(UUID id, CatalogItemRequest request) {
        CatalogItem item = findActiveCatalogItem(id);
        catalogItemMapper.updateEntityFromRequest(request, item);
        CatalogItem saved = catalogItemRepository.save(item);
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

    private boolean canSeeAllStatuses(Set<RoleCode> roles) {
        return roles.contains(RoleCode.ADMIN) || roles.contains(RoleCode.CENTER_MANAGER);
    }

    private CatalogItem findActiveCatalogItem(UUID id) {
        return catalogItemRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CARE_SERVICE_NOT_FOUND));
    }
}
