package mss301.carecatalogservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carecatalogservice.dto.request.CareServiceRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CareServiceResponse;
import mss301.carecatalogservice.entity.CareService;
import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import mss301.carecatalogservice.mapper.CareServiceMapper;
import mss301.carecatalogservice.repository.CareServiceRepository;
import mss301.carecatalogservice.service.CareServiceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CareServiceServiceImpl implements CareServiceService {

    CareServiceRepository careServiceRepository;
    CareServiceMapper careServiceMapper;

    @Override
    @Transactional
    public APIResponse<CareServiceResponse> createCareService(CareServiceRequest request) {
        CareService careService = careServiceMapper.toEntity(request);
        CareService savedCareService = careServiceRepository.save(careService);
        log.info("Created care service {}", savedCareService.getId());
        return APIResponse.success("Tạo dịch vụ chăm sóc thành công", careServiceMapper.toResponse(savedCareService));
    }

    @Override
    @Transactional
    public APIResponse<CareServiceResponse> updateCareService(UUID id, CareServiceRequest request) {
        CareService careService = findActiveCareService(id);
        careServiceMapper.updateEntityFromRequest(request, careService);
        CareService savedCareService = careServiceRepository.save(careService);
        log.info("Updated care service {}", id);
        return APIResponse.success("Cập nhật dịch vụ chăm sóc thành công", careServiceMapper.toResponse(savedCareService));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<CareServiceResponse>> getAllCareServices() {
        List<CareServiceResponse> careServices = careServiceRepository.findAllByDeletedAtIsNull().stream()
                .map(careServiceMapper::toResponse)
                .toList();
        return APIResponse.success("Lấy danh sách dịch vụ chăm sóc thành công", careServices);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CareServiceResponse> getCareServiceById(UUID id) {
        CareService careService = findActiveCareService(id);
        return APIResponse.success("Lấy dịch vụ chăm sóc thành công", careServiceMapper.toResponse(careService));
    }

    @Override
    @Transactional
    public APIResponse<Void> deleteCareService(UUID id) {
        CareService careService = findActiveCareService(id);
        careService.setDeletedAt(Instant.now());
        careServiceRepository.save(careService);
        log.info("Soft deleted care service {}", id);
        return APIResponse.success("Xóa dịch vụ chăm sóc thành công");
    }

    private CareService findActiveCareService(UUID id) {
        return careServiceRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CARE_SERVICE_NOT_FOUND));
    }
}
