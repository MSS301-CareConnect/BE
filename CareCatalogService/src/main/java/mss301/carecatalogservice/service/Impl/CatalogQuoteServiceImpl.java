package mss301.carecatalogservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carecatalogservice.dto.request.ServiceQuoteRequest;
import mss301.carecatalogservice.dto.response.ServiceQuoteResponse;
import mss301.carecatalogservice.entity.CatalogItem;
import mss301.carecatalogservice.entity.CenterOffering;
import mss301.carecatalogservice.entity.PricePlan;
import mss301.carecatalogservice.enums.ServiceStatus;
import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import mss301.carecatalogservice.mapper.CenterOfferingMapper;
import mss301.carecatalogservice.repository.CatalogItemRepository;
import mss301.carecatalogservice.repository.CenterOfferingRepository;
import mss301.carecatalogservice.repository.PricePlanRepository;
import mss301.carecatalogservice.service.CatalogQuoteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Báo giá xác định (CAT-01, D12): mỗi dịch vụ phải chỉ rõ pricePlanId, Catalog không tự chọn gói. Gói giá phải active,
 * thuộc đúng dịch vụ trung tâm; dịch vụ trung tâm và danh mục chuẩn phải ACTIVE. Cùng input → cùng kết quả.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CatalogQuoteServiceImpl implements CatalogQuoteService {

    CatalogItemRepository catalogItemRepository;
    CenterOfferingRepository centerOfferingRepository;
    PricePlanRepository pricePlanRepository;
    CenterOfferingMapper centerOfferingMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ServiceQuoteResponse> quote(ServiceQuoteRequest request) {
        UUID centerId = request.getCenterId();
        List<ServiceQuoteResponse> quotes = new ArrayList<>();
        for (ServiceQuoteRequest.Item item : request.getItems()) {
            CatalogItem catalogItem = findActiveCatalogItem(item.getCareServiceId());
            CenterOffering centerOffering = findActiveOffering(centerId, item.getCenterServiceId(), catalogItem);
            PricePlan pricePlan = findActivePricePlan(item.getPricePlanId(), centerOffering);
            quotes.add(centerOfferingMapper.toQuoteResponse(catalogItem, centerOffering, pricePlan));
        }
        return quotes;
    }

    private CatalogItem findActiveCatalogItem(UUID careServiceId) {
        CatalogItem catalogItem = catalogItemRepository.findByIdAndDeletedAtIsNull(careServiceId)
                .orElseThrow(() -> new AppException(ErrorCode.CARE_SERVICE_NOT_FOUND));
        if (catalogItem.getStatus() != ServiceStatus.ACTIVE) {
            throw new AppException(ErrorCode.SERVICE_NOT_ACTIVE);
        }
        return catalogItem;
    }

    // centerServiceId tùy chọn: không gửi thì tìm theo (trung tâm, dịch vụ chuẩn) — unique nên vẫn xác định.
    private CenterOffering findActiveOffering(UUID centerId, UUID centerServiceId, CatalogItem catalogItem) {
        Optional<CenterOffering> found = centerServiceId == null
                ? centerOfferingRepository.findByCenterIdAndCatalogItemIdAndDeletedAtIsNull(centerId, catalogItem.getId())
                : centerOfferingRepository.findByIdAndCenterIdAndDeletedAtIsNull(centerServiceId, centerId);
        CenterOffering centerOffering = found.orElseThrow(() -> new AppException(ErrorCode.CENTER_SERVICE_NOT_FOUND));
        if (!centerOffering.getCatalogItem().getId().equals(catalogItem.getId())) {
            throw new AppException(ErrorCode.CENTER_SERVICE_NOT_FOUND);
        }
        if (centerOffering.getStatus() != ServiceStatus.ACTIVE) {
            throw new AppException(ErrorCode.SERVICE_NOT_ACTIVE);
        }
        return centerOffering;
    }

    private PricePlan findActivePricePlan(UUID pricePlanId, CenterOffering centerOffering) {
        // Request đã @NotNull; kiểm lại để lời gọi trong code (không qua @Valid) cũng không rơi về gói mặc định.
        if (pricePlanId == null) {
            throw new AppException(ErrorCode.PRICE_PLAN_ID_REQUIRED);
        }
        PricePlan pricePlan = pricePlanRepository.findByIdAndCenterOfferingIdAndDeletedAtIsNull(pricePlanId, centerOffering.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRICE_PLAN_NOT_FOUND));
        if (!Boolean.TRUE.equals(pricePlan.getIsActive())) {
            throw new AppException(ErrorCode.PRICE_PLAN_NOT_ACTIVE);
        }
        return pricePlan;
    }
}
