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
import mss301.carecatalogservice.repository.CatalogItemRepository;
import mss301.carecatalogservice.repository.CenterOfferingRepository;
import mss301.carecatalogservice.repository.PricePlanRepository;
import mss301.carecatalogservice.service.CatalogQuoteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CatalogQuoteServiceImpl implements CatalogQuoteService {

    CatalogItemRepository catalogItemRepository;
    CenterOfferingRepository centerOfferingRepository;
    PricePlanRepository pricePlanRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ServiceQuoteResponse> quote(ServiceQuoteRequest request) {
        UUID centerId = request.getCenterId();
        List<ServiceQuoteResponse> quotes = new ArrayList<>();

        for (ServiceQuoteRequest.Item item : request.getItems()) {
            CatalogItem catalogItem = catalogItemRepository.findByIdAndDeletedAtIsNull(item.getCareServiceId())
                    .orElseThrow(() -> new AppException(ErrorCode.CARE_SERVICE_NOT_FOUND));

            if (catalogItem.getStatus() != ServiceStatus.ACTIVE) {
                throw new AppException(ErrorCode.SERVICE_NOT_ACTIVE);
            }

            CenterOffering centerOffering;
            if (item.getCenterServiceId() != null) {
                centerOffering = centerOfferingRepository.findByIdAndCenterIdAndDeletedAtIsNull(item.getCenterServiceId(), centerId)
                        .orElseThrow(() -> new AppException(ErrorCode.CENTER_SERVICE_NOT_FOUND));
                if (!centerOffering.getCatalogItem().getId().equals(catalogItem.getId())) {
                    throw new AppException(ErrorCode.CENTER_SERVICE_NOT_FOUND);
                }
            } else {
                centerOffering = centerOfferingRepository.findByCenterIdAndCatalogItemIdAndDeletedAtIsNull(centerId, catalogItem.getId())
                        .orElseThrow(() -> new AppException(ErrorCode.CENTER_SERVICE_NOT_FOUND));
            }

            if (centerOffering.getStatus() != ServiceStatus.ACTIVE) {
                throw new AppException(ErrorCode.SERVICE_NOT_ACTIVE);
            }

            PricePlan pricePlan;
            if (item.getPricePlanId() != null) {
                pricePlan = pricePlanRepository.findByIdAndCenterOfferingIdAndDeletedAtIsNull(item.getPricePlanId(), centerOffering.getId())
                        .orElseThrow(() -> new AppException(ErrorCode.PRICE_PLAN_NOT_FOUND));
                if (!Boolean.TRUE.equals(pricePlan.getIsActive())) {
                    throw new AppException(ErrorCode.PRICE_PLAN_NOT_FOUND);
                }
            } else {
                List<PricePlan> activePlans = pricePlanRepository.findByCenterOfferingIdAndIsActiveTrueAndDeletedAtIsNull(centerOffering.getId());
                if (activePlans.isEmpty()) {
                    throw new AppException(ErrorCode.PRICE_PLAN_NOT_FOUND);
                }
                pricePlan = activePlans.getFirst();
            }

            quotes.add(ServiceQuoteResponse.builder()
                    .careServiceId(catalogItem.getId())
                    .centerServiceId(centerOffering.getId())
                    .pricePlanId(pricePlan.getId())
                    .serviceName(catalogItem.getName())
                    .pricingUnit(pricePlan.getPricingUnit())
                    .durationMinutes(pricePlan.getDurationMinutes())
                    .basePrice(pricePlan.getBasePrice())
                    .active(true)
                    .build());
        }

        return quotes;
    }
}
