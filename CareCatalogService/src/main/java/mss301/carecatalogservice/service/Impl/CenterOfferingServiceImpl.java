package mss301.carecatalogservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carecatalogservice.dto.request.CenterOfferingRequest;
import mss301.carecatalogservice.dto.request.ServiceQuoteRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CenterOfferingResponse;
import mss301.carecatalogservice.dto.response.ServiceQuoteResponse;
import mss301.carecatalogservice.entity.CatalogItem;
import mss301.carecatalogservice.entity.CenterOffering;
import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import mss301.carecatalogservice.mapper.CenterOfferingMapper;
import mss301.carecatalogservice.repository.CatalogItemRepository;
import mss301.carecatalogservice.repository.CenterOfferingRepository;
import mss301.carecatalogservice.service.CatalogQuoteService;
import mss301.carecatalogservice.service.CenterOfferingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CenterOfferingServiceImpl implements CenterOfferingService {

    CenterOfferingRepository centerOfferingRepository;
    CatalogItemRepository catalogItemRepository;
    CenterOfferingMapper centerOfferingMapper;
    CatalogQuoteService catalogQuoteService;

    @Override
    @Transactional
    public APIResponse<CenterOfferingResponse> create(UUID centerId, CenterOfferingRequest request) {
        CatalogItem catalogItem = findActiveCatalogItem(request.getCatalogItemId());

        if (centerOfferingRepository.existsByCenterIdAndCatalogItemIdAndDeletedAtIsNull(centerId, catalogItem.getId())) {
            throw new AppException(ErrorCode.CENTER_SERVICE_ALREADY_EXISTS);
        }

        CenterOffering offering = centerOfferingMapper.toEntity(request);
        offering.setCatalogItem(catalogItem);
        offering.setCenterId(centerId);

        CenterOffering saved = centerOfferingRepository.save(offering);
        log.info("Created center offering {} for center {}", saved.getId(), centerId);
        return APIResponse.success("Đăng ký dịch vụ cho trung tâm thành công", centerOfferingMapper.toResponse(saved));
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

        centerOfferingMapper.updateEntityFromRequest(request, offering);
        CenterOffering saved = centerOfferingRepository.save(offering);
        log.info("Updated center offering {} for center {}", id, centerId);
        return APIResponse.success("Cập nhật dịch vụ trung tâm thành công", centerOfferingMapper.toResponse(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<CenterOfferingResponse>> listByCenter(UUID centerId) {
        List<CenterOfferingResponse> offerings = centerOfferingRepository.findByCenterIdAndDeletedAtIsNull(centerId)
                .stream()
                .map(centerOfferingMapper::toResponse)
                .toList();
        return APIResponse.success("Lấy danh sách dịch vụ của trung tâm thành công", offerings);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CenterOfferingResponse> getById(UUID id) {
        CenterOffering offering = findActiveOffering(id);
        return APIResponse.success("Lấy thông tin dịch vụ trung tâm thành công", centerOfferingMapper.toResponse(offering));
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID centerId, UUID id) {
        CenterOffering offering = findActiveOffering(id);
        checkCenterOwnership(offering, centerId);
        offering.setDeletedAt(Instant.now());
        centerOfferingRepository.save(offering);
        log.info("Soft deleted center offering {} for center {}", id, centerId);
        return APIResponse.success("Xóa dịch vụ trung tâm thành công");
    }

    private void checkCenterOwnership(CenterOffering offering, UUID centerId) {
        if (!offering.getCenterId().equals(centerId)) {
            throw new AppException(ErrorCode.CENTER_ACCESS_DENIED);
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

    @Override
    @Transactional(readOnly = true)
    public List<ServiceQuoteResponse> quote(ServiceQuoteRequest request) {
        return catalogQuoteService.quote(request);
    }
}
