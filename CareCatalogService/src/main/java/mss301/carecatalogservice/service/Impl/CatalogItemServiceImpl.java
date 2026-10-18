package mss301.carecatalogservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carecatalogservice.dto.request.CatalogItemRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CatalogItemResponse;
import mss301.carecatalogservice.entity.CatalogItem;
import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import mss301.carecatalogservice.mapper.CatalogItemMapper;
import mss301.carecatalogservice.repository.CatalogItemRepository;
import mss301.carecatalogservice.service.CatalogItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CatalogItemServiceImpl implements CatalogItemService {

    CatalogItemRepository catalogItemRepository;
    CatalogItemMapper catalogItemMapper;

    @Override
    @Transactional
    public APIResponse<CatalogItemResponse> create(CatalogItemRequest request) {
        CatalogItem item = catalogItemMapper.toEntity(request);
        CatalogItem saved = catalogItemRepository.save(item);
        log.info("Created catalog item: {}", saved.getId());
        return APIResponse.success("Tạo dịch vụ chuẩn thành công", catalogItemMapper.toResponse(saved));
    }

    @Override
    @Transactional
    public APIResponse<CatalogItemResponse> update(UUID id, CatalogItemRequest request) {
        CatalogItem item = findActiveCatalogItem(id);
        catalogItemMapper.updateEntityFromRequest(request, item);
        CatalogItem saved = catalogItemRepository.save(item);
        log.info("Updated catalog item: {}", id);
        return APIResponse.success("Cập nhật dịch vụ chuẩn thành công", catalogItemMapper.toResponse(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<CatalogItemResponse>> listAll() {
        List<CatalogItemResponse> items = catalogItemRepository.findAllByDeletedAtIsNull()
                .stream()
                .map(catalogItemMapper::toResponse)
                .toList();
        return APIResponse.success("Lấy danh sách dịch vụ chuẩn thành công", items);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CatalogItemResponse> getById(UUID id) {
        CatalogItem item = findActiveCatalogItem(id);
        return APIResponse.success("Lấy thông tin dịch vụ chuẩn thành công", catalogItemMapper.toResponse(item));
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID id) {
        CatalogItem item = findActiveCatalogItem(id);
        item.setDeletedAt(Instant.now());
        catalogItemRepository.save(item);
        log.info("Soft deleted catalog item: {}", id);
        return APIResponse.success("Xóa dịch vụ chuẩn thành công");
    }

    private CatalogItem findActiveCatalogItem(UUID id) {
        return catalogItemRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CARE_SERVICE_NOT_FOUND));
    }
}
