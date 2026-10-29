package mss301.carecatalogservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carecatalogservice.dto.request.PricePlanRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.PricePlanResponse;
import mss301.carecatalogservice.entity.CenterOffering;
import mss301.carecatalogservice.entity.PricePlan;
import mss301.carecatalogservice.enums.PricingUnit;
import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import mss301.carecatalogservice.mapper.PricePlanMapper;
import mss301.carecatalogservice.repository.CenterOfferingRepository;
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

    static int HOUR_DURATION_MINUTES = 60;

    PricePlanRepository pricePlanRepository;
    CenterOfferingRepository centerOfferingRepository;
    PricePlanMapper pricePlanMapper;

    @Override
    @Transactional
    public APIResponse<PricePlanResponse> create(UUID centerId, PricePlanRequest request) {
        checkDuration(request);
        CenterOffering centerOffering = findActiveCenterOffering(request.getCenterOfferingId());
        checkCenterOwnership(centerOffering, centerId);
        PricePlan pricePlan = pricePlanMapper.toEntity(request);
        pricePlan.setCenterOffering(centerOffering);
        pricePlan.setIsActive(request.getIsActive() == null ? Boolean.TRUE : request.getIsActive());
        PricePlan saved = pricePlanRepository.save(pricePlan);
        log.info("Created price plan {} for center offering {} (center {})", saved.getId(), centerOffering.getId(), centerId);
        PricePlanResponse pricePlanResponse = pricePlanMapper.toResponse(saved);
        return APIResponse.success("Tạo bảng giá thành công", pricePlanResponse);
    }

    @Override
    @Transactional
    public APIResponse<PricePlanResponse> update(UUID centerId, UUID id, PricePlanRequest request) {
        checkDuration(request);
        PricePlan pricePlan = findActivePricePlan(id);
        checkCenterOwnership(pricePlan.getCenterOffering(), centerId);
        checkVersion(request.getVersion(), pricePlan.getVersion());

        if (!pricePlan.getCenterOffering().getId().equals(request.getCenterOfferingId())) {
            CenterOffering newOffering = findActiveCenterOffering(request.getCenterOfferingId());
            checkCenterOwnership(newOffering, centerId);
            pricePlan.setCenterOffering(newOffering);
        }
        pricePlanMapper.updateEntityFromRequest(request, pricePlan);
        if (request.getIsActive() != null) {
            pricePlan.setIsActive(request.getIsActive());
        }
        PricePlan saved = pricePlanRepository.saveAndFlush(pricePlan);
        log.info("Updated price plan {} for center {}", id, centerId);
        PricePlanResponse pricePlanResponse = pricePlanMapper.toResponse(saved);
        return APIResponse.success("Cập nhật bảng giá thành công", pricePlanResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<PricePlanResponse>> listByCenterOffering(UUID centerOfferingId) {
        List<PricePlanResponse> pricePlans = pricePlanRepository.findByCenterOfferingIdAndDeletedAtIsNull(centerOfferingId)
                .stream()
                .map(pricePlanMapper::toResponse)
                .toList();
        return APIResponse.success("Lấy danh sách bảng giá thành công", pricePlans);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PricePlanResponse> get(UUID id) {
        PricePlan pricePlan = findActivePricePlan(id);
        return APIResponse.success("Lấy bảng giá thành công", pricePlanMapper.toResponse(pricePlan));
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID centerId, UUID id) {
        PricePlan pricePlan = findActivePricePlan(id);
        checkCenterOwnership(pricePlan.getCenterOffering(), centerId);
        pricePlan.setDeletedAt(Instant.now());
        pricePlanRepository.save(pricePlan);
        log.info("Soft deleted price plan {} for center {}", id, centerId);
        return APIResponse.success("Xóa bảng giá thành công");
    }

    // Đơn giá theo giờ luôn tính trên 60 phút (BK-01 nhân với số giờ của buổi); DAY / SHIFT là thời lượng chuẩn của đơn vị.
    private void checkDuration(PricePlanRequest request) {
        if (request.getPricingUnit() == PricingUnit.HOUR && request.getDurationMinutes() != HOUR_DURATION_MINUTES) {
            throw new AppException(ErrorCode.INVALID_DURATION);
        }
    }

    private void checkCenterOwnership(CenterOffering centerOffering, UUID centerId) {
        if (!centerOffering.getCenterId().equals(centerId)) {
            throw new AppException(ErrorCode.CENTER_ACCESS_DENIED);
        }
    }

    private PricePlan findActivePricePlan(UUID id) {
        return pricePlanRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRICE_PLAN_NOT_FOUND));
    }

    private CenterOffering findActiveCenterOffering(UUID id) {
        return centerOfferingRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_SERVICE_NOT_FOUND));
    }

    // M-04: form cũ (version khác bản đang lưu) không được ghi đè thay đổi của người khác.
    private void checkVersion(Long expected, Long current) {
        if (expected != null && !expected.equals(current)) {
            throw new AppException(ErrorCode.CONCURRENT_UPDATE);
        }
    }
}
