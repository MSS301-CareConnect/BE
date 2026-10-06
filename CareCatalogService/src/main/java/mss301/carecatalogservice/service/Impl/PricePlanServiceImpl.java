package mss301.carecatalogservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carecatalogservice.dto.request.PricePlanRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.PricePlanResponse;
import mss301.carecatalogservice.entity.CenterService;
import mss301.carecatalogservice.entity.PricePlan;
import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import mss301.carecatalogservice.mapper.PricePlanMapper;
import mss301.carecatalogservice.repository.CenterServiceRepository;
import mss301.carecatalogservice.repository.PricePlanRepository;
import mss301.carecatalogservice.service.PricePlanService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PricePlanServiceImpl implements PricePlanService {

    PricePlanRepository pricePlanRepository;
    CenterServiceRepository centerServiceRepository;
    PricePlanMapper pricePlanMapper;

    @Override
    @Transactional
    public APIResponse<PricePlanResponse> createPricePlan(UUID centerId, PricePlanRequest request) {
        CenterService centerService = findActiveCenterService(request.getCenterServiceId());
        checkCenterOwnership(centerService, centerId);
        PricePlan pricePlan = pricePlanMapper.toEntity(request);
        pricePlan.setCenterService(centerService);
        // isActive không gửi → giữ mặc định true của entity.
        if (request.getIsActive() != null) {
            pricePlan.setIsActive(request.getIsActive());
        }
        PricePlan savedPricePlan = pricePlanRepository.save(pricePlan);
        log.info("Created price plan {} for center service {} (center {})", savedPricePlan.getId(), centerService.getId(), centerId);
        return APIResponse.success("Tạo bảng giá thành công", pricePlanMapper.toResponse(savedPricePlan));
    }

    @Override
    @Transactional
    public APIResponse<PricePlanResponse> updatePricePlan(UUID centerId, UUID id, PricePlanRequest request) {
        PricePlan pricePlan = findActivePricePlan(id);
        checkCenterOwnership(pricePlan.getCenterService(), centerId);

        if (!pricePlan.getCenterService().getId().equals(request.getCenterServiceId())) {
            CenterService newCenterService = findActiveCenterService(request.getCenterServiceId());
            checkCenterOwnership(newCenterService, centerId);
            pricePlan.setCenterService(newCenterService);
        }
        pricePlanMapper.updateEntityFromRequest(request, pricePlan);
        if (request.getIsActive() != null) {
            pricePlan.setIsActive(request.getIsActive());
        }
        PricePlan savedPricePlan = pricePlanRepository.save(pricePlan);
        log.info("Updated price plan {} for center {}", id, centerId);
        return APIResponse.success("Cập nhật bảng giá thành công", pricePlanMapper.toResponse(savedPricePlan));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<PricePlanResponse>> getPricePlansByCenterServiceId(UUID centerServiceId) {
        List<PricePlanResponse> pricePlans = pricePlanRepository.findByCenterServiceIdAndDeletedAtIsNull(centerServiceId)
                .stream()
                .map(pricePlanMapper::toResponse)
                .toList();
        return APIResponse.success("Lấy danh sách bảng giá thành công", pricePlans);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PricePlanResponse> getPricePlanById(UUID id) {
        PricePlan pricePlan = findActivePricePlan(id);
        return APIResponse.success("Lấy bảng giá thành công", pricePlanMapper.toResponse(pricePlan));
    }

    @Override
    @Transactional
    public APIResponse<Void> deletePricePlan(UUID centerId, UUID id) {
        PricePlan pricePlan = findActivePricePlan(id);
        checkCenterOwnership(pricePlan.getCenterService(), centerId);
        pricePlan.setDeletedAt(Instant.now());
        pricePlanRepository.save(pricePlan);
        log.info("Soft deleted price plan {} for center {}", id, centerId);
        return APIResponse.success("Xóa bảng giá thành công");
    }

    private void checkCenterOwnership(CenterService centerService, UUID centerId) {
        if (!centerService.getCenterId().equals(centerId)) {
            throw new AppException(ErrorCode.CENTER_ACCESS_DENIED);
        }
    }

    private PricePlan findActivePricePlan(UUID id) {
        return pricePlanRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRICE_PLAN_NOT_FOUND));
    }

    private CenterService findActiveCenterService(UUID id) {
        return centerServiceRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_SERVICE_NOT_FOUND));
    }
}
