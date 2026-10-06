package mss301.carecatalogservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carecatalogservice.dto.request.CenterServiceRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CenterServiceResponse;
import mss301.carecatalogservice.entity.CareService;
import mss301.carecatalogservice.entity.CenterService;
import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import mss301.carecatalogservice.mapper.CenterServiceMapper;
import mss301.carecatalogservice.repository.CareServiceRepository;
import mss301.carecatalogservice.repository.CenterServiceRepository;
import mss301.carecatalogservice.service.CenterServiceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CenterServiceServiceImpl implements CenterServiceService {

    CenterServiceRepository centerServiceRepository;
    CareServiceRepository careServiceRepository;
    CenterServiceMapper centerServiceMapper;

    @Override
    @Transactional
    public APIResponse<CenterServiceResponse> createCenterService(UUID centerId, CenterServiceRequest request) {
        if (request.getCenterId() != null && !request.getCenterId().equals(centerId)) {
            throw new AppException(ErrorCode.CENTER_ACCESS_DENIED);
        }
        if (centerServiceRepository.existsByCenterIdAndCareServiceIdAndDeletedAtIsNull(centerId, request.getCareServiceId())) {
            throw new AppException(ErrorCode.CENTER_SERVICE_ALREADY_EXISTS);
        }
        CareService careService = findActiveCareService(request.getCareServiceId());
        CenterService centerService = centerServiceMapper.toEntity(request);
        centerService.setCenterId(centerId);
        centerService.setCareService(careService);
        CenterService savedCenterService = centerServiceRepository.save(centerService);
        log.info("Created center service {} for center {}", savedCenterService.getId(), centerId);
        return APIResponse.success("Tạo dịch vụ trung tâm thành công", centerServiceMapper.toResponse(savedCenterService));
    }

    @Override
    @Transactional
    public APIResponse<CenterServiceResponse> updateCenterService(UUID centerId, UUID id, CenterServiceRequest request) {
        CenterService centerService = findActiveCenterService(id);
        checkCenterOwnership(centerService, centerId);

        if (request.getCenterId() != null && !request.getCenterId().equals(centerId)) {
            throw new AppException(ErrorCode.CENTER_ACCESS_DENIED);
        }
        // Đổi dịch vụ chuẩn → phải kiểm dịch vụ mới còn tồn tại và không trùng lặp
        if (!centerService.getCareService().getId().equals(request.getCareServiceId())) {
            if (centerServiceRepository.existsByCenterIdAndCareServiceIdAndDeletedAtIsNull(centerId, request.getCareServiceId())) {
                throw new AppException(ErrorCode.CENTER_SERVICE_ALREADY_EXISTS);
            }
            centerService.setCareService(findActiveCareService(request.getCareServiceId()));
        }
        centerServiceMapper.updateEntityFromRequest(request, centerService);
        centerService.setCenterId(centerId);
        CenterService savedCenterService = centerServiceRepository.save(centerService);
        log.info("Updated center service {} for center {}", id, centerId);
        return APIResponse.success("Cập nhật dịch vụ trung tâm thành công", centerServiceMapper.toResponse(savedCenterService));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<CenterServiceResponse>> getCenterServicesByCenterId(UUID centerId) {
        List<CenterServiceResponse> centerServices = centerServiceRepository.findByCenterIdAndDeletedAtIsNull(centerId)
                .stream()
                .map(centerServiceMapper::toResponse)
                .toList();
        return APIResponse.success("Lấy danh sách dịch vụ trung tâm thành công", centerServices);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CenterServiceResponse> getCenterServiceById(UUID id) {
        CenterService centerService = findActiveCenterService(id);
        return APIResponse.success("Lấy dịch vụ trung tâm thành công", centerServiceMapper.toResponse(centerService));
    }

    @Override
    @Transactional
    public APIResponse<Void> deleteCenterService(UUID centerId, UUID id) {
        CenterService centerService = findActiveCenterService(id);
        checkCenterOwnership(centerService, centerId);
        centerService.setDeletedAt(Instant.now());
        centerServiceRepository.save(centerService);
        log.info("Soft deleted center service {} for center {}", id, centerId);
        return APIResponse.success("Xóa dịch vụ trung tâm thành công");
    }

    private void checkCenterOwnership(CenterService centerService, UUID centerId) {
        if (!centerService.getCenterId().equals(centerId)) {
            throw new AppException(ErrorCode.CENTER_ACCESS_DENIED);
        }
    }

    private CenterService findActiveCenterService(UUID id) {
        return centerServiceRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_SERVICE_NOT_FOUND));
    }

    private CareService findActiveCareService(UUID id) {
        return careServiceRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CARE_SERVICE_NOT_FOUND));
    }
}
