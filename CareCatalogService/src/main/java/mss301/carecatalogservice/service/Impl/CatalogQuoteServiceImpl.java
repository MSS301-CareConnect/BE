package mss301.carecatalogservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carecatalogservice.dto.request.InternalServiceQuoteRequest;
import mss301.carecatalogservice.dto.response.InternalServiceQuoteResponse;
import mss301.carecatalogservice.entity.CareService;
import mss301.carecatalogservice.entity.CenterService;
import mss301.carecatalogservice.entity.PricePlan;
import mss301.carecatalogservice.enums.ServiceStatus;
import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import mss301.carecatalogservice.repository.CareServiceRepository;
import mss301.carecatalogservice.repository.CenterServiceRepository;
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

    CareServiceRepository careServiceRepository;
    CenterServiceRepository centerServiceRepository;
    PricePlanRepository pricePlanRepository;

    @Override
    @Transactional(readOnly = true)
    public List<InternalServiceQuoteResponse> quote(InternalServiceQuoteRequest request) {
        UUID centerId = request.getCenterId();
        List<InternalServiceQuoteResponse> quotes = new ArrayList<>();

        for (InternalServiceQuoteRequest.Item item : request.getItems()) {
            CareService careService = careServiceRepository.findByIdAndDeletedAtIsNull(item.getCareServiceId())
                    .orElseThrow(() -> new AppException(ErrorCode.CARE_SERVICE_NOT_FOUND));

            if (careService.getStatus() != ServiceStatus.ACTIVE) {
                throw new AppException(ErrorCode.SERVICE_NOT_ACTIVE);
            }

            CenterService centerService;
            if (item.getCenterServiceId() != null) {
                centerService = centerServiceRepository.findByIdAndCenterIdAndDeletedAtIsNull(item.getCenterServiceId(), centerId)
                        .orElseThrow(() -> new AppException(ErrorCode.CENTER_SERVICE_NOT_FOUND));
                if (!centerService.getCareService().getId().equals(careService.getId())) {
                    throw new AppException(ErrorCode.CENTER_SERVICE_NOT_FOUND);
                }
            } else {
                centerService = centerServiceRepository.findByCenterIdAndCareServiceIdAndDeletedAtIsNull(centerId, careService.getId())
                        .orElseThrow(() -> new AppException(ErrorCode.CENTER_SERVICE_NOT_FOUND));
            }

            if (centerService.getStatus() != ServiceStatus.ACTIVE) {
                throw new AppException(ErrorCode.SERVICE_NOT_ACTIVE);
            }

            PricePlan pricePlan;
            if (item.getPricePlanId() != null) {
                pricePlan = pricePlanRepository.findByIdAndCenterServiceIdAndDeletedAtIsNull(item.getPricePlanId(), centerService.getId())
                        .orElseThrow(() -> new AppException(ErrorCode.PRICE_PLAN_NOT_FOUND));
                if (!Boolean.TRUE.equals(pricePlan.getIsActive())) {
                    throw new AppException(ErrorCode.PRICE_PLAN_NOT_FOUND);
                }
            } else {
                List<PricePlan> activePlans = pricePlanRepository.findByCenterServiceIdAndIsActiveTrueAndDeletedAtIsNull(centerService.getId());
                if (activePlans.isEmpty()) {
                    throw new AppException(ErrorCode.PRICE_PLAN_NOT_FOUND);
                }
                pricePlan = activePlans.getFirst();
            }

            quotes.add(InternalServiceQuoteResponse.builder()
                    .careServiceId(careService.getId())
                    .centerServiceId(centerService.getId())
                    .pricePlanId(pricePlan.getId())
                    .serviceName(careService.getName())
                    .pricingUnit(pricePlan.getPricingUnit())
                    .durationMinutes(pricePlan.getDurationMinutes())
                    .basePrice(pricePlan.getBasePrice())
                    .active(true)
                    .build());
        }

        return quotes;
    }
}
