package mss301.carecatalogservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carecatalogservice.dto.request.CenterOfferingRequest;
import mss301.carecatalogservice.dto.request.CenterOfferingSearchRequest;
import mss301.carecatalogservice.dto.request.PriceSummaryRequest;
import mss301.carecatalogservice.dto.request.ServiceQuoteRequest;
import mss301.carecatalogservice.dto.request.ServiceStatusRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CenterOfferingResponse;
import mss301.carecatalogservice.dto.response.PageResponse;
import mss301.carecatalogservice.dto.response.PriceSummaryResponse;
import mss301.carecatalogservice.dto.response.ServiceQuoteResponse;
import mss301.carecatalogservice.entity.CatalogItem;
import mss301.carecatalogservice.entity.CenterOffering;
import mss301.carecatalogservice.entity.PricePlan;
import mss301.carecatalogservice.enums.RoleCode;
import mss301.carecatalogservice.enums.ServiceStatus;
import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import mss301.carecatalogservice.mapper.CenterOfferingMapper;
import mss301.carecatalogservice.repository.CatalogItemRepository;
import mss301.carecatalogservice.repository.CenterOfferingRepository;
import mss301.carecatalogservice.repository.CenterPriceView;
import mss301.carecatalogservice.repository.PricePlanRepository;
import mss301.carecatalogservice.service.CatalogQuoteService;
import mss301.carecatalogservice.service.CenterOfferingService;
import mss301.carecatalogservice.util.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CenterOfferingServiceImpl implements CenterOfferingService {

    static Sort CREATED_ASC = Sort.by(Sort.Direction.ASC, "createdAt");

    CenterOfferingRepository centerOfferingRepository;
    CatalogItemRepository catalogItemRepository;
    PricePlanRepository pricePlanRepository;
    CenterOfferingMapper centerOfferingMapper;
    CatalogQuoteService catalogQuoteService;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CenterOfferingResponse>> listByCenter(Set<RoleCode> roles, UUID scopeCenterId,
                                                                          UUID centerId,
                                                                          CenterOfferingSearchRequest request) {
        boolean fullView = canManage(roles, scopeCenterId, centerId);
        Page<CenterOffering> offerings;
        if (!fullView) {
            offerings = centerOfferingRepository.findSellableByCenter(centerId, ServiceStatus.ACTIVE,
                    PageRequests.of(request.getPage(), request.getSize(), CREATED_ASC));
        } else if (request.getStatus() == null) {
            offerings = centerOfferingRepository.findByCenterIdAndDeletedAtIsNull(centerId,
                    PageRequests.of(request.getPage(), request.getSize(), CREATED_ASC));
        } else {
            offerings = centerOfferingRepository.findByCenterIdAndStatusAndDeletedAtIsNull(centerId, request.getStatus(),
                    PageRequests.of(request.getPage(), request.getSize(), CREATED_ASC));
        }
        Map<UUID, List<PricePlan>> plansByOffering = findPlansByOffering(offerings.getContent(), fullView);
        List<CenterOfferingResponse> centerOfferingResponses = new ArrayList<>();
        for (CenterOffering offering : offerings.getContent()) {
            centerOfferingResponses.add(centerOfferingMapper.toResponse(offering,
                    plansByOffering.getOrDefault(offering.getId(), List.of())));
        }
        PageResponse<CenterOfferingResponse> pageResponse = PageResponse.of(offerings, centerOfferingResponses);
        return APIResponse.success("Lấy danh sách dịch vụ của trung tâm thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CenterOfferingResponse> get(Set<RoleCode> roles, UUID scopeCenterId, UUID id) {
        CenterOffering offering = findActiveOffering(id);
        boolean fullView = canManage(roles, scopeCenterId, offering.getCenterId());
        List<PricePlan> pricePlans = findPlansByOffering(List.of(offering), fullView)
                .getOrDefault(offering.getId(), List.of());
        // Người không quản lý trung tâm chỉ thấy dịch vụ đang bán được — dịch vụ đã tắt coi như không tồn tại.
        if (!fullView && !isSellable(offering, pricePlans)) {
            throw new AppException(ErrorCode.CENTER_SERVICE_NOT_FOUND);
        }
        CenterOfferingResponse centerOfferingResponse = centerOfferingMapper.toResponse(offering, pricePlans);
        return APIResponse.success("Lấy thông tin dịch vụ trung tâm thành công", centerOfferingResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<PriceSummaryResponse>> listPriceSummaries(PriceSummaryRequest request) {
        if (request.getMinPrice() != null && request.getMaxPrice() != null
                && request.getMinPrice().compareTo(request.getMaxPrice()) > 0) {
            throw new AppException(ErrorCode.PRICE_RANGE_INVALID);
        }
        List<CenterPriceView> prices = request.getCareServiceId() == null
                ? pricePlanRepository.findSellablePrices(ServiceStatus.ACTIVE)
                : pricePlanRepository.findSellablePricesByCatalogItem(request.getCareServiceId(), ServiceStatus.ACTIVE);
        // Gom theo trung tâm, chỉ tính gói nằm trong khoảng giá; TreeMap để thứ tự kết quả ổn định.
        Map<UUID, BigDecimal> minPriceByCenter = prices.stream()
                .filter(price -> isInRange(price.getBasePrice(), request.getMinPrice(), request.getMaxPrice()))
                .collect(Collectors.toMap(CenterPriceView::getCenterId, CenterPriceView::getBasePrice,
                        BigDecimal::min, TreeMap::new));
        List<PriceSummaryResponse> priceSummaryResponses = new ArrayList<>();
        minPriceByCenter.forEach((centerId, minPrice) ->
                priceSummaryResponses.add(centerOfferingMapper.toPriceSummaryResponse(centerId, minPrice)));
        return APIResponse.success("Lấy giá thấp nhất theo trung tâm thành công", priceSummaryResponses);
    }

    @Override
    @Transactional
    public APIResponse<CenterOfferingResponse> create(UUID centerId, CenterOfferingRequest request) {
        CatalogItem catalogItem = findActiveCatalogItem(request.getCatalogItemId());
        checkCatalogItemActive(catalogItem);
        if (centerOfferingRepository.existsByCenterIdAndCatalogItemIdAndDeletedAtIsNull(centerId, catalogItem.getId())) {
            throw new AppException(ErrorCode.CENTER_SERVICE_ALREADY_EXISTS);
        }
        CenterOffering offering = centerOfferingMapper.toEntity(request);
        offering.setCatalogItem(catalogItem);
        offering.setCenterId(centerId);
        CenterOffering saved = centerOfferingRepository.save(offering);
        log.info("Created center offering {} for center {}", saved.getId(), centerId);
        CenterOfferingResponse centerOfferingResponse = centerOfferingMapper.toResponse(saved);
        return APIResponse.success("Đăng ký dịch vụ cho trung tâm thành công", centerOfferingResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<ServiceQuoteResponse>> quote(ServiceQuoteRequest request) {
        List<ServiceQuoteResponse> serviceQuoteResponses = catalogQuoteService.quote(request);
        return APIResponse.success("Báo giá dịch vụ thành công", serviceQuoteResponses);
    }

    @Override
    @Transactional
    public APIResponse<CenterOfferingResponse> update(UUID centerId, UUID id, CenterOfferingRequest request) {
        CenterOffering offering = findActiveOffering(id);
        checkCenterOwnership(offering, centerId);
        if (!offering.getCatalogItem().getId().equals(request.getCatalogItemId())) {
            CatalogItem newCatalogItem = findActiveCatalogItem(request.getCatalogItemId());
            if (centerOfferingRepository.existsByCenterIdAndCatalogItemIdAndDeletedAtIsNull(centerId, newCatalogItem.getId())) {
                throw new AppException(ErrorCode.CENTER_SERVICE_ALREADY_EXISTS);
            }
            offering.setCatalogItem(newCatalogItem);
        }
        // Chỉ chặn khi offering sẽ ACTIVE: manager vẫn tắt được dịch vụ có danh mục chuẩn đã bị vô hiệu hóa.
        if (request.getStatus() == ServiceStatus.ACTIVE) {
            checkCatalogItemActive(offering.getCatalogItem());
        }
        centerOfferingMapper.updateEntityFromRequest(request, offering);
        CenterOffering saved = centerOfferingRepository.save(offering);
        log.info("Updated center offering {} for center {}", id, centerId);
        CenterOfferingResponse centerOfferingResponse = centerOfferingMapper.toResponse(saved);
        return APIResponse.success("Cập nhật dịch vụ trung tâm thành công", centerOfferingResponse);
    }

    @Override
    @Transactional
    public APIResponse<CenterOfferingResponse> updateStatus(UUID centerId, UUID id, ServiceStatusRequest request) {
        CenterOffering offering = findActiveOffering(id);
        checkCenterOwnership(offering, centerId);
        if (request.getStatus() == ServiceStatus.ACTIVE) {
            checkCatalogItemActive(offering.getCatalogItem());
        }
        offering.setStatus(request.getStatus());
        CenterOffering saved = centerOfferingRepository.save(offering);
        log.info("Center offering {} status -> {} (center {})", id, request.getStatus(), centerId);
        CenterOfferingResponse centerOfferingResponse = centerOfferingMapper.toResponse(saved);
        return APIResponse.success("Cập nhật trạng thái dịch vụ trung tâm thành công", centerOfferingResponse);
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID centerId, UUID id) {
        CenterOffering offering = findActiveOffering(id);
        checkCenterOwnership(offering, centerId);
        if (pricePlanRepository.existsByCenterOfferingIdAndIsActiveTrueAndDeletedAtIsNull(id)) {
            throw new AppException(ErrorCode.CENTER_SERVICE_IN_USE);
        }
        Instant now = Instant.now();
        for (PricePlan pricePlan : pricePlanRepository.findByCenterOfferingIdAndDeletedAtIsNull(id)) {
            pricePlan.setDeletedAt(now);
        }
        offering.setDeletedAt(now);
        centerOfferingRepository.save(offering);
        log.info("Soft deleted center offering {} (and its price plans) for center {}", id, centerId);
        return APIResponse.success("Xóa dịch vụ trung tâm thành công");
    }

    // ADMIN và manager của chính trung tâm đó thấy mọi trạng thái + mọi gói giá.
    private boolean canManage(Set<RoleCode> roles, UUID scopeCenterId, UUID centerId) {
        return roles.contains(RoleCode.ADMIN)
                || (roles.contains(RoleCode.CENTER_MANAGER) && centerId.equals(scopeCenterId));
    }

    // Một truy vấn cho cả trang (không N+1). Người xem thường chỉ nhận gói active.
    private Map<UUID, List<PricePlan>> findPlansByOffering(List<CenterOffering> offerings, boolean includeInactive) {
        if (offerings.isEmpty()) {
            return Map.of();
        }
        List<UUID> offeringIds = offerings.stream()
                .map(CenterOffering::getId)
                .toList();
        List<PricePlan> pricePlans = includeInactive
                ? pricePlanRepository.findByCenterOfferingIdInAndDeletedAtIsNull(offeringIds)
                : pricePlanRepository.findByCenterOfferingIdInAndIsActiveTrueAndDeletedAtIsNull(offeringIds);
        return pricePlans.stream()
                .collect(Collectors.groupingBy(pricePlan -> pricePlan.getCenterOffering().getId()));
    }

    private boolean isSellable(CenterOffering offering, List<PricePlan> activePricePlans) {
        return offering.getStatus() == ServiceStatus.ACTIVE
                && offering.getCatalogItem().getStatus() == ServiceStatus.ACTIVE
                && !activePricePlans.isEmpty();
    }

    private boolean isInRange(BigDecimal price, BigDecimal minPrice, BigDecimal maxPrice) {
        return (minPrice == null || price.compareTo(minPrice) >= 0)
                && (maxPrice == null || price.compareTo(maxPrice) <= 0);
    }

    private void checkCenterOwnership(CenterOffering offering, UUID centerId) {
        if (!offering.getCenterId().equals(centerId)) {
            throw new AppException(ErrorCode.CENTER_ACCESS_DENIED);
        }
    }

    private void checkCatalogItemActive(CatalogItem catalogItem) {
        if (catalogItem.getStatus() != ServiceStatus.ACTIVE) {
            throw new AppException(ErrorCode.SERVICE_NOT_ACTIVE);
        }
    }

    private CenterOffering findActiveOffering(UUID id) {
        return centerOfferingRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_SERVICE_NOT_FOUND));
    }

    private CatalogItem findActiveCatalogItem(UUID id) {
        return catalogItemRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CARE_SERVICE_NOT_FOUND));
    }
}
